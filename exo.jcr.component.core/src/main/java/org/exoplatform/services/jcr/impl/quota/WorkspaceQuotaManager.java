/*
 * Copyright (C) 2012 eXo Platform SAS.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.exoplatform.services.jcr.impl.quota;

import org.exoplatform.commons.utils.PrivilegedFileHelper;
import org.exoplatform.commons.utils.PrivilegedSystemHelper;
import org.exoplatform.commons.utils.SecurityHelper;
import org.exoplatform.management.annotations.Managed;
import org.exoplatform.management.annotations.ManagedDescription;
import org.exoplatform.management.annotations.ManagedName;
import org.exoplatform.management.jmx.annotations.NameTemplate;
import org.exoplatform.management.jmx.annotations.Property;
import org.exoplatform.services.jcr.config.RepositoryEntry;
import org.exoplatform.services.jcr.config.WorkspaceEntry;
import org.exoplatform.services.jcr.core.WorkspaceContainerFacade;
import org.exoplatform.services.jcr.dataflow.ItemDataConsumer;
import org.exoplatform.services.jcr.dataflow.ItemDataTraversingVisitor;
import org.exoplatform.services.jcr.dataflow.ItemState;
import org.exoplatform.services.jcr.dataflow.ItemStateChangesLog;
import org.exoplatform.services.jcr.dataflow.persistent.MandatoryItemsPersistenceListener;
import org.exoplatform.services.jcr.datamodel.ItemType;
import org.exoplatform.services.jcr.datamodel.NodeData;
import org.exoplatform.services.jcr.datamodel.PropertyData;
import org.exoplatform.services.jcr.datamodel.QPathEntry;
import org.exoplatform.services.jcr.impl.Constants;
import org.exoplatform.services.jcr.impl.backup.BackupException;
import org.exoplatform.services.jcr.impl.backup.Backupable;
import org.exoplatform.services.jcr.impl.backup.DataRestore;
import org.exoplatform.services.jcr.impl.backup.ResumeException;
import org.exoplatform.services.jcr.impl.backup.SuspendException;
import org.exoplatform.services.jcr.impl.backup.Suspendable;
import org.exoplatform.services.jcr.impl.backup.rdbms.DBBackup;
import org.exoplatform.services.jcr.impl.backup.rdbms.DataRestoreContext;
import org.exoplatform.services.jcr.impl.core.JCRPath;
import org.exoplatform.services.jcr.impl.core.LocationFactory;
import org.exoplatform.services.jcr.impl.core.RepositoryImpl;
import org.exoplatform.services.jcr.impl.core.query.SearchManager;
import org.exoplatform.services.jcr.impl.dataflow.persistent.WorkspacePersistentDataManager;
import org.exoplatform.services.jcr.impl.dataflow.serialization.ZipObjectReader;
import org.exoplatform.services.jcr.impl.dataflow.serialization.ZipObjectWriter;
import org.exoplatform.services.jcr.impl.util.io.DirectoryHelper;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.jboss.cache.util.concurrent.ConcurrentHashSet;
import org.picocontainer.Startable;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.jcr.RepositoryException;

/**
 * @author <a href="abazko@exoplatform.com">Anatoliy Bazko</a>
 * @version $Id: WorkspaceQuotaManager.java 34360 2009-07-22 23:58:59Z tolusha $
 */
@Managed
@NameTemplate(@Property(key = "service", value = "WorkspaceQuotaManager"))
public class WorkspaceQuotaManager implements Startable, Backupable, Suspendable
{

   /**
    * Logger.
    */
   protected final Log LOG = ExoLogger.getLogger("exo.jcr.component.core.WorkspaceQuotaManagerr");

   /**
    * Workspace name.
    */
   protected final String wsName;

   /**
    * Repository name.
    */
   protected final String rName;

   /**
    * Unique name.
    */
   protected final String uniqueName;

   /**
    * Workspace container.
    */
   protected final WorkspaceContainerFacade wsContainer;

   /**
    * {@link WorkspacePersistentDataManager} instance.
    */
   protected final WorkspacePersistentDataManager dataManager;

   /**
    * {@link LocationFactory} instance.
    */
   protected final LocationFactory lFactory;

   /**
    * Quota manager of repository level.
    */
   protected final RepositoryQuotaManager repositoryQuotaManager;

   /**
    * Executor service.
    */
   protected ExecutorService executor;

   /**
    * {@link QuotaPersister}
    */
   protected final QuotaPersister quotaPersister;

   /**
    * Indicates if workspace data size exceeded quota limit.
    */
   protected AtomicBoolean alerted = new AtomicBoolean();

   /**
    * List of alerted nodes in workspace.
    */
   protected Set<String> alertedPaths = new ConcurrentHashSet<String>();

   /**
    * File name for backuped data.
    */
   protected static final String BACKUP_FILE_NAME = "quota";

   /**
    * {@link MandatoryItemsPersistenceListener} implementation.
    */
   protected final ValidateChangesListener validateChangesListener = new ValidateChangesListener();

   /**
    * {@link MandatoryItemsPersistenceListener} implementation.
    */
   protected final AccumulateChangesListener accumulateChangesListener = new AccumulateChangesListener();

   /**
    * Indicates if component suspended or not.
    */
   protected AtomicBoolean isSuspended = new AtomicBoolean();

   /**
    * Contains node paths for which {@link DefineAlertedNodeTask} currently is run. 
    * Does't allow execution several tasks over common path.
    */
   protected Set<String> runNodesTasks = new ConcurrentHashSet<String>();

   /**
    * WorkspaceQuotaManager constructor.
    */
   public WorkspaceQuotaManager(RepositoryImpl repository, RepositoryQuotaManager rQuotaManager,
      RepositoryEntry rEntry, WorkspaceEntry wsEntry, WorkspacePersistentDataManager dataManager)
   {
      this.rName = rEntry.getName();
      this.wsName = wsEntry.getName();
      this.uniqueName = "/" + rName + "/" + wsName;
      this.wsContainer = repository.getWorkspaceContainer(wsName);
      this.dataManager = dataManager;
      this.lFactory = repository.getLocationFactory();
      this.repositoryQuotaManager = rQuotaManager;
      this.quotaPersister = rQuotaManager.globalQuotaManager.quotaPersister;

      initExecutorService();

      defineAlertedState();
      defineAlertedPaths();

      dataManager.addItemPersistenceListener(accumulateChangesListener);
      dataManager.addItemPersistenceListener(validateChangesListener);
   }

   /**
    * @see QuotaManager#getNodeDataSize(String, String, String)
    */
   @Managed
   @ManagedDescription("Returns a node data size")
   public long getNodeDataSize(@ManagedDescription("The absolute path to node") @ManagedName("nodePath") String nodePath)
      throws QuotaManagerException
   {
      if (nodePath.equals(JCRPath.ROOT_PATH))
      {
         return quotaPersister.getWorkspaceDataSize(rName, wsName);
      }

      return quotaPersister.getNodeDataSize(rName, wsName, nodePath);
   }

   /**
    * @see QuotaManager#getNodeQuota(String, String, String)
    */
   @Managed
   @ManagedDescription("Returns a node quota limit")
   public long getNodeQuota(String nodePath) throws QuotaManagerException
   {
      return quotaPersister.getNodeQuotaByPathOrPattern(rName, wsName, nodePath);
   }

   /**
    * @see QuotaManager#setNodeQuota(String, String, String, long, boolean)
    */
   @Managed
   @ManagedDescription("Sets a node quota limit")
   public void setNodeQuota(String nodePath, long quotaLimit, boolean asyncUpdate) throws QuotaManagerException
   {
      quotaPersister.setNodeQuota(rName, wsName, nodePath, quotaLimit, asyncUpdate);

      try
      {
         quotaPersister.getNodeDataSize(rName, wsName, nodePath);
         defineAlertedPath(nodePath);
      }
      catch (UnknownQuotaDataSizeException e)
      {
         if (!runNodesTasks.contains(nodePath))
         {
            synchronized (runNodesTasks)
            {
               if (!runNodesTasks.contains(nodePath))
               {
                  Runnable task = new DefineAlertedNodeTask(this, nodePath, runNodesTasks);
                  executor.execute(task);
               }
            }
         }
      }
   }

   /**
    * @see QuotaManager#setGroupOfNodesQuota(String, String, String, long, boolean)
    */
   @Managed
   @ManagedDescription("Sets a quota limit for a bunch of nodes")
   public void setGroupOfNodesQuota(String patternPath, long quotaLimit, boolean asyncUpdate)
      throws QuotaManagerException
   {
      quotaPersister.setGroupOfNodeQuota(rName, wsName, patternPath, quotaLimit, asyncUpdate);
   }

   /**
    * @see QuotaManager#removeNodeQuota(String, String, String)
    */
   @Managed
   @ManagedDescription("Removes a quota limit for a node")
   public void removeNodeQuota(String nodePath) throws QuotaManagerException
   {
      quotaPersister.removeNodeQuota(rName, wsName, nodePath);
      defineAlertedPath(nodePath);
   }

   /**
    * @see QuotaManager#removeGroupOfNodesQuota(String, String, String)
    */
   @Managed
   @ManagedDescription("Removes a quota limit for a bunch of nodes")
   public void removeGroupOfNodesQuota(String patternPath) throws QuotaManagerException
   {
      Set<String> removedPaths = quotaPersister.removeGroupOfNodesQuota(rName, wsName, patternPath);
      alertedPaths.remove(removedPaths);
   }

   /**
    * @see QuotaManager#setWorkspaceQuota(String, String, long)
    */
   @ManagedDescription("Sets workspace quota limit")
   public void setWorkspaceQuota(long quotaLimit) throws QuotaManagerException
   {
      quotaPersister.setWorkspaceQuota(rName, wsName, quotaLimit);
      defineAlertedState();
   }

   /**
    * @see QuotaManager#removeWorkspaceQuota(String, String, long)
    */
   @ManagedDescription("Removes workspace quota limit")
   public void removeWorkspaceQuota() throws QuotaManagerException
   {
      quotaPersister.removeWorkspaceQuota(rName, wsName);
      defineAlertedState();
   }

   /**
    * @see QuotaManager#getWorkspaceQuota(String, String)
    */
   @Managed
   @ManagedDescription("Returns workspace quota limit")
   public long getWorkspaceQuota() throws QuotaManagerException
   {
      return quotaPersister.getWorkspaceQuota(rName, wsName);
   }

   /**
    * @see QuotaManager#getWorkspaceDataSize(String, String)
    */
   @Managed
   @ManagedDescription("Returns a size of the Workspace")
   public long getWorkspaceDataSize() throws QuotaManagerException
   {
      return quotaPersister.getWorkspaceDataSize(rName, wsName);
   }

   /**
    * @see QuotaManager#getWorkspaceIndexSize(String, String)
    */
   @Managed
   @ManagedDescription("Returns a size of the index")
   public long getWorkspaceIndexSize() throws QuotaManagerException
   {
      try
      {
         return SecurityHelper.doPrivilegedExceptionAction(new PrivilegedExceptionAction<Long>()
         {
            public Long run() throws Exception
            {
               Method getIndexDirMethod = SearchManager.class.getDeclaredMethod("getIndexDirectory");
               getIndexDirMethod.setAccessible(true);

               // get all instances, since for system workspace we have 2 SearchManager
               List<SearchManager> searchers = wsContainer.getComponentInstancesOfType(SearchManager.class);

               long size = 0;
               for (SearchManager searchManager : searchers)
               {
                  File indexDir = (File)getIndexDirMethod.invoke(searchManager);
                  size += DirectoryHelper.getSize(indexDir);
               }

               return size;
            }
         });
      }
      catch (PrivilegedActionException e)
      {
         throw new QuotaManagerException(e.getMessage(), e);
      }
   }

   /**
    * @see BaseQuotaManager#accumulateChanges(long)
    */
   protected void accumulateChanges(long delta)
   {
      long dataSize = 0;
      try
      {
         dataSize = quotaPersister.getWorkspaceDataSize(rName, wsName);
      }
      catch (UnknownQuotaDataSizeException e)
      {
         if (LOG.isTraceEnabled())
         {
            LOG.trace(e.getMessage(), e);
         }
      }

      long newDataSize = Math.max(dataSize + delta, 0);
      quotaPersister.setWorkspaceDataSize(rName, wsName, newDataSize);

      defineAlertedState();

      repositoryQuotaManager.accumulateChanges(delta);
   }

   /**
    * Define workspace alerted state based on values of quota limit and data size.
    */
   protected void defineAlertedState()
   {
      try
      {
         long quotaLimit = quotaPersister.getWorkspaceQuota(rName, wsName);
         try
         {
            long dataSize = quotaPersister.getWorkspaceDataSize(rName, wsName);
            alerted.set(dataSize > quotaLimit);
         }
         catch (UnknownQuotaDataSizeException e)
         {
            alerted.set(false);
         }
      }
      catch (UnknownQuotaLimitException e)
      {
         alerted.set(false);
      }
   }

   /**
    * Define if node path should be marked as alerted. It means
    * node data size exceeded quota limit.
    */
   protected void defineAlertedPath(String nodePath)
   {
      try
      {
         long dataSize = quotaPersister.getNodeDataSize(rName, wsName, nodePath);
         try
         {
            long quotaLimit = quotaPersister.getNodeQuotaByPathOrPattern(rName, wsName, nodePath);

            if (dataSize > quotaLimit)
            {
               alertedPaths.remove(nodePath);
            }
            else
            {
               alertedPaths.add(nodePath);
            }
         }
         catch (UnknownQuotaLimitException e)
         {
            alertedPaths.remove(nodePath);
         }
      }
      catch (UnknownQuotaDataSizeException e)
      {
         alertedPaths.remove(nodePath);
      }
   }

   /**
    * Gets all alerted paths.
    */
   protected void defineAlertedPaths()
   {
      alertedPaths = quotaPersister.getAlertedPaths(rName, wsName);
   }

   /**
    * Calculates node data size by asking directly respective {@link WorkspacePersistentDataManager}.  
    */
   protected long getNodeDataSizeDirectly(String nodePath) throws QuotaManagerException
   {
      if (nodePath.equals(JCRPath.ROOT_PATH))
      {
         return getWorkspaceDataSize();
      }

      try
      {
         NodeData node = (NodeData)dataManager.getItemData(Constants.ROOT_UUID);

         JCRPath path = lFactory.parseRelPath(nodePath.substring(1)); // let ignore root entry '[]:1'
         for (QPathEntry entry : path.getInternalPath().getEntries())
         {
            node = (NodeData)dataManager.getItemData(node, entry, ItemType.NODE);
         }

         CalculateNodeDataSizeVisitor visitor = new CalculateNodeDataSizeVisitor(dataManager);
         node.accept(visitor);

         return visitor.getSize();
      }
      catch (RepositoryException e)
      {
         throw new QuotaManagerException(e.getMessage(), e);
      }
   }

   /**
    * Calculate workspace data size by asking directly respective {@link WorkspacePersistentDataManager}.  
    */
   protected long getWorkspaceDataSizeDirectly() throws QuotaManagerException
   {
      try
      {
         return dataManager.getWorkspaceDataSize();
      }
      catch (RepositoryException e)
      {
         throw new QuotaManagerException(e.getMessage(), e);
      }
   }

   /**
    * Traverse over all children nodes and calculate its size.
    */
   private class CalculateNodeDataSizeVisitor extends ItemDataTraversingVisitor
   {

      /**
       * Node data size.
       */
      private long size;

      /**
       * CalculateNodeDataSizeVisitor constructor.
       */
      public CalculateNodeDataSizeVisitor(ItemDataConsumer dataManager)
      {
         super(dataManager);
      }

      /**
       * {@inheritDoc}
       */
      protected void entering(PropertyData property, int level) throws RepositoryException
      {
      }

      /**
       * {@inheritDoc}
       */
      protected void entering(NodeData node, int level) throws RepositoryException
      {
         size += ((WorkspacePersistentDataManager)dataManager).getNodeDataSize(node.getIdentifier());
      }

      /**
       * {@inheritDoc}
       */
      protected void leaving(PropertyData property, int level) throws RepositoryException
      {
      }

      /**
       * {@inheritDoc}
       */
      protected void leaving(NodeData node, int level) throws RepositoryException
      {
      }

      /**
       * Returns calculated node data size.
       */
      public long getSize()
      {
         return size;
      }
   }

   /**
    * {@inheritDoc}
    */
   public void start()
   {
      repositoryQuotaManager.registerWorkspaceQuotaManager(wsName, this);

      try
      {
         long dataSize = quotaPersister.getWorkspaceDataSize(rName, wsName);
         repositoryQuotaManager.accumulateChanges(dataSize);
      }
      catch (UnknownQuotaDataSizeException e)
      {
         Runnable quotaTask = new CalculateWorkspaceDataSizeTask(this);
         executor.execute(quotaTask);
      }
   }

   /**
    * {@inheritDoc}
    */
   public void stop()
   {
      executor.shutdownNow();

      try
      {
         long dataSize = quotaPersister.getWorkspaceDataSize(rName, wsName);
         repositoryQuotaManager.accumulateChanges(-dataSize);
      }
      catch (UnknownQuotaDataSizeException e)
      {
         if (LOG.isTraceEnabled())
         {
            LOG.trace(e.getMessage(), e);
         }
      }

      repositoryQuotaManager.unregisterWorkspaceQuotaManager(wsName);
   }

   // ====================================> Listeners

   /**
    * @link MandatoryItemsPersistenceListener} implementation.
    * 
    * Is not TX aware listener. Will receive changes when data is successfully committed
    * to storage. It is allow accumulate new changed size and redefine alerted states 
    * knowing that transaction will not be rollbacked.
    */
   private class AccumulateChangesListener implements MandatoryItemsPersistenceListener
   {
      /**
       * {@inheritDoc}
       */
      public void onSaveItems(ItemStateChangesLog itemStates)
      {
         // TOOD
      }

      /**
       * {@inheritDoc}
       */
      public boolean isTXAware()
      {
         return false;
      }
   }

   /**
    * @link MandatoryItemsPersistenceListener} implementation.
    * 
    * Is TX aware listener. Will recieve changes before data is committed to storage.
    * It allows to validate does some entity can exceeds quota limit if new changes
    * is coming.
    */
   private class ValidateChangesListener implements MandatoryItemsPersistenceListener
   {
      /**
       * {@inheritDoc}
       * Checks if new changes can exceeds some limits.
       * 
       * @throws IllegalStateException if data size exceeded quota limit
       */
      public void onSaveItems(ItemStateChangesLog itemStates)
      {
         try
         {
            boolean newContentArrivedInWS = false;
            Set<String> checkedAlertedPaths = new HashSet<String>();

            for (ItemState state : itemStates.getAllStates())
            {
               if (state.isAdded() || state.isUpdated() || state.isRenamed())
               {
                  String itemPath;
                  try
                  {
                     itemPath = lFactory.createJCRPath(state.getData().getQPath()).getAsString(false);
                  }
                  catch (RepositoryException e)
                  {
                     throw new IllegalStateException(e.getMessage(), e);
                  }

                  for (String alertedPath : alertedPaths)
                  {
                     if (!checkedAlertedPaths.contains(alertedPath)
                        && PathPatternUtils.acceptDescendant(alertedPath, itemPath))
                     {
                        repositoryQuotaManager.globalQuotaManager.behaveOnQuotaExceeded("Node " + uniqueName
                           + alertedPath + " data size exceeded quota limit");
                     }
                  }

                  newContentArrivedInWS = true;
               }
            }

            if (newContentArrivedInWS)
            {
               if (alerted.get())
               {
                  repositoryQuotaManager.globalQuotaManager.behaveOnQuotaExceeded("Workspace " + uniqueName
                     + " data size exceeded quota limit");

               }

               repositoryQuotaManager.validateAccumulateChanges();
            }
         }
         catch (ExceededQuotaLimitException e)
         {
            throw new IllegalStateException(e.getMessage(), e);
         }
      }

      /**
       * {@inheritDoc}
       */
      public boolean isTXAware()
      {
         return true;
      }
   }

   /**
    * Initialize executor service
    */
   private void initExecutorService()
   {
      this.executor = Executors.newFixedThreadPool(1, new ThreadFactory()
      {
         public Thread newThread(Runnable arg0)
         {
            return new Thread(arg0, "QuotaManagerThread /" + rName + "/" + wsName);
         }
      });
   }

   // ====================================> Backupable

   /**
    * {@inheritDoc}
    */
   public void backup(File storageDir) throws BackupException
   {
      File backupFile = new File(storageDir, BACKUP_FILE_NAME + DBBackup.CONTENT_FILE_SUFFIX);
      doBackup(backupFile);
   }

   /**
    * {@inheritDoc}
    */
   public void clean() throws BackupException
   {
      doClean();
   }

   /**
    * {@inheritDoc}
    */
   public DataRestore getDataRestorer(DataRestoreContext context) throws BackupException
   {
      return new WorkspaceQuotaRestore(context);
   }

   /**
    * {@link DataRestore} implementation for quota. 
    */
   private class WorkspaceQuotaRestore implements DataRestore
   {

      private final File tempFile;

      private final File backupFile;

      /**
       * WorkspaceQuotaRestore constructor.
       */
      WorkspaceQuotaRestore(DataRestoreContext context)
      {
         File storageDir = (File)context.getObject(DataRestoreContext.STORAGE_DIR);
         this.backupFile = new File(storageDir, BACKUP_FILE_NAME + DBBackup.CONTENT_FILE_SUFFIX);

         File tempDir = new File(PrivilegedSystemHelper.getProperty("java.io.tmpdir"));
         this.tempFile = new File(tempDir, "temp.dump");
      }

      /**
       * {@inheritDoc}
       */
      public void clean() throws BackupException
      {
         doBackup(tempFile);
         doClean();
      }

      /**
       * {@inheritDoc}
       */
      public void restore() throws BackupException
      {
         doRestore(backupFile);
      }

      /**
       * {@inheritDoc}
       */
      public void commit() throws BackupException
      {
      }

      /**
       * {@inheritDoc}
       */
      public void rollback() throws BackupException
      {
         doClean();
         doRestore(tempFile);
      }

      /**
       * {@inheritDoc}
       */
      public void close() throws BackupException
      {
         tempFile.delete();
      }
   }

   /**
    * Restores content.
    */
   private void doRestore(File backupFile) throws BackupException
   {
      ZipObjectReader reader = null;
      try
      {
         reader = new ZipObjectReader(PrivilegedFileHelper.zipInputStream(backupFile));
         quotaPersister.restoreWorkspaceData(rName, wsName, reader);
      }
      catch (IOException e)
      {
         throw new BackupException(e);
      }
      finally
      {
         if (reader != null)
         {
            try
            {
               reader.close();
            }
            catch (IOException e)
            {
               LOG.error("Can't close input stream", e);
            }
         }
      }

      try
      {
         long dataSize = quotaPersister.getWorkspaceDataSize(rName, wsName);
         repositoryQuotaManager.accumulateChanges(dataSize);
      }
      catch (UnknownQuotaDataSizeException e)
      {
         if (LOG.isTraceEnabled())
         {
            LOG.trace(e.getMessage(), e);
         }
      }

      defineAlertedState();
      defineAlertedPaths();
   }

   /**
    * Backups data to define file.
    */
   private void doBackup(File backupFile) throws BackupException
   {
      ZipObjectWriter writer = null;
      try
      {
         writer = new ZipObjectWriter(PrivilegedFileHelper.zipOutputStream(backupFile));
         quotaPersister.backupWorkspaceData(rName, wsName, writer);
      }
      catch (IOException e)
      {
         throw new BackupException(e);
      }
      finally
      {
         if (writer != null)
         {
            try
            {
               writer.close();
            }
            catch (IOException e)
            {
               LOG.error("Can't close output stream", e);
            }
         }
      }
   }

   private void doClean() throws BackupException
   {
      try
      {
         long dataSize = quotaPersister.getWorkspaceDataSize(rName, wsName);
         accumulateChanges(-dataSize);
      }
      catch (UnknownQuotaDataSizeException e)
      {
         if (LOG.isTraceEnabled())
         {
            LOG.trace(e.getMessage(), e);
         }
      }

      quotaPersister.clearWorkspaceData(rName, wsName);
      alertedPaths.clear();
   }

   // =========================================> Suspendable

   /**
    * {@inheritDoc}
    */
   public void suspend() throws SuspendException
   {
      executor.shutdown();
      try
      {
         executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
      }
      catch (InterruptedException e)
      {
         throw new SuspendException(e.getMessage(), e);
      }

      isSuspended.set(true);
   }

   /**
    * {@inheritDoc}
    */
   public void resume() throws ResumeException
   {
      initExecutorService();

      isSuspended.set(false);
   }

   /**
    * {@inheritDoc}
    */
   public boolean isSuspended()
   {
      return isSuspended.get();
   }

   /**
    * {@inheritDoc}
    */
   public int getPriority()
   {
      return Suspendable.PRIORITY_LOW;
   }
}
