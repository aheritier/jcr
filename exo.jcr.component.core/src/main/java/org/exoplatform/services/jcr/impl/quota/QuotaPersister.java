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
import org.exoplatform.services.jcr.impl.dataflow.serialization.ZipObjectReader;
import org.exoplatform.services.jcr.impl.dataflow.serialization.ZipObjectWriter;

import java.util.Set;

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
   long getNodeQuotaByPathOrPattern(String repositoryName, String workspaceName, String nodePath) throws UnknownQuotaLimitException;

   /**
    * @see QuotaManager#setNodeQuota(String, String, String, long, boolean)
    */
   void setNodeQuota(String repositoryName, String workspaceName, String nodePath, long quotaLimit, boolean asyncUpdate);

   /**
    * @see QuotaManager#removeNodeQuota(String, String, String)
    */
   void removeNodeQuota(String repositoryName, String workspaceName, String nodePath);

   /**
    * @see QuotaManager#removeGroupOfNodesQuota(String, String, String)
    */
   void removeGroupOfNodesQuota(String repositoryName, String workspaceName, String patternPath);

   /**
    * @see QuotaManager#setNodeQuota(String, String, String, long, boolean)
    */
   void setGroupOfNodeQuota(String repositoryName, String workspaceName, String patternPath, long quotaLimit,
      boolean asyncUpdate);

   /**
    * Returns alerted node paths. 
    */
   Set<String> getAlertedPaths(String repositoryName, String workspaceName);

   /**
    * @see QuotaManager#getNodeDataSize(String, String, String)
    */
   long getNodeDataSize(String repositoryName, String workspaceName, String nodePath)
      throws UnknownQuotaDataSizeException;

   /**
    * Persists node data size.
    */
   void setNodeDataSize(String repositoryName, String workspaceName, String nodePath, long dataSize);

   /**
    * @see QuotaManager#getWorkspaceQuota(String, String)
    */
   long getWorkspaceQuota(String repositoryName, String workspaceName) throws UnknownQuotaLimitException;

   /**
    * @see QuotaManager#setWorkspaceQuota(String, String, long)
    */
   void setWorkspaceQuota(String repositoryName, String workspaceName, long quotaLimit);

   /**
    * @see QuotaManager#removeWorkspaceQuota(String, String, long)
    */
   void removeWorkspaceQuota(String repositoryName, String workspaceName);

   /**
    * Persists workspace data size.
    */
   void setWorkspaceDataSize(String repositoryName, String workspaceName, long dataSize);

   /**
    * @see QuotaManager#getWorkspaceDataSize(String, String)
    */
   long getWorkspaceDataSize(String repositoryName, String workspaceName) throws UnknownQuotaDataSizeException;

   /**
    * @see QuotaManager#getRepositoryQuota(String)
    */
   long getRepositoryQuota(String repositoryName) throws UnknownQuotaLimitException;

   /**
    * @see QuotaManager#setRepositoryQuota(String, long)
    */
   void setRepositoryQuota(String repositoryName, long quotaLimit);

   /**
    * @see QuotaManager#removeRepositoryQuota
    */
   void removeRepositoryQuota(String repositoryName);

   /**
    * @see QuotaManager#getRepositoryDataSize(String)
    */
   long getRepositoryDataSize(String repositoryName) throws UnknownQuotaDataSizeException;

   /**
    * Persists repository data size.
    */
   void setRepositoryDataSize(String repositoryName, long dataSize);

   /**
    * @see QuotaManager#getGlobalDataSize()
    */
   long getGlobalDataSize() throws UnknownQuotaDataSizeException;

   /**
    * Persists global data size.
    */
   void setGlobalDataSize(long dataSize);

   /**
    * @see QuotaManager#getGlobalQuota() 
    */
   long getGlobalQuota() throws UnknownQuotaLimitException;

   /**
    * @see QuotaManager#setGlobalQuota(long)
    */
   void setGlobalQuota(long quotaLimit);

   /**
    * @see QuotaManager#removeGlobalQuota
    */
   void removeGlobalQuota();

   // ==========================> backup methods

   /**
    * Removes all record about workspace entity.
    */
   void clearWorkspaceData(String repositoryName, String workspaceName) throws BackupException;

   /**
    * Backups all record about workspace entity.
    */
   void backupWorkspaceData(String repositoryName, String workspaceName, ZipObjectWriter writer) throws BackupException;

   /**
    * Restore all record about workspace entity.
    */
   void restoreWorkspaceData(String repositoryName, String workspaceName, ZipObjectReader reader)
      throws BackupException;
}
