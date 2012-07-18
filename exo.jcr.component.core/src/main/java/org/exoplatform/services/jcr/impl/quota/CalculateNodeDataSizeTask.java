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

import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

import java.util.Set;

/**
 * @author <a href="abazko@exoplatform.com">Anatoliy Bazko</a>
 * @version $Id: CalculateNodeDataSizeTask.java 34360 2009-07-22 23:58:59Z tolusha $
 */
public class CalculateNodeDataSizeTask implements Runnable
{
   /**
    * Logger.
    */
   private final Log LOG = ExoLogger.getLogger("exo.jcr.component.core.CalculateNodeDataSizeTask");

   private final WorkspaceQuotaManager quotaManager;

   private final QuotaPersister quotaPersister;

   private final String rName;

   private final String wsName;

   private final String nodePath;

   private final Set<String> runNodesTasks;

   /**
    * CalculateNodeDataSizeTask constructor.
    */
   public CalculateNodeDataSizeTask(WorkspaceQuotaManager quotaManager, String nodePath, Set<String> runNodesTasks)
   {
      this.quotaManager = quotaManager;
      this.nodePath = nodePath;
      this.quotaPersister = quotaManager.quotaPersister;
      this.rName = quotaManager.rName;
      this.wsName = quotaManager.wsName;
      this.runNodesTasks = runNodesTasks;
   }

   /**
    * {@inheritDoc}
    */
   public void run()
   {
      try
      {
         long dataSize = quotaManager.getNodeDataSizeDirectly(nodePath);
         quotaPersister.setNodeDataSize(rName, wsName, nodePath, dataSize);
      }
      catch (QuotaManagerException e)
      {
         LOG.error(e.getMessage(), e);
      }
      finally
      {
         runNodesTasks.remove(nodePath);
      }
   }
}
