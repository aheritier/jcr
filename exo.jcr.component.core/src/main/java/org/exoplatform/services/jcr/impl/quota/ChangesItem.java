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

import org.exoplatform.services.jcr.impl.Constants;
import org.exoplatform.services.jcr.storage.WorkspaceDataContainer;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Wraps information of  changed data size of particular save. First is put into pending changes
 * and after save is performed being moved into changes log. 
 * 
 * @author <a href="abazko@exoplatform.com">Anatoliy Bazko</a>
 * @version $Id: ChangesItem.java 34360 2009-07-22 23:58:59Z tolusha $
 */
class ChangesItem implements Externalizable
{
   /**
    * ChangesItem constructor.
    */
   public ChangesItem()
   {
   }

   /**
    * Contains calculated workspace data size changes of particular save. 
    */
   long workspaceDelta;

   /**
    * Contains calculated nodes data size changes of particular save.
    * Represents {@link Map} with absolute node path as key and changed node 
    * data size of all its descendants as value respectively.
    */
   Map<String, Long> calculatedNodesDelta = new HashMap<String, Long>();

   /**
    * Set absolute paths of nodes for which changes were made but changed size is unknown. Most
    * famous case when {@link WorkspaceDataContainer#TRIGGER_EVENTS_FOR_DESCENDENTS_ON_RENAME} is 
    * set to false and move operation is performed.
    */
   Set<String> unknownNodesDelta = new HashSet<String>();

   /**
    * Collects node paths for which data size updating
    * is performing asynchronously.
    */
   Set<String> asyncUpdate = new HashSet<String>();

   /**
    * Merges current changes with new one.
    */
   public void merge(ChangesItem changesItem)
   {
      workspaceDelta += changesItem.workspaceDelta;

      for (Entry<String, Long> changesEntry : changesItem.calculatedNodesDelta.entrySet())
      {
         String nodePath = changesEntry.getKey();
         Long currentDelta = changesEntry.getValue();

         Long oldDelta = calculatedNodesDelta.get(nodePath);
         Long newDelta = currentDelta + (oldDelta == null ? 0 : oldDelta);

         calculatedNodesDelta.put(nodePath, newDelta);
      }

      for (String path : changesItem.unknownNodesDelta)
      {
         unknownNodesDelta.add(path);
      }

      for (String path : changesItem.asyncUpdate)
      {
         asyncUpdate.add(path);
      }
   }

   /**
    * Checks if there is any changes.
    */
   public boolean isEmpty()
   {
      return workspaceDelta == 0 && calculatedNodesDelta.isEmpty() && unknownNodesDelta.isEmpty();
   }

   /**
    * Leave in {@link ChangesItem} only changes should be apply asynchronously 
    * and return ones to apply instantly.
    */
   public ChangesItem extractSyncChanges()
   {
      ChangesItem syncChangesItem = new ChangesItem();

      Iterator<String> iter = calculatedNodesDelta.keySet().iterator();
      while (iter.hasNext() && !asyncUpdate.isEmpty())
      {
         String nodePath = iter.next();
         
         if (!asyncUpdate.contains(nodePath))
         {
            Long chanagedSize = calculatedNodesDelta.get(nodePath);
            syncChangesItem.calculatedNodesDelta.put(nodePath, chanagedSize);
            syncChangesItem.workspaceDelta += chanagedSize;

            iter.remove();
            this.asyncUpdate.remove(nodePath);
            this.workspaceDelta -= chanagedSize;
         }
      }

      return syncChangesItem;
   }

   /**
    * {@inheritDoc}
    */
   public void writeExternal(ObjectOutput out) throws IOException
   {
      out.writeLong(workspaceDelta);

      out.writeInt(calculatedNodesDelta.size());
      for (Entry<String, Long> entry : calculatedNodesDelta.entrySet())
      {
         writeString(out, entry.getKey());
         out.writeLong(entry.getValue());
      }

      out.writeInt(unknownNodesDelta.size());
      for (String path : unknownNodesDelta)
      {
         writeString(out, path);
      }

      out.writeInt(asyncUpdate.size());
      for (String path : asyncUpdate)
      {
         writeString(out, path);
      }
   }

   private void writeString(ObjectOutput out, String str) throws IOException
   {
      byte[] data = str.getBytes(Constants.DEFAULT_ENCODING);
      out.writeInt(data.length);
      out.write(data);
   }

   /**
    * {@inheritDoc}
    */
   public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException
   {
      this.workspaceDelta = in.readLong();

      int size = in.readInt();
      this.calculatedNodesDelta = new HashMap<String, Long>(size);
      for (int i = 0; i < size; i++)
      {
         String nodePath = readString(in);
         Long delta = in.readLong();

         calculatedNodesDelta.put(nodePath, delta);
      }

      size = in.readInt();
      this.unknownNodesDelta = new HashSet<String>(size);
      for (int i = 0; i < size; i++)
      {
         String nodePath = readString(in);
         unknownNodesDelta.add(nodePath);
      }

      size = in.readInt();
      this.asyncUpdate = new HashSet<String>(size);
      for (int i = 0; i < size; i++)
      {
         String nodePath = readString(in);
         asyncUpdate.add(nodePath);
      }
   }

   private String readString(ObjectInput in) throws IOException
   {
      byte[] data = new byte[in.readInt()];
      in.readFully(data);

      return new String(data, Constants.DEFAULT_ENCODING);
   }
}
