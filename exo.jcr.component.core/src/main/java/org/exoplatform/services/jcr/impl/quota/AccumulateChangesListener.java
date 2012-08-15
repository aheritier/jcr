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

import org.exoplatform.services.jcr.dataflow.persistent.PersistenceCommitListener;
import org.exoplatform.services.jcr.dataflow.persistent.PersistenceRollbackListener;
import org.exoplatform.services.rpc.RPCException;

/**
 * Accumulate changes when transaction is committed.
 * 
 * @author <a href="abazko@exoplatform.com">Anatoliy Bazko</a>
 * @version $Id: AccumulateChangesListener.java 34360 2009-07-22 23:58:59Z tolusha $
 */
public class AccumulateChangesListener implements PersistenceRollbackListener, PersistenceCommitListener
{

   /**
    * {@link WorkspaceQuotaManager} instance.
    */
   private final WorkspaceQuotaManager wqm;

   /**
    * AccumulateChangesListener constructor.
    */
   AccumulateChangesListener(WorkspaceQuotaManager wqm)
   {
      this.wqm = wqm;
   }

   /**
    * {@inheritDoc}
    */
   public void onCommit()
   {
      ChangesItem changesItem = wqm.pendingChanges.get();
      try
      {
         if (wqm.testCase)
         {
            // apply changes instantly
            wqm.pushChangesToCoordinator(changesItem, true);
         }
         else
         {
            wqm.pushChangesToCoordinator(changesItem.extractSyncChanges(), true);
            wqm.changesLog.add(wqm.pendingChanges.get());
         }
      }
      catch (SecurityException e)
      {
         throw new IllegalStateException("Can't push changes to coordinator", e.getCause());
      }
      catch (RPCException e)
      {
         throw new IllegalStateException("Can't push changes to coordinator", e.getCause());
      }
      finally
      {
         wqm.pendingChanges.remove();
      }
   }

   /**
    * {@inheritDoc}
    */
   public void onRollback()
   {
      wqm.pendingChanges.remove();
   }
}
