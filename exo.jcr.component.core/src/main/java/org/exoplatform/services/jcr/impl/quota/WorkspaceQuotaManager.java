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
import org.exoplatform.services.jcr.datamodel.ItemType;
import org.exoplatform.services.jcr.datamodel.NodeData;
import org.exoplatform.services.jcr.datamodel.PropertyData;
import org.exoplatform.services.jcr.datamodel.QPathEntry;
import org.exoplatform.services.jcr.impl.Constants;
import org.exoplatform.services.jcr.impl.core.JCRPath;
import org.exoplatform.services.jcr.impl.core.LocationFactory;
import org.exoplatform.services.jcr.impl.core.RepositoryImpl;
import org.exoplatform.services.jcr.impl.core.query.SearchManager;
import org.exoplatform.services.jcr.impl.dataflow.persistent.WorkspacePersistentDataManager;
import org.exoplatform.services.jcr.impl.util.io.DirectoryHelper;
import org.picocontainer.Startable;

import java.io.File;
import java.lang.reflect.Method;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.List;
import java.util.concurrent.Executor;

import javax.jcr.RepositoryException;

/**
 * @author <a href="abazko@exoplatform.com">Anatoliy Bazko</a>
 * @version $Id: WorkspaceQuotaManager.java 34360 2009-07-22 23:58:59Z tolusha $
 */
@Managed
@NameTemplate(@Property(key = "service", value = "WorkspaceQuotaManager"))
public class WorkspaceQuotaManager implements Startable
{

   /**
    * Workspace name.
    */
   protected final String wsName;

   /**
    * Repository name.
    */
   protected final String rName;

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
   protected final RepositoryQuotaManager rQuotaManager;

   /**
    * Executor service.
    */
   protected final Executor executor;

   /**
    * {@link QuotaPersister}
    */
   protected final QuotaPersister quotaPersister;

   /**
    * Indicates if workspace data size exceeded quota limit.
    */
   protected boolean alerted;

   /**
    * WorkspaceQuotaManager constructor.
    */
   public WorkspaceQuotaManager(RepositoryImpl repository, RepositoryQuotaManager rQuotaManager,
      RepositoryEntry rEntry, WorkspaceEntry wsEntry, WorkspacePersistentDataManager dataManager)
   {
      this.rName = rEntry.getName();
      this.wsName = wsEntry.getName();
      this.wsContainer = repository.getWorkspaceContainer(wsName);
      this.dataManager = dataManager;
      this.lFactory = repository.getLocationFactory();
      this.rQuotaManager = rQuotaManager;
      this.executor = rQuotaManager.getExecutorSevice();
      this.quotaPersister = rQuotaManager.getQuotaPersister();
   }

   /**
    * @see QuotaManager#getNodeDataSize(String, String, String)
    */
   @Managed
   @ManagedDescription("Returns a node data size")
   public long getNodeDataSize(@ManagedDescription("The absolute path to node") @ManagedName("nodePath") String nodePath)
      throws QuotaManagerException
   {
      try
      {
         return quotaPersister.getNodeDataSize(rName, wsName, nodePath);
      }
      catch (UnknownQuotaUsedException e)
      {
         return getNodeDataSizeDirectly(nodePath);
      }
   }

   /**
    * @see QuotaManager#getNodeQuota(String, String, String)
    */
   @Managed
   @ManagedDescription("Returns a node quota limit")
   public long getNodeQuota(String nodePath) throws QuotaManagerException
   {
      return quotaPersister.getNodeQuota(rName, wsName, nodePath);
   }

   /**
    * @see QuotaManager#setNodeQuota(String, String, String, long, boolean)
    */
   @Managed
   @ManagedDescription("Sets a node quota limit")
   public void setNodeQuota(String nodePath, long quotaLimit, boolean asyncUpdate) throws QuotaManagerException
   {
      quotaPersister.setNodeQuota(rName, wsName, nodePath, quotaLimit, asyncUpdate);

      TrackNodeTask task = new TrackNodeTask(this, nodePath, quotaLimit, asyncUpdate);
      executor.execute(task);
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
   }

   /**
    * @see QuotaManager#removeGroupOfNodesQuota(String, String, String)
    */
   @Managed
   @ManagedDescription("Removes a quota limit for a bunch of nodes")
   public void removeGroupOfNodesQuota(String nodePath) throws QuotaManagerException
   {
      quotaPersister.removeGroupOfNodesQuota(rName, wsName, nodePath);
   }

   /**
    * @see QuotaManager#setWorkspaceQuota(String, String, long)
    */
   @ManagedDescription("Sets workspace quota limit")
   public void setWorkspaceQuota(long quotaLimit) throws QuotaManagerException
   {
      quotaPersister.setWorkspaceQuota(rName, wsName, quotaLimit);

      TrackWorkspaceTask quotaTask = new TrackWorkspaceTask(this, quotaLimit);
      executor.execute(quotaTask);
   }

   /**
    * @see QuotaManager#removeWorkspaceQuota(String, String, long)
    */
   @ManagedDescription("Removes workspace quota limit")
   public void removeWorkspaceQuota() throws QuotaManagerException
   {
      alerted = false;
      quotaPersister.removeWorkspaceQuota(rName, wsName);
   }

   /**
    * @see QuotaManager#getWorkspaceQuota(String, String)
    */
   @Managed
   @ManagedDescription("Returns workspace quota limit")
   public long getWorkspaceQuota(String workspaceName) throws QuotaManagerException
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
      try
      {
         return quotaPersister.getWorkspaceDataSize(rName, wsName);
      }
      catch (UnknownQuotaUsedException e)
      {
         return getWorkspaceDataSizeDirectly();
      }
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
    * Tracks workspace by calculating the size if a stored content,
    *
    * @param quotaLimit
    *          the quota limit         
    * @throws QuotaManagerException
    *          if any exception is occurred
    */
   protected void trackWorkspace(long quotaLimit) throws QuotaManagerException
   {
      long dataSize;

      try
      {
         dataSize = quotaPersister.getWorkspaceDataSize(rName, wsName);
      }
      catch (UnknownQuotaUsedException e)
      {
         dataSize = getWorkspaceDataSizeDirectly();
         quotaPersister.setWorkspaceDataSize(rName, wsName, dataSize);
      }

      alerted = dataSize > quotaLimit; 
   }

   /**
    * Tracks Node by calculating the size if a stored content,
    */
   protected void trackNode(String nodePath, long quotaLimit, boolean asyncUpdate) throws QuotaManagerException
   {
      long dataSize;

      try
      {
         dataSize = quotaPersister.getNodeDataSize(rName, wsName, nodePath);
      }
      catch (UnknownQuotaUsedException e)
      {
         dataSize = getNodeDataSizeDirectly(nodePath);
         quotaPersister.setNodeDataSize(rName, wsName, nodePath, dataSize);
      }

   }

   /**
    * Calculates node data size by asking directly respective {@link WorkspacePersistentDataManager}.  
    */
   private long getNodeDataSizeDirectly(String nodePath) throws QuotaManagerException
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
   private long getWorkspaceDataSizeDirectly() throws QuotaManagerException
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
       * CalculateNodeDataSizeVisitory constructor.
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
      rQuotaManager.registerWorkspaceQuotaManager(wsName, this);
   }

   /**
    * {@inheritDoc}
    */
   public void stop()
   {
      rQuotaManager.unregisterWorkspaceQuotaManager(wsName);
   }
}
