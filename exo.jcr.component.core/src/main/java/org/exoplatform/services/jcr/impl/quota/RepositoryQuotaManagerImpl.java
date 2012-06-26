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

import org.exoplatform.services.jcr.config.RepositoryEntry;
import org.picocontainer.Startable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author <a href="abazko@exoplatform.com">Anatoliy Bazko</a>
 * @version $Id: RepositoryQuotaManagerImpl.java 34360 2009-07-22 23:58:59Z tolusha $
 */
public class RepositoryQuotaManagerImpl implements RepositoryQuotaManager, Startable
{

   /**
    * All {@link WorkspaceQuotaManager} belonging to repository.
    */
   private Map<String, WorkspaceQuotaManager> quotaManagers = new ConcurrentHashMap<String, WorkspaceQuotaManager>();

   /**
    * The repository name.
    */
   protected final String rName;

   /**
    * RepositoryQuotaManagerImpl constructor.
    */
   public RepositoryQuotaManagerImpl(RepositoryEntry rEntry)
   {
      this.rName = rEntry.getName();
   }

   /**
    * {@inheritDoc}
    */
   public long getNodeDataSize(String workspaceName, String nodePath) throws QuotaManagerException
   {
      WorkspaceQuotaManager wqm = getWorkspaceQuotaManager(workspaceName);
      return wqm.getNodeDataSize(nodePath);
   }

   /**
    * {@inheritDoc}
    */
   public long getWorkspaceDataSize(String workspaceName) throws QuotaManagerException
   {
      WorkspaceQuotaManager wqm = getWorkspaceQuotaManager(workspaceName);
      return wqm.getWorkspaceDataSize();
   }

   /**
    * {@inheritDoc}
    */
   public long getWorkspaceIndexSize(String workspaceName) throws QuotaManagerException
   {
      WorkspaceQuotaManager wqm = getWorkspaceQuotaManager(workspaceName);
      return wqm.getWorkspaceIndexSize();
   }

   /**
    * {@inheritDoc}
    */
   public long getRepositoryDataSize() throws QuotaManagerException
   {
      long size = 0;
      for (WorkspaceQuotaManager wQuotaManager : quotaManagers.values())
      {
         size += wQuotaManager.getWorkspaceDataSize();
      }

      return size;
   }

   /**
    * {@inheritDoc}
    */
   public long getRepositoryIndexSize() throws QuotaManagerException
   {
      long size = 0;
      for (WorkspaceQuotaManager wQuotaManager : quotaManagers.values())
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
   }

   /**
    * {@inheritDoc}
    */
   public void stop()
   {
      quotaManagers.clear();
   }

   /**
    * {@inheritDoc}
    */
   public void registerWorkspaceQuotaManager(String workspaceName, WorkspaceQuotaManager wQuotaManager)
   {
      quotaManagers.put(workspaceName, wQuotaManager);
   }

   /**
    * {@inheritDoc}
    */
   public void unregisterWorkspaceQuotaManager(String workspaceName)
   {
      quotaManagers.remove(workspaceName);
   }

   private WorkspaceQuotaManager getWorkspaceQuotaManager(String workspaceName) throws QuotaManagerException
   {
      WorkspaceQuotaManager wqm = quotaManagers.get(workspaceName);
      if (wqm == null)
      {
         throw new QuotaManagerException("Workspace " + workspaceName + " is not registered in " + rName);
      }

      return wqm;
   }
}
