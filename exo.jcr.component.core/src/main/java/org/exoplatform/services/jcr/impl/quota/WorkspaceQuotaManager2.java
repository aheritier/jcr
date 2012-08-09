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
import org.exoplatform.management.annotations.ManagedName;
import org.exoplatform.services.jcr.impl.dataflow.persistent.WorkspacePersistentDataManager;

/**
 * @author <a href="abazko@exoplatform.com">Anatoliy Bazko</a>
 * @version $Id: WorkspaceQuotaManager2.java 34360 2009-07-22 23:58:59Z tolusha $
 */
public interface WorkspaceQuotaManager2
{

   /**
    * @see QuotaManager#getNodeDataSize(String, String, String)
    */
   @Managed
   @ManagedDescription("Returns a node data size")
   public abstract long getNodeDataSize(
      @ManagedDescription("The absolute path to node") @ManagedName("nodePath") String nodePath)
      throws QuotaManagerException;

   /**
    * @see QuotaManager#getNodeQuota(String, String, String)
    */
   @Managed
   @ManagedDescription("Returns a node quota limit")
   public abstract long getNodeQuota(String nodePath) throws QuotaManagerException;

   /**
    * @see QuotaManager#setNodeQuota(String, String, String, long, boolean)
    */
   @Managed
   @ManagedDescription("Sets a node quota limit")
   public abstract void setNodeQuota(String nodePath, long quotaLimit, boolean asyncUpdate)
      throws QuotaManagerException;

   /**
    * @see QuotaManager#setGroupOfNodesQuota(String, String, String, long, boolean)
    */
   @Managed
   @ManagedDescription("Sets a quota limit for a bunch of nodes")
   public abstract void setGroupOfNodesQuota(String patternPath, long quotaLimit, boolean asyncUpdate)
      throws QuotaManagerException;

   /**
    * @see QuotaManager#removeNodeQuota(String, String, String)
    */
   @Managed
   @ManagedDescription("Removes a quota limit for a node")
   public abstract void removeNodeQuota(String nodePath) throws QuotaManagerException;

   /**
    * @see QuotaManager#removeGroupOfNodesQuota(String, String, String)
    */
   @Managed
   @ManagedDescription("Removes a quota limit for a bunch of nodes")
   public abstract void removeGroupOfNodesQuota(String patternPath) throws QuotaManagerException;

   /**
    * @see QuotaManager#setWorkspaceQuota(String, String, long)
    */
   @ManagedDescription("Sets workspace quota limit")
   public abstract void setWorkspaceQuota(long quotaLimit) throws QuotaManagerException;

   /**
    * @see QuotaManager#removeWorkspaceQuota(String, String, long)
    */
   @ManagedDescription("Removes workspace quota limit")
   public abstract void removeWorkspaceQuota() throws QuotaManagerException;

   /**
    * @see QuotaManager#getWorkspaceQuota(String, String)
    */
   @Managed
   @ManagedDescription("Returns workspace quota limit")
   public abstract long getWorkspaceQuota() throws QuotaManagerException;

   /**
    * @see QuotaManager#getWorkspaceDataSize(String, String)
    */
   @Managed
   @ManagedDescription("Returns a size of the Workspace")
   public abstract long getWorkspaceDataSize() throws QuotaManagerException;

   /**
    * @see QuotaManager#getWorkspaceIndexSize(String, String)
    */
   @Managed
   @ManagedDescription("Returns a size of the index")
   public abstract long getWorkspaceIndexSize() throws QuotaManagerException;

   /**
    * Calculates node data size by asking directly respective {@link WorkspacePersistentDataManager}.  
    */
   public abstract long getNodeDataSizeDirectly(String nodePath) throws QuotaManagerException;

   /**
    * Calculate workspace data size by asking directly respective {@link WorkspacePersistentDataManager}.  
    */
   public abstract long getWorkspaceDataSizeDirectly() throws QuotaManagerException;

}