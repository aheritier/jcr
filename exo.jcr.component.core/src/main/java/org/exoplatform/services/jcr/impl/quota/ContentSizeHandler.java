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

/**
 * Manages persisted content size changes.
 * 
 * @author <a href="abazko@exoplatform.com">Anatoliy Bazko</a>
 * @version $Id: ContentSizeHandler.java 34360 2009-07-22 23:58:59Z tolusha $
 */
public class ContentSizeHandler
{
   /**
    * Accumulates persisted data size changing.
    * In case of {@link ItemState#ADDED} it is positive value, for {@link ItemState#DELETED} it is
    * negative value and so on.
    */
   private Long deltaSize = null;
   
   /**
    * Returns {@link #deltaSize}.
    */
   public long getChangedSize()
   {
      if (deltaSize == null)
      {
         throw new IllegalStateException("Content size handler in illegal state");
      }

      return deltaSize;
   }

   /**
    * Accumulates changes.
    */
   public void accumulateSize(long deltaSize)
   {
      if (this.deltaSize == null)
      {
         this.deltaSize = deltaSize;
      }
      else
      {
         this.deltaSize += deltaSize;
      }
   }
}
