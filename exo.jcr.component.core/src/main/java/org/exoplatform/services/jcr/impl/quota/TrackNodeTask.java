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
 * @version $Id: TrackNodeTask.java 34360 2009-07-22 23:58:59Z tolusha $
 */
public class TrackNodeTask implements Runnable
{

   /**
    * Logger.
    */
   private final Log LOG = ExoLogger.getLogger("exo.jcr.component.core.TrackNodeTask");

   private final WorkspaceQuotaManager quotaManager;

   private final long quotaLimit;

   private final String nodePath;

   private final boolean asyncUpdate;

   /**
    * TrackNodeTask constructor. 
    */
   public TrackNodeTask(WorkspaceQuotaManager quotaManager, String nodePath, long quotaLimit, boolean asyncUpdate)
   {
      this.quotaManager = quotaManager;
      this.quotaLimit = quotaLimit;
      this.nodePath = nodePath;
      this.asyncUpdate = asyncUpdate;
   }

   /**
    * {@inheritDoc}
    */
   public void run()
   {
      try
      {
         quotaManager.trackNode(nodePath, quotaLimit, asyncUpdate);
      }
      catch (QuotaManagerException e)
      {
         LOG.error(e.getMessage(), e);
      }
   }
}
