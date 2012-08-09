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
import org.exoplatform.services.jcr.dataflow.persistent.PersistenceCommitListener;
import org.exoplatform.services.jcr.dataflow.persistent.PersistenceRollbackListener;
import org.exoplatform.services.jcr.datamodel.ItemType;
import org.exoplatform.services.jcr.datamodel.NodeData;
import org.exoplatform.services.jcr.datamodel.PropertyData;
import org.exoplatform.services.jcr.datamodel.QPath;
import org.exoplatform.services.jcr.datamodel.QPathEntry;
import org.exoplatform.services.jcr.impl.Constants;
import org.exoplatform.services.jcr.impl.core.JCRPath;
import org.exoplatform.services.jcr.impl.core.LocationFactory;
import org.exoplatform.services.jcr.impl.core.RepositoryImpl;
import org.exoplatform.services.jcr.impl.core.query.SearchManager;
import org.exoplatform.services.jcr.impl.dataflow.persistent.WorkspacePersistentDataManager;
import org.exoplatform.services.jcr.impl.util.io.DirectoryHelper;
import org.exoplatform.services.jcr.storage.WorkspaceDataContainer;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.rpc.RPCService;
import org.jboss.cache.util.concurrent.ConcurrentHashSet;
import org.picocontainer.Startable;

import java.io.File;
import java.lang.reflect.Method;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import javax.jcr.RepositoryException;

/**
 * @author <a href="abazko@exoplatform.com">Anatoliy Bazko</a>
 * @version $Id: BaseWorkspaceQuotaManager.java 34360 2009-07-22 23:58:59Z tolusha $
 */
@Managed
@NameTemplate(@Property(key = "service", value = "WorkspaceQuotaManager"))
public abstract class BaseWorkspaceQuotaManager implements Startable, WorkspaceQuotaManager2
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
    * Contains node paths for which {@link CalculateNodeDataSizeTask} currently is run. 
    * Does't allow execution several tasks over common path.
    */
   protected Set<String> runNodesTasks = new ConcurrentHashSet<String>();

   /**
    * Contains calculated nodes data size changes of last save.
    * Will be cleared on persistence rollback or commit.
    */
   protected ThreadLocal<Map<String, Long>> nodesDataSizeChanges = new ThreadLocal<Map<String, Long>>();

   /**
    * List of nodes absolute paths for which changes were made but changed size is unknown. Most
    * famous case when {@link WorkspaceDataContainer#TRIGGER_EVENTS_FOR_DESCENDENTS_ON_RENAME} is 
    * set to false. 
    */
   protected ThreadLocal<Set<String>> unknownNodesDataSizeChanges = new ThreadLocal<Set<String>>();

   /**
    * Contains calculated workspace data size changes of last save.
    * Will be cleared on persistence rollback or commit.
    */
   protected ThreadLocal<Long> workspaceDataSizeChanges = new ThreadLocal<Long>();

   /**
    * BaseWorkspaceQuotaManager constructor.
    */
   public BaseWorkspaceQuotaManager(RepositoryImpl repository, RepositoryQuotaManager rQuotaManager,
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

      initExecutorService();

      dataManager.addPersistenceRollbackListener(accumulateChangesListener);
      dataManager.addPersistenceCommitListener(accumulateChangesListener);
      dataManager.addItemPersistenceListener(validateChangesListener);
   }

   /**
    * @see org.exoplatform.services.jcr.impl.quota.WorkspaceQuotaManager2#getNodeDataSize(java.lang.String)
    */
   @Override
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
    * @see org.exoplatform.services.jcr.impl.quota.WorkspaceQuotaManager2#getNodeQuota(java.lang.String)
    */
   @Override
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
    * @see org.exoplatform.services.jcr.impl.quota.WorkspaceQuotaManager2#setNodeQuota(java.lang.String, long, boolean)
    */
   @Override
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
    * @see org.exoplatform.services.jcr.impl.quota.WorkspaceQuotaManager2#setGroupOfNodesQuota(java.lang.String, long, boolean)
    */
   @Override
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
    * @see org.exoplatform.services.jcr.impl.quota.WorkspaceQuotaManager2#removeNodeQuota(java.lang.String)
    */
   @Override
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
    * @see org.exoplatform.services.jcr.impl.quota.WorkspaceQuotaManager2#removeGroupOfNodesQuota(java.lang.String)
    */
   @Override
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
    * @see org.exoplatform.services.jcr.impl.quota.WorkspaceQuotaManager2#setWorkspaceQuota(long)
    */
   @Override
   @ManagedDescription("Sets workspace quota limit")
   public void setWorkspaceQuota(long quotaLimit) throws QuotaManagerException
   {
      quotaPersister.setWorkspaceQuota(rName, wsName, quotaLimit);
   }

   /**
    * @see org.exoplatform.services.jcr.impl.quota.WorkspaceQuotaManager2#removeWorkspaceQuota()
    */
   @Override
   @ManagedDescription("Removes workspace quota limit")
   public void removeWorkspaceQuota() throws QuotaManagerException
   {
      quotaPersister.removeWorkspaceQuota(rName, wsName);
   }

   /**
    * @see org.exoplatform.services.jcr.impl.quota.WorkspaceQuotaManager2#getWorkspaceQuota()
    */
   @Override
   @Managed
   @ManagedDescription("Returns workspace quota limit")
   public long getWorkspaceQuota() throws QuotaManagerException
   {
      return quotaPersister.getWorkspaceQuota(rName, wsName);
   }

   /**
    * @see org.exoplatform.services.jcr.impl.quota.WorkspaceQuotaManager2#getWorkspaceDataSize()
    */
   @Override
   @Managed
   @ManagedDescription("Returns a size of the Workspace")
   public long getWorkspaceDataSize() throws QuotaManagerException
   {
      return quotaPersister.getWorkspaceDataSize(rName, wsName);
   }

   /**
    * @see org.exoplatform.services.jcr.impl.quota.WorkspaceQuotaManager2#getWorkspaceIndexSize()
    */
   @Override
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

      repositoryQuotaManager.accumulateChanges(delta);
   }

   /**
    * @see org.exoplatform.services.jcr.impl.quota.WorkspaceQuotaManager2#getNodeDataSizeDirectly(java.lang.String)
    */
   @Override
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
    * @see org.exoplatform.services.jcr.impl.quota.WorkspaceQuotaManager2#getWorkspaceDataSizeDirectly()
    */
   @Override
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
      awaitTasksTermination();

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
         try
         {
            accumulateChanges(workspaceDataSizeChanges.get());
            accumulateNodesChanges(nodesDataSizeChanges.get(), unknownNodesDataSizeChanges.get());
         }
         finally
         {
            nodesDataSizeChanges.remove();
            unknownNodesDataSizeChanges.remove();

            workspaceDataSizeChanges.remove();
         }
      }

      /**
       * {@inheritDoc}
       */
      public void onRollback()
      {
         nodesDataSizeChanges.remove();
         unknownNodesDataSizeChanges.remove();

         workspaceDataSizeChanges.remove();
      }

      /**
       * @see WorkspaceQuotaManager#accumulateChanges(long)
       */
      private void accumulateNodesChanges(Map<String, Long> nodesDelta, Set<String> unknownChangedSize)
      {
         Set<String> trackedNodes = quotaPersister.getAllTrackedNodes(rName, wsName);
         for (String nodePath : unknownChangedSize)
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
            long wsDelta = 0;
            Map<String, Long> nodesDelta = new HashMap<String, Long>();
            Set<String> unknownChangedSize = new HashSet<String>();

            for (ItemState state : itemStates.getAllStates())
            {
               if (!state.getData().isNode())
               {
                  String itemPath = getPath(state.getData().getQPath().makeParentPath());

                  // update changed size for every node
                  Set<String> quotableParents = quotaPersister.getAllQuotableParentNodes(rName, wsName, itemPath);
                  for (String parent : quotableParents)
                  {
                     Long oldDelta = nodesDelta.get(parent);
                     Long newDelta = state.getChangedSize() + (oldDelta != null ? oldDelta : 0);

                     nodesDelta.put(parent, newDelta);
                  }

                  // update changed size for workspace
                  wsDelta += state.getChangedSize();
               }
               else
               {
                  if (state.isPathChanged())
                  {
                     String itemPath = getPath(state.getData().getQPath());
                     String oldPath = getPath(state.getOldPath());

                     unknownChangedSize.add(itemPath);
                     unknownChangedSize.add(oldPath);
                  }
               }
            }

            validateAccumulateChanges(wsDelta);
            validateAccumulateNodesChanges(nodesDelta);

            nodesDataSizeChanges.set(nodesDelta);
            unknownNodesDataSizeChanges.set(unknownChangedSize);

            workspaceDataSizeChanges.set(wsDelta);
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
       * @see BaseQuotaManager#validateAccumulateChanges(long)
       */
      private void validateAccumulateChanges(long delta) throws ExceededQuotaLimitException
      {
         repositoryQuotaManager.validateAccumulateChanges(delta);

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
       * @see BaseQuotaManager#validateAccumulateChanges(long)
       */
      private void validateAccumulateNodesChanges(Map<String, Long> nodesDelta) throws ExceededQuotaLimitException
      {
         for (Entry<String, Long> entry : nodesDelta.entrySet())
         {
            String nodePath = entry.getKey();
            Long delta = entry.getValue();

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
}
