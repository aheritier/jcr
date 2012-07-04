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

/**
 * @author <a href="abazko@exoplatform.com">Anatoliy Bazko</a>
 * @version $Id: QuotaPersister.java 34360 2009-07-22 23:58:59Z tolusha $
 */
public interface QuotaPersister
{
   /**
    * Release all resources.
    */
   void destroy();

   /**
    * @see QuotaManager#getNodeQuota(String, String, String)
    */
   long getNodeQuota(String repositoryName, String workspaceName, String nodePath) throws QuotaManagerException;

   /**
    * @see QuotaManager#getNodeDataSize(String, String, String)
    */
   long getNodeDataSize(String repositoryName, String workspaceName, String nodePath)
      throws QuotaManagerException;

   /**
    * @see QuotaManager#getWorkspaceQuota(String, String)
    */
   long getWorkspaceQuota(String repositoryName, String workspaceName) throws QuotaManagerException;

   /**
    * @see QuotaManager#setWorkspaceQuota(String, String, long)
    */
   void setWorkspaceQuota(String repositoryName, String workspaceName, long quotaLimit)
      throws QuotaManagerException;

   /**
    * Persists workspace data size.
    */
   void setWorkspaceDataSize(String repositoryName, String workspaceName, long dataSize) throws QuotaManagerException;

   /**
    * @see QuotaManager#getWorkspaceDataSize(String, String)
    */
   long getWorkspaceDataSize(String repositoryName, String workspaceName) throws QuotaManagerException;

   /**
    * @see QuotaManager#getRepositoryQuota(String)
    */
   long getRepositoryQuota(String repositoryName) throws QuotaManagerException;

   /**
    * @see QuotaManager#setRepositoryQuota(String, long)
    */
   void setRepositoryQuota(String repositoryName, long quotaLimit) throws QuotaManagerException;
   
   /**
    * @see QuotaManager#getRepositoryDataSize(String)
    */
   long getRepositoryDataSize(String repositoryName) throws QuotaManagerException;
   
   /**
    * Persists repository data size.
    */
   void setRepositoryDataSize(String repositoryName, long dataSize) throws QuotaManagerException;

   /**
    * @see QuotaManager#getGlobalDataSize()
    */
   long getGlobalDataSize() throws QuotaManagerException;

   /**
    * Persists global data size.
    */
   void setGlobalDataSize(long dataSize) throws QuotaManagerException;

   /**
    * @see QuotaManager#getGlobalQuota()
    */
   long getGlobalQuota() throws QuotaManagerException;

   /**
    * @see QuotaManager#setGlobalQuota(long)
    */
   void setGlobalQuota(long quotaLimit) throws QuotaManagerException;

}
