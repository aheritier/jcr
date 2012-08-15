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
package org.exoplatform.services.jcr.dataflow;

import java.util.List;

/**
 * @author <a href="mailto:aplotnikov@exoplatform.com">Andrey Plotnikov</a>
 * @version $Id: WrapperChangesLog.java 34360 10 Aug 2012 andrew.plotnikov $
 */
public class ChangesLogFacade
{

   private PlainChangesLogImpl changesLog;

   /**
    * ChangesLogFacade constructor. 
    */
   public ChangesLogFacade(PlainChangesLogImpl changesLog)
   {
      this.changesLog = changesLog;
   }

   /**
    * ChangesLogFacade constructor. 
    */
   public ChangesLogFacade(List<ItemState> itemsList)
   {
      this.changesLog = new PlainChangesLogImpl();
      changesLog.addAll(itemsList);
   }

   /**
    * @see PlainChangesLogImpl#getLastChildOrderNumber(String) 
    */
   public int getLastChildOrderNumber(String rootIdentifier)
   {
      return changesLog.getLastChildOrderNumber(rootIdentifier);
   }
}
