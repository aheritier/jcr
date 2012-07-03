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
 * QuotaManager provides methods for getting sizes of all eXo instances, getting/setting quotas and various settings.
 * 
 * @author <a href="abazko@exoplatform.com">Anatoliy Bazko</a>
 * @version $Id: QuotaManager.java 34360 2009-07-22 23:58:59Z tolusha $
 */
public interface QuotaManager
{
   /**
    * Returns a size of the Node. Size of the node is a length of content, stored in it.
    * If node has child nodes - size is a sum of all child nodes sizes plus 
    * size of the current node.
    *
    * @param repositoryName
    *          the repository name            
    * @param workspaceName
    *          the workspace name in repository 
    * @param nodePath
    *          the absolute path to node
    * @throws QuotaManagerException If an error occurs.
    */
   long getNodeDataSize(String repositoryName, String workspaceName, String nodePath) throws QuotaManagerException;

   /**
    * Returns a quota of the Node. Quota of the node is a maximum allowed size of this node.
    * 
    * @param repositoryName
    *          the repository name            
    * @param workspaceName
    *          the workspace name in repository 
    * @param nodePath
    *          the absolute path to node
    * @throws QuotaManagerException If an error occurs.
    * @throws UnknownQuotaLimitException If quota limit was not set
    */
   long getNodeQuota(String repositoryName, String workspaceName, String nodePath) throws QuotaManagerException;

   /**
    * Setting a quota of the Node.
    *
    * @param repositoryName
    *          the repository name            
    * @param workspaceName
    *          the workspace name in repository 
    * @param nodePath
    *          the absolute path to node
    * @param quotaSize
    *          the maximum allowed sum of content size stored in node
    * @param asyncUpdate
    *          true means checking if node exceeds quota limit will be performed asynchronously, i.e. for some
    *          period of time difference between new and old content size will be accumulated and only then send to
    *          validate          
    * @throws QuotaManagerException If an error occurs.
    */
   void setNodeQuota(String repositoryName, String workspaceName, String nodePath, long quotaSize, boolean asyncUpdate)
      throws QuotaManagerException;

   /**
    * Setting a quota for group of nodes.
    *
    * @param repositoryName
    *          the repository name            
    * @param workspaceName
    *          the workspace name in repository 
    * @param pattern
    *          the pattern indicates group of nodes to set quota, allowed <code>*</code> as any node name 
    *          and <code>%</code> as any character in name
    * @param quotaSize
    *          the maximum allowed sum of content size stored in node
    * @param asyncUpdate
    *          true means checking if node exceeds quota limit will be performed asynchronously, i.e. for some
    *          period of time difference between new and old content size will be accumulated and only then send to
    *          validate          
    * @throws QuotaManagerException If an error occurs.
    */
   void setGroupOfNodesQuota(String repositoryName, String workspaceName, String pattern, long quotaSize,
      boolean asyncUpdate) throws QuotaManagerException;

   /**
    * Returns current sum of content size stored in workspace.
    * 
    * @param repositoryName
    *          the repository name            
    * @param workspaceName
    *          the workspace name in repository 
    * @throws QuotaManagerException If an error occurs.
    */
   long getWorkspaceDataSize(String repositoryName, String workspaceName) throws QuotaManagerException;

   /**
    * Returns maximum allowed sum of content size that can be stored in workspace.
    *
    * @param repositoryName
    *          the repository name            
    * @param workspaceName
    *          the workspace name in repository 
    * @throws QuotaManagerException If an error occurs.
    * @throws UnknownQuotaLimitException If quota limit was not set 
    */
   long getWorkspaceQuota(String repositoryName, String workspaceName) throws QuotaManagerException;

   /**
    * Setting maximum allowed sum of content size stored in workspace.
    *
    * @param repositoryName
    *          the repository name            
    * @param workspaceName
    *          the workspace name in repository 
    * @param quotaSize
    *          the sum of maximum allowed content size
    *                   
    * @throws QuotaManagerException If an error occurs.
    */
   void setWorkspaceQuota(String repositoryName, String workspaceName, long quotaSize) throws QuotaManagerException;

   /**
    * Returns a index size of particular workspace in repository. Size of the workspace's index is a size of the 
    * index directory at file system belonging to workspace.
    *  
    * @param repositoryName
    *          the repository name    
    * @param workspaceName
    *          the workspace name in repository
    *          
    * @throws QuotaManagerException If an error occurs.
    */
   long getWorkspaceIndexSize(String repositoryName, String workspaceName) throws QuotaManagerException;

   /**
    * Returns current sum of content size stored in repository.
    * 
    * @param repositoryName
    *          the repository name            
    * @throws QuotaManagerException If an error occurs.
    */
   long getRepositoryDataSize(String repositoryName) throws QuotaManagerException;

   /**
    * Returns maximum allowed sum of content size stored in repository.
    *
    * @param repositoryName
    *          the repository name            
    * @throws QuotaManagerException If an error occurs.
    * @throws UnknownQuotaLimitException If quota limit was not set 
    */
   long getRepositoryQuota(String repositoryName) throws QuotaManagerException;

   /**
    * Setting maximum allowed sum of content size that can be stored in repository.
    *
    * @param repositoryName
    *          the repository name            
    * @param quotaSize
    *          the sum of maximum allowed content size
    *                   
    * @throws QuotaManagerException If an error occurs.
    */
   void setRepositoryQuota(String repositoryName, long quotaSize) throws QuotaManagerException;

   /**
    * Returns a index size of particular repository. Size of the repository's index is a size of the 
    * index directory at file system belonging to repository.
    *  
    * @param repositoryName
    *          the repository name    
    *          
    * @throws QuotaManagerException If an error occurs.
    */
   long getRepositoryIndexSize(String repositoryName) throws QuotaManagerException;

   /**
    * Returns current sum of content size whole JCR.
    * 
    * @throws QuotaManagerException If an error occurs.
    */
   long getGlobalDataSize() throws QuotaManagerException;

   /**
    * Returns maximum allowed sum of content size stored in whole JCR.
    *
    * @throws QuotaManagerException If an error occurs.
    * @throws UnknownQuotaLimitException If quota limit was not set 
    */
   long getGlobalQuota() throws QuotaManagerException;

   /**
    * Setting maximum allowed sum of content size that can be stored in whole JCR.
    *
    * @param quotaSize
    *          the sum of maximum allowed content size
    *                   
    * @throws QuotaManagerException If an error occurs.
    */
   void setGlobalQuota(long quotaSize) throws QuotaManagerException;

   /**
    * Returns a index size of whole JCR repository. 
    *  
    * @throws QuotaManagerException If an error occurs.
    */
   long getGlobalIndexSize() throws QuotaManagerException;
}
