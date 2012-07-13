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

/**
 * @author <a href="abazko@exoplatform.com">Anatoliy Bazko</a>
 * @version $Id: SetNodeQuotaTask.java 34360 2009-07-22 23:58:59Z tolusha $
 */
public class SetNodeQuotaTask implements Runnable
{
   /**
    * Logger.
    */
   private final Log LOG = ExoLogger.getLogger("exo.jcr.component.core.SetNodeQuotaTask");

   private final WorkspaceQuotaManager quotaManager;

   private final QuotaPersister quotaPersister;

   private final String rName;

   private final String wsName;

   private final String nodePath;

   private final long quotaLimit;

   /**
    * SetNodeQuotaTask constructor. 
    */
   public SetNodeQuotaTask(WorkspaceQuotaManager quotaManager, String nodePath, long quotaLimit)
   {
      this.quotaManager = quotaManager;
      this.nodePath = nodePath;
      this.quotaLimit = quotaLimit;
      this.quotaPersister = quotaManager.quotaPersister;
      this.rName = quotaManager.rName;
      this.wsName = quotaManager.wsName;
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

         quotaManager.defineAlertedPath(nodePath, dataSize, quotaLimit);
      }
      catch (QuotaManagerException e)
      {
         LOG.error(e.getMessage(), e);
      }
   }
}
