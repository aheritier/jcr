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
import org.exoplatform.services.jcr.config.RepositoryEntry;
import org.exoplatform.services.jcr.config.WorkspaceEntry;
import org.exoplatform.services.jcr.core.WorkspaceContainerFacade;
import org.exoplatform.services.jcr.dataflow.ItemDataConsumer;
import org.exoplatform.services.jcr.dataflow.ItemDataTraversingVisitor;
import org.exoplatform.services.jcr.dataflow.ItemState;
import org.exoplatform.services.jcr.dataflow.ItemStateChangesLog;
import org.exoplatform.services.jcr.dataflow.persistent.MandatoryItemsPersistenceListener;
import org.exoplatform.services.jcr.dataflow.persistent.PersistenceCommitListener;
import org.exoplatform.services.jcr.dataflow.persistent.PersistenceRollbackListener;
import org.exoplatform.services.jcr.datamodel.ItemType;
import org.exoplatform.services.jcr.datamodel.NodeData;
import org.exoplatform.services.jcr.datamodel.PropertyData;
import org.exoplatform.services.jcr.datamodel.QPath;
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
import org.exoplatform.services.jcr.storage.WorkspaceDataContainer;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.rpc.RPCService;
import org.jboss.cache.util.concurrent.ConcurrentHashSet;
import org.picocontainer.Startable;

import java.io.Externalizable;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.lang.reflect.Method;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
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
    * {@link WorkspacePersistentDataManager}.
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
    * {@link RPCService}
    */
   protected final RPCService rpcService;

   /**
    * {@link MandatoryItemsPersistenceListener} implementation.
    */
   protected final ValidateChangesListener validateChangesListener = new ValidateChangesListener();

   /**
    * {@link MandatoryItemsPersistenceListener} implementation.
    */
   protected final AccumulateChangesListener accumulateChangesListener = new AccumulateChangesListener();

   /**
    * File name for backuped data.
    */
   protected static final String BACKUP_FILE_NAME = "quota";

   /**
    * Indicates if component suspended or not.
    */
   protected AtomicBoolean isSuspended = new AtomicBoolean();

   /**
    * Contains node paths for which {@link CalculateNodeDataSizeTask} currently is run. 
    * Does't allow execution several tasks over common path.
    */
   protected Set<String> runNodesTasks = new ConcurrentHashSet<String>();

   /**
    * Pending changes of current save. If save failed changes will be removed, otherwise
    * is moved into changes log to be pushed to coordinator by timer.
    */
   protected ThreadLocal<ChangesItem> pendingChanges = new ThreadLocal<ChangesItem>();

   /**
    * Accumulates changes of every save.  
    */
   protected ChangesLog changesLog = new ChangesLog();

   /**
    * @see BaseQuotaManager#testCase.
    */
   protected final boolean testCase;

   /**
    * BaseWorkspaceQuotaManager constructor.
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
      this.rpcService = rQuotaManager.globalQuotaManager.rpcService;
      this.testCase = rQuotaManager.globalQuotaManager.testCase;

      initExecutorService();

      dataManager.addPersistenceRollbackListener(accumulateChangesListener);
      dataManager.addPersistenceCommitListener(accumulateChangesListener);
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
         return getWorkspaceDataSize();
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
      if (nodePath.equals(JCRPath.ROOT_PATH))
      {
         return getWorkspaceQuota();
      }

      return quotaPersister.getNodeQuotaOrGroupOfNodesQuota(rName, wsName, nodePath);
   }

   /**
    * @see QuotaManager#setNodeQuota(String, String, String, long, boolean)
    */
   @Managed
   @ManagedDescription("Sets a node quota limit")
   public void setNodeQuota(String nodePath, long quotaLimit, boolean asyncUpdate) throws QuotaManagerException
   {
      if (nodePath.equals(JCRPath.ROOT_PATH))
      {
         setWorkspaceQuota(quotaLimit);
      }
      else
      {
         try
         {
            quotaPersister.getNodeQuotaOrGroupOfNodesQuota(rName, wsName, nodePath);
         }
         catch (UnknownQuotaLimitException e)
         {
            // have deal with setting new quota limit value
            quotaPersister.setNodeQuota(rName, wsName, nodePath, quotaLimit, asyncUpdate);
            tryExecuteCalculateNodeDataSizeTask(nodePath);

            return;
         }

         // have deal with changing quota limit value
         quotaPersister.setNodeQuota(rName, wsName, nodePath, quotaLimit, asyncUpdate);

         try
         {
            quotaPersister.getNodeDataSize(rName, wsName, nodePath);
         }
         catch (UnknownQuotaDataSizeException e)
         {
            tryExecuteCalculateNodeDataSizeTask(nodePath);
         }
      }
   }

   /**
    * Executes task {@link CalculateNodeDataSizeTask} if it is not in list
    * current executing tasks.
    */
   private void tryExecuteCalculateNodeDataSizeTask(String nodePath)
   {
      if (!runNodesTasks.contains(nodePath))
      {
         synchronized (runNodesTasks)
         {
            if (!runNodesTasks.contains(nodePath))
            {
               Runnable task = new CalculateNodeDataSizeTask(this, nodePath, runNodesTasks);
               executor.execute(task);
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
      if (patternPath.equals(JCRPath.ROOT_PATH))
      {
         setWorkspaceQuota(quotaLimit);
      }
      else
      {
         quotaPersister.setGroupOfNodesQuota(rName, wsName, patternPath, quotaLimit, asyncUpdate);
      }
   }

   /**
    * @see QuotaManager#removeNodeQuota(String, String, String)
    */
   @Managed
   @ManagedDescription("Removes a quota limit for a node")
   public void removeNodeQuota(String nodePath) throws QuotaManagerException
   {
      if (nodePath.equals(JCRPath.ROOT_PATH))
      {
         removeWorkspaceQuota();
      }
      else
      {
         quotaPersister.removeNodeQuotaAndDataSize(rName, wsName, nodePath);
      }
   }

   /**
    * @see QuotaManager#removeGroupOfNodesQuota(String, String, String)
    */
   @Managed
   @ManagedDescription("Removes a quota limit for a bunch of nodes")
   public void removeGroupOfNodesQuota(String patternPath) throws QuotaManagerException
   {
      if (patternPath.equals(JCRPath.ROOT_PATH))
      {
         removeWorkspaceQuota();
      }
      else
      {
         quotaPersister.removeGroupOfNodesAndDataSize(rName, wsName, patternPath);
      }
   }

   /**
    * @see QuotaManager#setWorkspaceQuota(String, String, long)
    */
   @ManagedDescription("Sets workspace quota limit")
   public void setWorkspaceQuota(long quotaLimit) throws QuotaManagerException
   {
      quotaPersister.setWorkspaceQuota(rName, wsName, quotaLimit);
   }

   /**
    * @see QuotaManager#removeWorkspaceQuota(String, String, long)
    */
   @ManagedDescription("Removes workspace quota limit")
   public void removeWorkspaceQuota() throws QuotaManagerException
   {
      quotaPersister.removeWorkspaceQuota(rName, wsName);
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
    * @see BaseQuotaManager#accumulatePersistedChanges(long)
    */
   protected void accumulatePersistedChanges(long delta)
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

      repositoryQuotaManager.accumulatePersistedChanges(delta);
   }

   /**
    * @see WorkspaceQuotaManager#accumulateChanges(long)
    */
   protected void accumulatePersistedNodesChanges(Map<String, Long> nodesDelta, Set<String> unknownNodesDelta)
   {
      Set<String> trackedNodes = quotaPersister.getAllTrackedNodes(rName, wsName);
      for (String nodePath : unknownNodesDelta)
      {
         for (String trackedPath : trackedNodes)
         {
            if (trackedPath.startsWith(nodePath))
            {
               quotaPersister.removeNodeDataSize(rName, wsName, trackedPath);
            }
         }
      }

      for (Entry<String, Long> entry : nodesDelta.entrySet())
      {
         String nodePath = entry.getKey();
         Long delta = entry.getValue();

         try
         {
            long dataSize = delta + quotaPersister.getNodeDataSize(rName, wsName, nodePath);
            quotaPersister.setNodeDataSize(rName, wsName, nodePath, dataSize);
         }
         catch (UnknownQuotaDataSizeException e)
         {
            tryExecuteCalculateNodeDataSizeTask(nodePath);
         }
      }
   }

   /**
    * Calculates node data size by asking directly respective {@link WorkspacePersistentDataManager}. 
    */
   public long getNodeDataSizeDirectly(String nodePath) throws QuotaManagerException
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

            if (node == null) // may be already removed
            {
               return 0;
            }
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
   public long getWorkspaceDataSizeDirectly() throws QuotaManagerException
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
         repositoryQuotaManager.accumulatePersistedChanges(dataSize);
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
      awaitTasksTermination();

      try
      {
         long dataSize = quotaPersister.getWorkspaceDataSize(rName, wsName);
         repositoryQuotaManager.accumulatePersistedChanges(-dataSize);
      }
      catch (UnknownQuotaDataSizeException e)
      {
         if (LOG.isTraceEnabled())
         {
            LOG.trace(e.getMessage(), e);
         }
      }

      repositoryQuotaManager.unregisterWorkspaceQuotaManager(wsName);

      dataManager.removeItemPersistenceListener(validateChangesListener);
      dataManager.removePersistenceCommitListener(accumulateChangesListener);
      dataManager.removePersistenceRollbackListener(accumulateChangesListener);
   }

   // ====================================> Listeners

   /**
    * Accumulate changes when transaction is committed.
    */
   private class AccumulateChangesListener implements PersistenceRollbackListener, PersistenceCommitListener
   {

      /**
       * {@inheritDoc}
       */
      public void onCommit()
      {
         ChangesItem changesItem = pendingChanges.get();
         try
         {
            if (!testCase)
            {
               changesLog.add(pendingChanges.get());
            }
            else
            {
               // apply changes instantly
               accumulatePersistedChanges(changesItem.workspaceDelta);
               accumulatePersistedNodesChanges(changesItem.calculatedNodesDelta, changesItem.unknownNodesDelta);
            }
         }
         finally
         {
            pendingChanges.remove();
         }
      }

      /**
       * {@inheritDoc}
       */
      public void onRollback()
      {
         pendingChanges.remove();
      }


   }

   /**
    * @link MandatoryItemsPersistenceListener} implementation.
    * 
    * Is TX aware listener. Will receive changes before data is committed to storage.
    * It allows to validate does some entity can exceeds quota limit if new changes
    * is coming.
    */
   private class ValidateChangesListener implements MandatoryItemsPersistenceListener
   {
      /**
       * {@inheritDoc}
       * Checks if new changes can exceeds some limits. It either can be node, workspace, 
       * repository or global JCR instance.
       * 
       * @throws IllegalStateException if data size exceeded quota limit
       */
      public void onSaveItems(ItemStateChangesLog itemStates)
      {
         try
         {
            ChangesItem changesItem = new ChangesItem();

            for (ItemState state : itemStates.getAllStates())
            {
               if (!state.getData().isNode())
               {
                  String itemPath = getPath(state.getData().getQPath().makeParentPath());

                  // update changed size for every node
                  Set<String> quotableParents = quotaPersister.getAllQuotableParentNodes(rName, wsName, itemPath);
                  for (String parent : quotableParents)
                  {
                     Long oldDelta = changesItem.calculatedNodesDelta.get(parent);
                     Long newDelta = state.getChangedSize() + (oldDelta != null ? oldDelta : 0);

                     changesItem.calculatedNodesDelta.put(parent, newDelta);
                  }

                  // update changed size for workspace
                  changesItem.workspaceDelta += state.getChangedSize();
               }
               else
               {
                  if (state.isPathChanged())
                  {
                     String itemPath = getPath(state.getData().getQPath());
                     String oldPath = getPath(state.getOldPath());

                     changesItem.unknownNodesDelta.add(itemPath);
                     changesItem.unknownNodesDelta.add(oldPath);
                  }
               }
            }

            pendingChanges.set(changesItem);

            validatePendingChanges(changesItem.workspaceDelta);
            validatePendingNodesChanges(changesItem.calculatedNodesDelta);
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

      /**
       * @see BaseQuotaManager#validatePendingChanges(long)
       */
      private void validatePendingChanges(long delta) throws ExceededQuotaLimitException
      {
         delta += changesLog.getWorkspaceDelta();

         repositoryQuotaManager.validatePendingChanges(delta);
         try
         {
            long quotaLimit = quotaPersister.getWorkspaceQuota(rName, wsName);

            try
            {
               long dataSize = quotaPersister.getWorkspaceDataSize(rName, wsName);

               if (dataSize + delta > quotaLimit)
               {
                  repositoryQuotaManager.globalQuotaManager.behaveOnQuotaExceeded("Workspace " + uniqueName
                     + " data size exceeded quota limit");
               }
            }
            catch (UnknownQuotaDataSizeException e)
            {
               return;
            }
         }
         catch (UnknownQuotaLimitException e)
         {
            return;
         }
      }

      /**
       * @see BaseQuotaManager#validatePendingChanges(long)
       */
      private void validatePendingNodesChanges(Map<String, Long> nodesDelta) throws ExceededQuotaLimitException
      {
         for (Entry<String, Long> entry : nodesDelta.entrySet())
         {
            String nodePath = entry.getKey();
            Long delta = entry.getValue() + changesLog.getNodeDelta(nodePath);

            try
            {
               long dataSize = quotaPersister.getNodeDataSize(rName, wsName, nodePath);
               try
               {
                  long quotaLimit = quotaPersister.getNodeQuotaOrGroupOfNodesQuota(rName, wsName, nodePath);
                  if (dataSize + delta > quotaLimit)
                  {
                     repositoryQuotaManager.globalQuotaManager.behaveOnQuotaExceeded("Node " + nodePath
                        + " data size exceeded quota limit");
                  }
               }
               catch (UnknownQuotaLimitException e)
               {
                  continue;
               }
            }
            catch (UnknownQuotaDataSizeException e)
            {
               continue;
            }
         }
      }
   }

   /**
    * Initialize executor service
    */
   protected void initExecutorService()
   {
      this.executor = Executors.newFixedThreadPool(1, new ThreadFactory()
      {
         public Thread newThread(Runnable arg0)
         {
            return new Thread(arg0, "QuotaManagerThread " + uniqueName);
         }
      });
   }

   /**
    * Returns item absolute path.
    * 
    * @param path
    *          {@link QPath} representation
    * @throws IllegalStateException if something wrong
    */
   private String getPath(QPath path)
   {
      try
      {
         return lFactory.createJCRPath(path).getAsString(false);
      }
      catch (RepositoryException e)
      {
         throw new IllegalStateException(e.getMessage(), e);
      }
   }

   /**
    * Awaits until all tasks will be done.
    */
   protected void awaitTasksTermination()
   {
      executor.shutdown();
      try
      {
         executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
      }
      catch (InterruptedException e)
      {
         LOG.warn("Termination has been interrupted");
      }
   }

   // ======================================> Backup & Suspend

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
         PrivilegedFileHelper.delete(tempFile);
      }
   }

   /**
    * Restores content.
    */
   private void doRestore(File backupFile) throws BackupException
   {
      ZipObjectReader in = null;
      try
      {
         in = new ZipObjectReader(PrivilegedFileHelper.zipInputStream(backupFile));
         quotaPersister.restoreWorkspaceData(rName, wsName, in);
      }
      catch (IOException e)
      {
         throw new BackupException(e);
      }
      finally
      {
         if (in != null)
         {
            try
            {
               in.close();
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
         repositoryQuotaManager.accumulatePersistedChanges(dataSize);
      }
      catch (UnknownQuotaDataSizeException e)
      {
         if (LOG.isTraceEnabled())
         {
            LOG.trace(e.getMessage(), e);
         }
      }
   }

   /**
    * Backups data to define file.
    */
   private void doBackup(File backupFile) throws BackupException
   {
      ZipObjectWriter out = null;
      try
      {
         out = new ZipObjectWriter(PrivilegedFileHelper.zipOutputStream(backupFile));
         quotaPersister.backupWorkspaceData(rName, wsName, out);
      }
      catch (IOException e)
      {
         throw new BackupException(e);
      }
      finally
      {
         if (out != null)
         {
            try
            {
               out.close();
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
         repositoryQuotaManager.accumulatePersistedChanges(-dataSize);
      }
      catch (UnknownQuotaDataSizeException e)
      {
         if (LOG.isTraceEnabled())
         {
            LOG.trace(e.getMessage(), e);
         }
      }

      quotaPersister.clearWorkspaceData(rName, wsName);
   }

   /**
    * {@inheritDoc}
    */
   public void suspend() throws SuspendException
   {
      executor.shutdownNow();
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

   // =================================================> Changes log

   /** 
    * Returns current changes log.
    */
   protected ChangesLog getChangesLog()
   {
      return changesLog;
   }

   /**
    * Wraps changed data size information of particular save. First is put into pending changes
    * and after save is performed being moved into changes log.
    */
   class ChangesItem implements Externalizable
   {
      /**
       * Contains calculated workspace data size changes of particular save. 
       */
      long workspaceDelta;

      /**
       * Contains calculated nodes data size changes of particular save.
       * Represents {@link Map} with absolute node path as key and changed node 
       * data size of all its descendants as value respectively.
       */
      Map<String, Long> calculatedNodesDelta = new HashMap<String, Long>();;

      /**
       * Set absolute paths of nodes for which changes were made but changed size is unknown. Most
       * famous case when {@link WorkspaceDataContainer#TRIGGER_EVENTS_FOR_DESCENDENTS_ON_RENAME} is 
       * set to false and move operation is performed.
       */
      Set<String> unknownNodesDelta = new HashSet<String>();

      /**
       * Merges current changes with new one.
       */
      void merge(ChangesItem changesItem)
      {
         workspaceDelta += changesItem.workspaceDelta;

         for (Entry<String, Long> changesEntry : changesItem.calculatedNodesDelta.entrySet())
         {
            String nodePath = changesEntry.getKey();
            Long currentDelta = changesEntry.getValue();

            Long oldDelta = calculatedNodesDelta.get(nodePath);
            Long newDelta = currentDelta + (oldDelta == null ? 0 : oldDelta);

            calculatedNodesDelta.put(nodePath, newDelta);
         }

         for (String path : changesItem.unknownNodesDelta)
         {
            unknownNodesDelta.add(path);
         }
      }

      /**
       * {@inheritDoc}
       */
      public void writeExternal(ObjectOutput out) throws IOException
      {
         out.writeLong(workspaceDelta);

         out.writeInt(calculatedNodesDelta.size());
         for (Entry<String, Long> entry : calculatedNodesDelta.entrySet())
         {
            writeString(out, entry.getKey());
            out.writeLong(entry.getValue());
         }

         out.writeInt(unknownNodesDelta.size());
         for (String path : unknownNodesDelta)
         {
            writeString(out, path);
         }
      }

      private void writeString(ObjectOutput out, String str) throws IOException
      {
         byte[] data = str.getBytes(Constants.DEFAULT_ENCODING);
         out.writeInt(data.length);
         out.write(data);
      }

      /**
       * {@inheritDoc}
       */
      public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException
      {
         this.workspaceDelta = in.readLong();

         int size = in.readInt();
         this.calculatedNodesDelta = new HashMap<String, Long>(size);
         for (int i = 0; i < size; i++)
         {
            String nodePath = readString(in);
            Long delta = in.readLong();

            calculatedNodesDelta.put(nodePath, delta);
         }

         size = in.readInt();
         this.unknownNodesDelta = new HashSet<String>();
         for (int i = 0; i < size; i++)
         {
            String nodePath = readString(in);
            unknownNodesDelta.add(nodePath);
         }
      }

      private String readString(ObjectInput in) throws IOException
      {
         byte[] data = new byte[in.readInt()];
         in.readFully(data);

         return new String(data, Constants.DEFAULT_ENCODING);
      }
   }

   /**
    * Accumulates changes of saves during whole period. Time from time
    * all changes are pushed to coordinator.
    */
   class ChangesLog extends ConcurrentLinkedQueue<ChangesItem>
   {
      /**
       * Returns total workspace changed size.
       */
      public long getWorkspaceDelta()
      {
         long wsDelta = 0;
         
         Iterator<ChangesItem> changes = iterator();
         while (changes.hasNext())
         {
            wsDelta += changes.next().workspaceDelta;
         }
         
         return wsDelta;
      }

      /**
       * Return total changed size for particular node.
       */
      public long getNodeDelta(String nodePath)
      {
         long nodeDelta = 0;

         Iterator<ChangesItem> changes = iterator();
         while (changes.hasNext())
         {
            nodeDelta += changes.next().calculatedNodesDelta.get(nodePath);
         }

         return nodeDelta;
      }
   }
}
