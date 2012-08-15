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

import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Accumulates changes of saves during whole period. Time from time
 * all changes are pushed to coordinator.
 * 
 * @author <a href="abazko@exoplatform.com">Anatoliy Bazko</a>
 * @version $Id: ChangesLog.java 34360 2009-07-22 23:58:59Z tolusha $ 
 */
class ChangesLog extends ConcurrentLinkedQueue<ChangesItem>
{
   /**
    * Returns total workspace changed size.
    */
   public long getWorkspaceDelta()
   {
      long wsDelta = 0;

      Iterator<ChangesItem> changes = iterator();
      while (changes.hasNext())
      {
         wsDelta += changes.next().workspaceDelta;
      }

      return wsDelta;
   }

   /**
    * Return total changed size for particular node.
    */
   public long getNodeDelta(String nodePath)
   {
      long nodeDelta = 0;

      Iterator<ChangesItem> changes = iterator();
      while (changes.hasNext())
      {
         nodeDelta += changes.next().calculatedNodesDelta.get(nodePath);
      }

      return nodeDelta;
   }

   /**
    * Merges all current existed changes into one single {@link ChangesItem} and return it.
    * Don't care if after merge new entries will come.
    */
   public ChangesItem merge()
   {
      ChangesItem totalChanges = new ChangesItem();

      for (ChangesItem particularChanges = poll(); particularChanges != null; particularChanges = poll())
      {
         totalChanges.merge(particularChanges);
      }

      return totalChanges;
   }
}

