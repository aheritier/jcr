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
 * Per workspace QuotaManager operation.
 * 
 * @author <a href="abazko@exoplatform.com">Anatoliy Bazko</a>
 * @version $Id: QuotaManager.java 34360 2009-07-22 23:58:59Z tolusha $
 */
public interface WorkspaceQuotaManager
{
   /**
    * Returns a size of the Node. Size of the node is a length of content, stored in it.
    * If node has child nodes - size is a sum of all child nodes sizes plus 
    * size of the current node.
    * 
    * @param nodePath
    *          the absolute path to node
    * 
    * @throws QuotaManagerException If an error occurs.
    */
   long getNodeDataSize(String nodePath) throws QuotaManagerException;

   /**
    * Returns a size of the Workspace. Size of the workspace is the the size of all nodes 
    * are placed in it.
    * 
    * @throws QuotaManagerExceptionIf an error occurs.
    */
   long getWorkspaceDataSize() throws QuotaManagerException;

   /**
    * Returns a index size. Size of the workspace's index is a size of the 
    * index directory at file system belonging to workspace.
    * 
    * @throws QuotaManagerException If an error occurs.
    */
   long getWorkspaceIndexSize() throws QuotaManagerException;
}
