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

import org.exoplatform.commons.utils.SecurityHelper;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
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

   /**
    * {@link WorkspaceQuotaManager} instance.
    */
   private final WorkspaceQuotaManager wqm;

   /**
    * The node path to calculate data size for.
    */
   private final String nodePath;

   /**
    * Workspace name.
    */
   public final String wsName;

   /**
    * Repository name.
    */
   public final String rName;

   /**
    * {@link QuotaPersister}
    */
   public final QuotaPersister quotaPersister;

   /**
    * @see WorkspaceQuotaContext#runNodesTasks
    */
   public final Set<String> runNodesTasks;

   /**
    * CalculateNodeDataSizeTask constructor.
    */
   public CalculateNodeDataSizeTask(WorkspaceQuotaManager wqm, String nodePath)
   {
      this.wqm = wqm;
      this.nodePath = nodePath;

      this.wsName = wqm.getContext().wsName;
      this.rName = wqm.getContext().rName;
      this.quotaPersister = wqm.getContext().quotaPersister;
      this.runNodesTasks = wqm.getContext().runNodesTasks;
   }

   /**
    * {@inheritDoc}
    */
   public void run()
   {
      try
      {
         SecurityHelper.doPrivilegedExceptionAction(new PrivilegedExceptionAction<Void>()
         {
            public Void run() throws Exception
            {
               long dataSize = wqm.getNodeDataSizeDirectly(nodePath);
               quotaPersister.setNodeDataSizeIfQuotaExists(rName, wsName, nodePath, dataSize);

               return null;
            }
         });
      }
      catch (PrivilegedActionException e)
      {
         LOG.warn("Can't calculate node data size " + nodePath + " because: " + e.getCause().getMessage());
      }
      finally
      {
         runNodesTasks.remove(nodePath);
      }
   }
}
