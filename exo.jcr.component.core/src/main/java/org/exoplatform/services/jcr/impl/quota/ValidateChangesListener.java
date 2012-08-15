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

import org.exoplatform.services.jcr.dataflow.ItemState;
import org.exoplatform.services.jcr.dataflow.ItemStateChangesLog;
import org.exoplatform.services.jcr.dataflow.persistent.MandatoryItemsPersistenceListener;
import org.exoplatform.services.jcr.datamodel.QPath;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.jcr.RepositoryException;

/**
 * @link MandatoryItemsPersistenceListener} implementation.
 * 
 * Is TX aware listener. Will receive changes before data is committed to storage.
 * It allows to validate if some entity can exceeds quota limit if new changes
 * is coming.
 * 
 * @author <a href="abazko@exoplatform.com">Anatoliy Bazko</a>
 * @version $Id: ValidateChangesListener.java 34360 2009-07-22 23:58:59Z tolusha $
 */
public class ValidateChangesListener implements MandatoryItemsPersistenceListener
{
   /**
    * {@link WorkspaceQuotaManager} instance.
    */
   private final WorkspaceQuotaManager wqm;

   /**
    * AccumulateChangesListener constructor.
    */
   ValidateChangesListener(WorkspaceQuotaManager wqm)
   {
      this.wqm = wqm;
   }

   /**
    * {@inheritDoc}
    * Checks if new changes can exceeds some limits. It either can be node, workspace, 
    * repository or global JCR instance.
    * 
    * @throws IllegalStateException if data size exceeded quota limit
    */
   public void onSaveItems(ItemStateChangesLog itemStates)
   {
      try
      {
         ChangesItem changesItem = new ChangesItem();

         for (ItemState state : itemStates.getAllStates())
         {
            if (!state.getData().isNode())
            {
               String itemPath = getPath(state.getData().getQPath().makeParentPath());

               Set<String> parentsWithQuota =
                  wqm.quotaPersister.getAllParentNodesWithQuota(wqm.rName, wqm.wsName, itemPath);
               for (String parent : parentsWithQuota)
               {
                  updateCalculatedNodesDelta(changesItem, parent, state.getChangedSize());
                  updateAsyncUpdate(changesItem, parent);
               }

               changesItem.workspaceDelta += state.getChangedSize();
            }
            else
            {
               updateUnknownNodesDelta(changesItem, state);
            }
         }

         wqm.pendingChanges.set(changesItem);

         validatePendingChanges(changesItem.workspaceDelta);
         validatePendingNodesChanges(changesItem.calculatedNodesDelta);
      }
      catch (ExceededQuotaLimitException e)
      {
         throw new IllegalStateException(e.getMessage(), e);
      }
   }

   /**
    * Checks if changes were made but changed size is unknown. If so, determinate
    * for which nodes data size should be recalculated at all and put that paths into
    * respective collection.
    */
   private void updateUnknownNodesDelta(ChangesItem changesItem, ItemState state)
   {
      if (state.isPathChanged())
      {
         String itemPath = getPath(state.getData().getQPath());
         String oldPath = getPath(state.getOldPath());

         for (String trackedPath : wqm.quotaPersister.getAllTrackedNodes(wqm.rName, wqm.wsName))
         {
            if (oldPath.startsWith(trackedPath))
            {
               changesItem.unknownNodesDelta.add(oldPath);
            }

            if (itemPath.startsWith(trackedPath))
            {
               changesItem.unknownNodesDelta.add(itemPath);
            }
         }
      }
   }

   /**
    * Checks if data size for node is represented by <code>quotableParent</code> path 
    * should be updated asynchronously. If so that path is putting into respective collection.
    * 
    * @param quotableParent
    *          absolute path to node for which quota is set 
    */
   private void updateAsyncUpdate(ChangesItem changesItem, String quotableParent)
   {
      boolean isAsyncUpdate;
      try
      {
         isAsyncUpdate = wqm.quotaPersister.isNodeQuotaOrGroupOfNodesQuotaAsync(wqm.rName, wqm.wsName, quotableParent);
      }
      catch (UnknownQuotaLimitException e)
      {
         isAsyncUpdate = false;
      }

      if (isAsyncUpdate)
      {
         changesItem.asyncUpdate.add(quotableParent);
      }
   }

   /**
    * Update changed size for node is represented by <code>quotableParent</code> path. 
    *   
    * @param quotableParent
    *          absolute path to node for which quota is set
    * @param changedSize
    *          changes one node or of its descendant
    */
   private void updateCalculatedNodesDelta(ChangesItem changesItem, String quotableParent, long changedSize)
   {
      Long oldDelta = changesItem.calculatedNodesDelta.get(quotableParent);
      Long newDelta = changedSize + (oldDelta != null ? oldDelta : 0);

      changesItem.calculatedNodesDelta.put(quotableParent, newDelta);
   }

   /**
    * {@inheritDoc}
    */
   public boolean isTXAware()
   {
      return true;
   }

   /**
    * @see BaseQuotaManager#validatePendingChanges(long)
    */
   private void validatePendingChanges(long delta) throws ExceededQuotaLimitException
   {
      delta += wqm.changesLog.getWorkspaceDelta();

      wqm.repositoryQuotaManager.validatePendingChanges(delta);
      try
      {
         long quotaLimit = wqm.quotaPersister.getWorkspaceQuota(wqm.rName, wqm.wsName);

         try
         {
            long dataSize = wqm.quotaPersister.getWorkspaceDataSize(wqm.rName, wqm.wsName);

            if (dataSize + delta > quotaLimit)
            {
               wqm.repositoryQuotaManager.globalQuotaManager.behaveOnQuotaExceeded("Workspace " + wqm.uniqueName
                  + " data size exceeded quota limit");
            }
         }
         catch (UnknownDataSizeException e)
         {
            return;
         }
      }
      catch (UnknownQuotaLimitException e)
      {
         return;
      }
   }

   /**
    * @see BaseQuotaManager#validatePendingChanges(long)
    */
   private void validatePendingNodesChanges(Map<String, Long> nodesDelta) throws ExceededQuotaLimitException
   {
      for (Entry<String, Long> entry : nodesDelta.entrySet())
      {
         String nodePath = entry.getKey();
         Long delta = entry.getValue() + wqm.changesLog.getNodeDelta(nodePath);

         try
         {
            long dataSize = wqm.quotaPersister.getNodeDataSize(wqm.rName, wqm.wsName, nodePath);
            try
            {
               long quotaLimit = wqm.quotaPersister.getNodeQuotaOrGroupOfNodesQuota(wqm.rName, wqm.wsName, nodePath);
               if (dataSize + delta > quotaLimit)
               {
                  wqm.repositoryQuotaManager.globalQuotaManager.behaveOnQuotaExceeded("Node " + nodePath
                     + " data size exceeded quota limit");
               }
            }
            catch (UnknownQuotaLimitException e)
            {
               continue;
            }
         }
         catch (UnknownDataSizeException e)
         {
            continue;
         }
      }
   }

   /**
    * Returns item absolute path.
    * 
    * @param path
    *          {@link QPath} representation
    * @throws IllegalStateException if something wrong
    */
   private String getPath(QPath path)
   {
      try
      {
         return wqm.lFactory.createJCRPath(path).getAsString(false);
      }
      catch (RepositoryException e)
      {
         throw new IllegalStateException(e.getMessage(), e);
      }
   }
}

