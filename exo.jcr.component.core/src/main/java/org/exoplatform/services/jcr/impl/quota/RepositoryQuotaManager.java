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

import org.exoplatform.management.annotations.Managed;
import org.exoplatform.management.annotations.ManagedDescription;
import org.exoplatform.management.jmx.annotations.NameTemplate;
import org.exoplatform.management.jmx.annotations.Property;
import org.exoplatform.services.jcr.config.RepositoryEntry;
import org.exoplatform.services.jcr.impl.proccess.WorkerThread;
import org.exoplatform.services.jcr.impl.quota.WorkspaceQuotaManager.ChangesItem;
import org.exoplatform.services.jcr.impl.quota.WorkspaceQuotaManager.ChangesLog;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.rpc.RPCService;
import org.exoplatform.services.rpc.RemoteCommand;
import org.picocontainer.Startable;

import java.io.Serializable;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author <a href="abazko@exoplatform.com">Anatoliy Bazko</a>
 * @version $Id: RepositoryQuotaManager.java 34360 2009-07-22 23:58:59Z tolusha $
 */
@Managed
@NameTemplate(@Property(key = "service", value = "RepositoryQuotaManager"))
public class RepositoryQuotaManager implements Startable
{

   /**
    * Logger.
    */
   protected final Log LOG = ExoLogger.getLogger("exo.jcr.component.core.RepositoryQuotaManager");

   /**
    * All {@link WorkspaceQuotaManager} belonging to repository.
    */
   private Map<String, WorkspaceQuotaManager> wsQuotaManagers = new ConcurrentHashMap<String, WorkspaceQuotaManager>();

   /**
    * The repository name.
    */
   protected final String rName;

   /**
    * The quota manager.
    */
   protected final BaseQuotaManager globalQuotaManager;

   /**
    * {@link RPCService}
    */
   protected final RPCService rpcService;

   /**
    * {@link QuotaPersister}
    */
   protected final QuotaPersister quotaPersister;

   /**
    * Task is obligated to push all changes to coordinator where they will
    * be accumulated.
    */
   protected final PushTask pushTask;

   /**
    * Timeout for task.
    */
   protected final long DEFAULT_TIMEOUT = 5000; // 5 sec

   /**
    * Remote command is obligated to push changes log to coordinator.
    */
   protected RemoteCommand pushChangesLogTask;

   /**
    * RepositoryQuotaManagerImpl constructor.
    */
   public RepositoryQuotaManager(BaseQuotaManager quotaManager, RepositoryEntry rEntry)
   {
      this.rName = rEntry.getName();
      this.globalQuotaManager = quotaManager;
      this.quotaPersister = globalQuotaManager.quotaPersister;
      this.rpcService = globalQuotaManager.rpcService;

      this.pushTask = new PushTask("PushQuotaChangesTask-" + rName, DEFAULT_TIMEOUT);
      this.pushTask.start();
      
      initRemoteCommands();
   }

   /**
    * @see QuotaManager#getNodeDataSize(String, String, String)
    */
   public long getNodeDataSize(String workspaceName, String nodePath) throws QuotaManagerException
   {
      WorkspaceQuotaManager wqm = getWorkspaceQuotaManager(workspaceName);
      return wqm.getNodeDataSize(nodePath);
   }

   /**
    * @see QuotaManager#getNodeQuota(String, String, String)
    */
   public long getNodeQuota(String workspaceName, String nodePath) throws QuotaManagerException
   {
      WorkspaceQuotaManager wqm = getWorkspaceQuotaManager(workspaceName);
      return wqm.getNodeQuota(nodePath);
   }

   /**
    * @see QuotaManager#setNodeQuota(String, String, String, long, boolean)
    */
   public void setNodeQuota(String workspaceName, String nodePath, long quotaLimit, boolean asyncUpdate)
      throws QuotaManagerException
   {
      WorkspaceQuotaManager wqm = getWorkspaceQuotaManager(workspaceName);
      wqm.setNodeQuota(nodePath, quotaLimit, asyncUpdate);
   }

   /**
    * @see QuotaManager#setGroupOfNodesQuota(String, String, String, long, boolean)
    */
   public void setGroupOfNodesQuota(String workspaceName, String patternPath, long quotaLimit,
      boolean asyncUpdate) throws QuotaManagerException
   {
      WorkspaceQuotaManager wqm = getWorkspaceQuotaManager(workspaceName);
      wqm.setGroupOfNodesQuota(patternPath, quotaLimit, asyncUpdate);
   }

   /**
    * @see QuotaManager#removeNodeQuota(String, String, String)
    */
   public void removeNodeQuota(String workspaceName, String nodePath) throws QuotaManagerException
   {
      WorkspaceQuotaManager wqm = getWorkspaceQuotaManager(workspaceName);
      wqm.removeNodeQuota(nodePath);
   }

   /**
    * @see QuotaManager#removeGroupOfNodesQuota(String, String, String)
    */
   public void removeGroupOfNodesQuota(String workspaceName, String nodePath) throws QuotaManagerException
   {
      WorkspaceQuotaManager wqm = getWorkspaceQuotaManager(workspaceName);
      wqm.removeGroupOfNodesQuota(nodePath);
   }

   /**
    * @see QuotaManager#getWorkspaceQuota(String, String)
    */
   public long getWorkspaceQuota(String workspaceName) throws QuotaManagerException
   {
      WorkspaceQuotaManager wqm = getWorkspaceQuotaManager(workspaceName);
      return wqm.getWorkspaceQuota();
   }

   /**
    * @see QuotaManager#setWorkspaceQuota(String, String, long)
    */
   public void setWorkspaceQuota(String workspaceName, long quotaLimti) throws QuotaManagerException
   {
      WorkspaceQuotaManager wqm = getWorkspaceQuotaManager(workspaceName);
      wqm.setWorkspaceQuota(quotaLimti);
   }

   /**
    * @see QuotaManager#removeWorkspaceQuota(String, String, long)
    */
   public void removeWorkspaceQuota(String workspaceName) throws QuotaManagerException
   {
      WorkspaceQuotaManager wqm = getWorkspaceQuotaManager(workspaceName);
      wqm.removeWorkspaceQuota();
   }

   /**
    * @see QuotaManager#getWorkspaceDataSize(String, String)
    */
   public long getWorkspaceDataSize(String workspaceName) throws QuotaManagerException
   {
      WorkspaceQuotaManager wqm = getWorkspaceQuotaManager(workspaceName);
      return wqm.getWorkspaceDataSize();
   }

   /**
    * @see QuotaManager#getWorkspaceIndexSize(String, String)
    */
   public long getWorkspaceIndexSize(String workspaceName) throws QuotaManagerException
   {
      WorkspaceQuotaManager wqm = getWorkspaceQuotaManager(workspaceName);
      return wqm.getWorkspaceIndexSize();
   }

   /**
    * @see QuotaManager#setRepositoryQuota(String, long)
    */
   @Managed
   @ManagedDescription("Sets repository quta limit")
   public void setRepositoryQuota(long quotaLimit) throws QuotaManagerException
   {
      quotaPersister.setRepositoryQuota(rName, quotaLimit);
   }

   /**
    * @see QuotaManager#removeRepositoryQuota(String)
    */
   @Managed
   @ManagedDescription("Removes repository quta limit")
   public void removeRepositoryQuota() throws QuotaManagerException
   {
      quotaPersister.removeRepositoryQuota(rName);
   }

   /**
    * @see QuotaManager#getRepositoryQuota(String)
    */
   @Managed
   @ManagedDescription("Returns repository quta limit")
   public long getRepositoryQuota() throws QuotaManagerException
   {
      return quotaPersister.getRepositoryQuota(rName);
   }

   /**
    * @see QuotaManager#getRepositoryDataSize(String)
    */
   @Managed
   @ManagedDescription("Returns repository data size")
   public long getRepositoryDataSize() throws QuotaManagerException
   {
      return quotaPersister.getRepositoryDataSize(rName);
   }

   /**
    * @see QuotaManager#getRepositoryIndexSize(String)
    */
   @Managed
   @ManagedDescription("Returns repository index size")
   public long getRepositoryIndexSize() throws QuotaManagerException
   {
      long size = 0;
      for (WorkspaceQuotaManager wQuotaManager : wsQuotaManagers.values())
      {
         size += wQuotaManager.getWorkspaceIndexSize();
      }

      return size;
   }

   /**
    * {@inheritDoc}
    */
   public void start()
   {
      globalQuotaManager.registerRepositoryQuotaManager(rName, this);
   }

   /**
    * {@inheritDoc}
    */
   public void stop()
   {
      wsQuotaManagers.clear();
      globalQuotaManager.unregisterRepositoryQuotaManager(rName);

      pushTask.halt();
      rpcService.unregisterCommand(pushChangesLogTask);
   }

   /**
    * @see BaseQuotaManager#accumulatePersistedChanges(long)
    */
   protected void accumulatePersistedChanges(long delta)
   {
      long dataSize = 0;
      try
      {
         dataSize = quotaPersister.getRepositoryDataSize(rName);
      }
      catch (UnknownQuotaDataSizeException e)
      {
         if (LOG.isTraceEnabled())
         {
            LOG.trace(e.getMessage(), e);
         }
      }

      long newDataSize = Math.max(dataSize + delta, 0);
      quotaPersister.setRepositoryDataSize(rName, newDataSize);

      globalQuotaManager.accumulatePersistedChanges(delta);
   }

   /**
    * @see BaseQuotaManager#validatePendingChanges(long)
    */
   protected void validatePendingChanges(long delta) throws ExceededQuotaLimitException
   {
      globalQuotaManager.validatePendingChanges(delta);

      try
      {
         long quotaLimit = quotaPersister.getRepositoryQuota(rName);

         try
         {
            long dataSize = quotaPersister.getRepositoryDataSize(rName);

            if (dataSize + delta > quotaLimit)
            {
               globalQuotaManager.behaveOnQuotaExceeded("Repository " + rName + " data size exceeded quota limit");
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
    * Registers {@link WorkspaceQuotaManager} by name. To delegate workspace based operation
    * to appropriate level. 
    */
   public void registerWorkspaceQuotaManager(String workspaceName, WorkspaceQuotaManager wQuotaManager)
   {
      wsQuotaManagers.put(workspaceName, wQuotaManager);
   }

   /**
    * Unregisters {@link WorkspaceQuotaManager} by name. 
    */
   public void unregisterWorkspaceQuotaManager(String workspaceName)
   {
      wsQuotaManagers.remove(workspaceName);
   }

   /**
    * Returns repository data size by summing size of all workspaces.
    */
   public long getRepositoryDataSizeDirectly() throws QuotaManagerException
   {
      long size = 0;
      for (WorkspaceQuotaManager wQuotaManager : wsQuotaManagers.values())
      {
         size += wQuotaManager.getWorkspaceDataSizeDirectly();
      }

      return size;
   }

   // ============================================> pushing changes to coordinator

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
            return "RepositoryQuotaManager-" + rName + "-pushChangesLogTask";
         }

         /**
          * {@inheritDoc}
          */
         public Serializable execute(Serializable[] args) throws Throwable
         {
            String workspaceName = (String)args[0];
            ChangesItem changesItem = (ChangesItem)args[1];
            
            WorkspaceQuotaManager wqm = getWorkspaceQuotaManager(workspaceName);
            wqm.accumulatePersistedChanges(changesItem.workspaceDelta);
            wqm.accumulatePersistedNodesChanges(changesItem.calculatedNodesDelta, changesItem.unknownNodesDelta);

            return null;
         }
      };

      rpcService.registerCommand(pushChangesLogTask);
   }

   /**
    * Push changes to coordinator.
    */
   private class PushTask extends WorkerThread
   {

      /**
       * PushTask constructor.
       */
      public PushTask(String name, long timeout)
      {
         super(name, timeout);
      }

      /**
       * {@inheritDoc}
       */
      protected void callPeriodically() throws Exception
      {
         for (Entry<String, WorkspaceQuotaManager> entry : wsQuotaManagers.entrySet())
         {
            String workspaceName = entry.getKey();
            WorkspaceQuotaManager wqm = entry.getValue();

            ChangesLog changesLog = wqm.getChangesLog();
            ChangesItem totalChanges = wqm.new ChangesItem();

            // lets merge all changes together 
            for (ChangesItem particularChanges = changesLog.poll(); particularChanges != null;)
            {
               totalChanges.merge(particularChanges);
            }

            rpcService.executeCommandOnCoordinator(pushChangesLogTask, false, workspaceName, totalChanges);
         }
      }
   }

   private WorkspaceQuotaManager getWorkspaceQuotaManager(String workspaceName) throws QuotaManagerException
   {
      WorkspaceQuotaManager wqm = wsQuotaManagers.get(workspaceName);
      if (wqm == null)
      {
         throw new QuotaManagerException("Workspace " + workspaceName + " is not registered in " + rName);
      }

      return wqm;
   }
}
