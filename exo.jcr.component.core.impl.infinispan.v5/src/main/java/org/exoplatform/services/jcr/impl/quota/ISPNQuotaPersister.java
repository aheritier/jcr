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

import org.exoplatform.services.jcr.impl.backup.BackupException;

import java.util.Set;

/**
 * @author <a href="abazko@exoplatform.com">Anatoliy Bazko</a>
 * @version $Id: ISPNQuotaPersister.java 34360 2009-07-22 23:58:59Z tolusha $
 */
public class ISPNQuotaPersister extends AbstractQuotaPersister
{

   /**
    * {@inheritDoc}
    */
   public void destroy()
   {
      // TODO Auto-generated method stub

   }

   /**
    * {@inheritDoc}
    */
   public void setNodeQuota(String repositoryName, String workspaceName, String nodePath, long quotaLimit,
      boolean asyncUpdate)
   {
      // TODO Auto-generated method stub

   }

   /**
    * {@inheritDoc}
    */
   public void setGroupOfNodesQuota(String repositoryName, String workspaceName, String patternPath, long quotaLimit,
      boolean asyncUpdate)
   {
      // TODO Auto-generated method stub

   }

   /**
    * {@inheritDoc}
    */
   public long getNodeDataSize(String repositoryName, String workspaceName, String nodePath)
      throws UnknownQuotaDataSizeException
   {
      // TODO Auto-generated method stub
      return 0;
   }

   /**
    * {@inheritDoc}
    */
   public void setNodeDataSize(String repositoryName, String workspaceName, String nodePath, long dataSize)
   {
      // TODO Auto-generated method stub

   }

   /**
    * {@inheritDoc}
    */
   public void removeNodeDataSize(String repositoryName, String workspaceName, String nodePath)
   {
      // TODO Auto-generated method stub

   }

   /**
    * {@inheritDoc}
    */
   public long getWorkspaceQuota(String repositoryName, String workspaceName) throws UnknownQuotaLimitException
   {
      // TODO Auto-generated method stub
      return 0;
   }

   /**
    * {@inheritDoc}
    */
   public void setWorkspaceQuota(String repositoryName, String workspaceName, long quotaLimit)
   {
      // TODO Auto-generated method stub

   }

   /**
    * {@inheritDoc}
    */
   public void removeWorkspaceQuota(String repositoryName, String workspaceName)
   {
      // TODO Auto-generated method stub

   }

   /**
    * {@inheritDoc}
    */
   public void setWorkspaceDataSize(String repositoryName, String workspaceName, long dataSize)
   {
      // TODO Auto-generated method stub

   }

   /**
    * {@inheritDoc}
    */
   public long getWorkspaceDataSize(String repositoryName, String workspaceName) throws UnknownQuotaDataSizeException
   {
      // TODO Auto-generated method stub
      return 0;
   }

   /**
    * {@inheritDoc}
    */
   public long getRepositoryQuota(String repositoryName) throws UnknownQuotaLimitException
   {
      // TODO Auto-generated method stub
      return 0;
   }

   /**
    * {@inheritDoc}
    */
   public void setRepositoryQuota(String repositoryName, long quotaLimit)
   {
      // TODO Auto-generated method stub

   }

   /**
    * {@inheritDoc}
    */
   public void removeRepositoryQuota(String repositoryName)
   {
      // TODO Auto-generated method stub

   }

   /**
    * {@inheritDoc}
    */
   public long getRepositoryDataSize(String repositoryName) throws UnknownQuotaDataSizeException
   {
      // TODO Auto-generated method stub
      return 0;
   }

   /**
    * {@inheritDoc}
    */
   public void setRepositoryDataSize(String repositoryName, long dataSize)
   {
      // TODO Auto-generated method stub

   }

   /**
    * {@inheritDoc}
    */
   public long getGlobalDataSize() throws UnknownQuotaDataSizeException
   {
      // TODO Auto-generated method stub
      return 0;
   }

   /**
    * {@inheritDoc}
    */
   public void setGlobalDataSize(long dataSize)
   {
      // TODO Auto-generated method stub

   }

   /**
    * {@inheritDoc}
    */
   public long getGlobalQuota() throws UnknownQuotaLimitException
   {
      // TODO Auto-generated method stub
      return 0;
   }

   /**
    * {@inheritDoc}
    */
   public void setGlobalQuota(long quotaLimit)
   {
      // TODO Auto-generated method stub

   }

   /**
    * {@inheritDoc}
    */
   public void removeGlobalQuota()
   {
      // TODO Auto-generated method stub

   }

   /**
    * {@inheritDoc}
    */
   public void clearWorkspaceData(String repositoryName, String workspaceName) throws BackupException
   {
      // TODO Auto-generated method stub

   }

   /**
    * {@inheritDoc}
    */
   public long getNodeQuota(String repositoryName, String workspaceName, String nodePath)
      throws UnknownQuotaLimitException
   {
      // TODO Auto-generated method stub
      return 0;
   }

   /**
    * {@inheritDoc}
    */
   public long getGroupOfNodesQuota(String repositoryName, String workspaceName, String patternPath)
      throws UnknownQuotaLimitException
   {
      // TODO Auto-generated method stub
      return 0;
   }

   /**
    * {@inheritDoc}
    */
   public boolean isNodeQuotaAsync(String repositoryName, String workspaceName, String nodePath)
      throws UnknownQuotaLimitException
   {
      // TODO Auto-generated method stub
      return false;
   }

   /**
    * {@inheritDoc}
    */
   public boolean isGroupOfNodesQuotaAsync(String repositoryName, String workspaceName, String patternPath)
      throws UnknownQuotaLimitException
   {
      // TODO Auto-generated method stub
      return false;
   }

   /**
    * {@inheritDoc}
    */
   public Set<String> getAllNodeQuota(String repositoryName, String workspaceName)
   {
      // TODO Auto-generated method stub
      return null;
   }

   /**
    * {@inheritDoc}
    */
   public Set<String> getAllGroupOfNodesQuota(String repositoryName, String workspaceName)
   {
      // TODO Auto-generated method stub
      return null;
   }

   /**
    * {@inheritDoc}
    */
   public Set<String> getAllTrackedNodes(String repositoryName, String workspaceName)
   {
      // TODO Auto-generated method stub
      return null;
   }

   /**
    * {@inheritDoc}
    */
   protected void removeInternallyNodeQuota(String repositoryName, String workspaceName, String nodePath)
   {
      // TODO Auto-generated method stub

   }

   /**
    * {@inheritDoc}
    */
   protected void removeInternallyGroupOfNodesQuota(String repositoryName, String workspaceName, String patternPath)
   {
      // TODO Auto-generated method stub

   }

}
