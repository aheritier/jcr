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
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.picocontainer.Startable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

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
    * Executor service.
    */
   protected final Executor executor;

   /**
    * {@link QuotaPersister}
    */
   protected final QuotaPersister quotaPersister;

   /**
    * Indicates if repository data size exceeded quota limit.
    */
   protected AtomicBoolean alerted = new AtomicBoolean();

   /**
    * Indicates that repository is tracked.
    */
   protected AtomicBoolean tracked = new AtomicBoolean();

   /**
    * Indicates that repository is quoted.
    */
   protected AtomicBoolean quoted = new AtomicBoolean();

   /**
    * RepositoryQuotaManagerImpl constructor.
    */
   public RepositoryQuotaManager(BaseQuotaManager quotaManager, RepositoryEntry rEntry)
   {
      this.rName = rEntry.getName();
      this.globalQuotaManager = quotaManager;
      this.executor = getExecutorSevice();
      this.quotaPersister = getQuotaPersister();

      try
      {
         long quotaLimit = quotaPersister.getRepositoryQuota(rName);
         quoted.set(true);
         tracked.set(true);

         try
         {
            long dataSize = quotaPersister.getRepositoryDataSize(rName);
            alerted.set(dataSize > quotaLimit);
         }
         catch (UnknownQuotaDataSizeException e)
         {
            // Maybe there is not information about data size yet
         }
      }
      catch (UnknownQuotaLimitException e)
      {
         // There is no quota, there is not reason to track, except if global tracking exists
         tracked.set(globalQuotaManager.isTracked());
      }
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
      return wqm.getWorkspaceQuota(workspaceName);
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
      quoted.set(true);
      quotaPersister.setRepositoryQuota(rName, quotaLimit);

      trackRepository();
   }

   /**
    * @see QuotaManager#removeRepositoryQuota(String)
    */
   @Managed
   @ManagedDescription("Removes repository quta limit")
   public void removeRepositoryQuota() throws QuotaManagerException
   {
      alerted.set(false);
      quoted.set(false);

      quotaPersister.removeRepositoryQuota(rName);

      if (!globalQuotaManager.isTracked())
      {
         untrackRepository();
      }
   }

   /**
    * Adds repository tracking.
    *
    * @throws QuotaManagerException
    *          if any exception is occurred
    */
   protected void trackRepository() throws QuotaManagerException
   {
      tracked.set(true);

      for (WorkspaceQuotaManager wQuotaManager : wsQuotaManagers.values())
      {
         wQuotaManager.trackWorkspace();
      }
   }

   /**
    * Removes repository tracking.
    *
    * @throws QuotaManagerException
    *          if any exception is occurred
    */
   protected void untrackRepository() throws QuotaManagerException
   {
      tracked.set(false);
      quotaPersister.removeRepositoryDataSize(rName);

      for (WorkspaceQuotaManager workspaceQuotaManager : wsQuotaManagers.values())
      {
         if (!workspaceQuotaManager.isTracked())
         {
            workspaceQuotaManager.untrackWorkspace();
         }
      }
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
      try
      {
         return quotaPersister.getRepositoryDataSize(rName);
      }
      catch (UnknownQuotaDataSizeException e)
      {
         return getRepositoryDataSizeDirectly();
      }
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
   }

   /**
    * Accumulate global data size changes.
    * 
    * @param delta
    *          the size on which repository was changed
    */
   protected void onAccumulateChanges(long delta)
   {
      if (tracked.get())
      {
         long dataSize = 0;
         try
         {
            dataSize = quotaPersister.getRepositoryDataSize(rName);
         }
         catch (UnknownQuotaDataSizeException e)
         {
            // it's ok, maybe there is no data size yet
         }

         quotaPersister.setRepositoryDataSize(rName, Math.max(dataSize + delta, 0));
      }

      globalQuotaManager.onAccumulateChanges(delta);
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
    * Returns {@link Executor} instance.
    */
   protected Executor getExecutorSevice()
   {
      return globalQuotaManager.getExecutorSevice();
   }

   /**
    * Returns {@link QuotaPersister} instance.
    */
   protected QuotaPersister getQuotaPersister()
   {
      return globalQuotaManager.getQuotaPersister();
   }

   /**
    * Returns {@link #tracked}.
    */
   protected boolean isTracked()
   {
      return tracked.get();
   }

   /**
    * Returns repository data size by summing size of all workspaces.
    */
   private long getRepositoryDataSizeDirectly() throws QuotaManagerException
   {
      long size = 0;
      for (WorkspaceQuotaManager wQuotaManager : wsQuotaManagers.values())
      {
         size += wQuotaManager.getWorkspaceDataSize();
      }

      return size;
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