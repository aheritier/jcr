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
import org.exoplatform.services.jcr.dataflow.persistent.MandatoryItemsPersistenceListener;
import org.exoplatform.services.jcr.dataflow.persistent.PersistenceCommitListener;
import org.exoplatform.services.jcr.dataflow.persistent.PersistenceRollbackListener;
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
import org.exoplatform.services.jcr.impl.backup.rdbms.DataRestoreContext;
import org.exoplatform.services.jcr.impl.core.JCRPath;
import org.exoplatform.services.jcr.impl.core.LocationFactory;
import org.exoplatform.services.jcr.impl.core.RepositoryImpl;
import org.exoplatform.services.jcr.impl.core.query.SearchManager;
import org.exoplatform.services.jcr.impl.dataflow.persistent.WorkspacePersistentDataManager;
import org.exoplatform.services.jcr.impl.util.io.DirectoryHelper;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.rpc.RPCException;
import org.exoplatform.services.rpc.RPCService;
import org.exoplatform.services.rpc.RemoteCommand;
import org.jboss.cache.util.concurrent.ConcurrentHashSet;
import org.picocontainer.Startable;

import java.io.File;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.jcr.ItemNotFoundException;
import javax.jcr.RepositoryException;

/**
 * {@link WorkspaceQuotaManager} listens all changes performed in
 * {@link WorkspacePersistentDataManager} and checks if some of them might
 * exceed defined quota limit. If so, exception might be thrown or warning printing.
 * Then all changes are devided into two part. First one should be applies synchronously
 * and are passed to coordinator to apply instantly. Another one are put into changes
 * log and to be passed to coordinator by timer. It is managed by 
 * {@link RepositoryQuotaManager} 
 * 
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
   protected final Log LOG = ExoLogger.getLogger("exo.jcr.component.core.WorkspaceQuotaManager");

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
    * Executor service per workspace to be able to suspend all tasks devoted current workspace..
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
   protected final ValidateChangesListener validateChangesListener;

   /**
    * {@link PersistenceRollbackListener} and {@link PersistenceCommitListener} implementations.
    */
   protected final AccumulateChangesListener accumulateChangesListener;

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
    * Remote command is obligated to push changes log to coordinator.
    */
   protected RemoteCommand pushChangesLogTask;

   /**
    * Remote command is obligated to calculate node data size directly asking
    * {@link WorkspacePersistentDataManager}.
    */
   protected RemoteCommand calculateNodeDataSizeTask;

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
      initRemoteCommands();

      validateChangesListener = new ValidateChangesListener(this);
      accumulateChangesListener = new AccumulateChangesListener(this);

      dataManager.addPersistenceRollbackListener(accumulateChangesListener);
      dataManager.addPersistenceCommitListener(accumulateChangesListener);
      dataManager.addItemPersistenceListener(validateChangesListener);
   }

   /**
    * @see QuotaManager#getNodeDataSize(String, String, String)
    */
   @Managed
   @ManagedDescription("Returns a node data size")
   public long getNodeDataSize(@ManagedName("nodePath") String nodePath) throws QuotaManagerException
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
   public long getNodeQuota(@ManagedName("nodePath") String nodePath) throws QuotaManagerException
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
   public void setNodeQuota(@ManagedName("nodePath") String nodePath, @ManagedName("quotaLimit") long quotaLimit,
      @ManagedName("asyncUpdate") boolean asyncUpdate) throws QuotaManagerException
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
            executeCalculateNodeDataSizeTask(nodePath);

            return;
         }

         // have deal with changing quota limit value
         quotaPersister.setNodeQuota(rName, wsName, nodePath, quotaLimit, asyncUpdate);

         try
         {
            quotaPersister.getNodeDataSize(rName, wsName, nodePath);
         }
         catch (UnknownDataSizeException e)
         {
            executeCalculateNodeDataSizeTask(nodePath);
         }
      }
   }

   /**
    * @see QuotaManager#setGroupOfNodesQuota(String, String, String, long, boolean)
    */
   @Managed
   @ManagedDescription("Sets a quota limit for a bunch of nodes")
   public void setGroupOfNodesQuota(@ManagedName("patternPath") String patternPath,
      @ManagedName("quotaLimit") long quotaLimit, @ManagedName("asyncUpdate") boolean asyncUpdate)
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
   public void removeNodeQuota(@ManagedName("nodePath") String nodePath) throws QuotaManagerException
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
   public void removeGroupOfNodesQuota(@ManagedName("patternPath") String patternPath) throws QuotaManagerException
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
   @Managed
   @ManagedDescription("Sets workspace quota limit")
   public void setWorkspaceQuota(long quotaLimit) throws QuotaManagerException
   {
      quotaPersister.setWorkspaceQuota(rName, wsName, quotaLimit);
   }

   /**
    * @see QuotaManager#removeWorkspaceQuota(String, String, long)
    */
   @Managed
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
   protected void accumulatePersistedChanges(long delta) throws QuotaManagerException
   {
      long dataSize = 0;
      try
      {
         dataSize = quotaPersister.getWorkspaceDataSize(rName, wsName);
      }
      catch (UnknownDataSizeException e)
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
      throws QuotaManagerException
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
            quotaPersister.setNodeDataSizeIfQuotaExists(rName, wsName, nodePath, dataSize);
         }
         catch (UnknownDataSizeException e)
         {
            executeCalculateNodeDataSizeTask(nodePath);
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
            node = (NodeData)dataManager.getItemData(node, entry, ItemType.NODE, false);

            if (node == null) // may be already removed
            {
               throw new ItemNotFoundException("Node " + nodePath + " not found in workspace");
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

      boolean isCoordinator;
      try
      {
         isCoordinator = rpcService.isCoordinator();
      }
      catch (RPCException e)
      {
         throw new IllegalStateException(e.getMessage(), e);
      }

      if (isCoordinator)
      {
         try
         {
            quotaPersister.getWorkspaceDataSize(rName, wsName);
         }
         catch (UnknownDataSizeException e)
         {
            calculateWorkspaceDataSize();
         }
      }
   }

   /**
    * {@inheritDoc}
    */
   public void stop()
   {
      executor.shutdownNow();

      repositoryQuotaManager.unregisterWorkspaceQuotaManager(wsName);

      dataManager.removeItemPersistenceListener(validateChangesListener);
      dataManager.removePersistenceCommitListener(accumulateChangesListener);
      dataManager.removePersistenceRollbackListener(accumulateChangesListener);

      rpcService.unregisterCommand(pushChangesLogTask);
      rpcService.unregisterCommand(calculateNodeDataSizeTask);
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
      WorkspaceQuotaRestore wqr = new WorkspaceQuotaRestore(this, storageDir);
      wqr.backup();
   }

   /**
    * {@inheritDoc}
    */
   public void clean() throws BackupException
   {
      File storageDir = new File(PrivilegedSystemHelper.getProperty("java.io.tmpdir"));

      WorkspaceQuotaRestore wqr = new WorkspaceQuotaRestore(this, storageDir);
      wqr.clean();
   }

   /**
    * {@inheritDoc}
    */
   public DataRestore getDataRestorer(DataRestoreContext context) throws BackupException
   {
      return new WorkspaceQuotaRestore(this, context);
   }

   /**
    * {@inheritDoc}
    */
   public void suspend() throws SuspendException
   {
      awaitTasksTermination();
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

   /**
    * Initialize remote commands.
    */
   protected void initRemoteCommands()
   {
      pushChangesLogTask = new RemoteCommand()
      {
         /**
          * {@inheritDoc}
          */
         public String getId()
         {
            return "WorkspaceQuotaManager-" + uniqueName + "-pushChangesLogTask";
         }

         /**
          * Accumulates persisted changes.
          */
         public Serializable execute(Serializable[] args) throws Throwable
         {
            ChangesItem changesItem = (ChangesItem)args[0];

            accumulatePersistedChanges(changesItem.workspaceDelta);
            accumulatePersistedNodesChanges(changesItem.calculatedNodesDelta, changesItem.unknownNodesDelta);

            return null;
         }
      };

      calculateNodeDataSizeTask = new RemoteCommand()
      {
         /**
          * {@inheritDoc}
          */
         public String getId()
         {
            return "WorkspaceQuotaManager-" + uniqueName + "-calculateNodeDataSizeTask";
         }

         /**
          * Executes task {@link CalculateNodeDataSizeTask} if it is not in a list
          * of current executing tasks. 
          */
         public Serializable execute(Serializable[] args) throws Throwable
         {
            String nodePath = (String)args[0];

            if (!runNodesTasks.contains(nodePath))
            {
               synchronized (runNodesTasks)
               {
                  if (!runNodesTasks.contains(nodePath))
                  {
                     Runnable task = new CalculateNodeDataSizeTask(WorkspaceQuotaManager.this, nodePath, runNodesTasks);
                     executor.execute(task);
                  }
               }
            }

            return null;
         }
      };

      rpcService.registerCommand(pushChangesLogTask);
      rpcService.registerCommand(calculateNodeDataSizeTask);
   }

   /**
    * Push changes to coordinator to apply.
    */
   protected void pushChangesToCoordinator(ChangesItem changesItem, boolean synchronous) throws SecurityException,
      RPCException
   {
      if (!changesItem.isEmpty())
      {
         rpcService.executeCommandOnCoordinator(pushChangesLogTask, synchronous, changesItem);
      }
   }

   /**
    * Execute task {@link CalculateNodeDataSizeTask} on coordinator.
    */
   protected void executeCalculateNodeDataSizeTask(String nodePath) throws QuotaManagerException
   {
      try
      {
         rpcService.executeCommandOnCoordinator(calculateNodeDataSizeTask, false, nodePath);
      }
      catch (SecurityException e)
      {
         throw new QuotaManagerException("Can't calculate node data size task", e.getCause());
      }
      catch (RPCException e)
      {
         throw new QuotaManagerException("Can't calculate node data size task", e.getCause());
      }
   }

   /**
    * Calculates and accumulates workspace data size.
    */
   private void calculateWorkspaceDataSize()
   {
      long dataSize;
      try
      {
         dataSize = getWorkspaceDataSizeDirectly();
      }
      catch (QuotaManagerException e1)
      {
         throw new IllegalStateException("Can't calculate workspace data size", e1);
      }

      quotaPersister.setWorkspaceDataSize(rName, wsName, dataSize);
      repositoryQuotaManager.accumulatePersistedChanges(dataSize);
   }

}
