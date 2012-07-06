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

import org.exoplatform.services.jcr.util.StringNumberParser;

/**
 * Is occurred when entity data size is exceeded quota limit.
 * 
 * @author <a href="abazko@exoplatform.com">Anatoliy Bazko</a>
 * @version $Id: ExceededQuotaLimitException.java 34360 2009-07-22 23:58:59Z tolusha $
 */
public class ExceededQuotaLimitException extends QuotaManagerException
{

   /**
    * Entity quota limit.
    */
   private final long quotaLimit;

   /**
    * Entity data size.
    */
   private final long dataSize;

   /**
    * Constructs a new exception with the specified detail message.  The
    * cause is not initialized, and may subsequently be initialized by
    * a call to {@link #initCause}.
    *
    * @param   message   the detail message. The detail message is saved for 
    *          later retrieval by the {@link #getMessage()} method.
    */
   public ExceededQuotaLimitException(String message, long quotaLimit, long dataSize)
   {
      super(message);

      this.quotaLimit = quotaLimit;
      this.dataSize = dataSize;
   }

   /**
    * Getter for {@link #dataSize}.
    */
   public long getDataSize()
   {
      return dataSize;
   }

   /**
    * Getter for {@link #quotaLimit}.
    */
   public long getQuotaLimit()
   {
      return quotaLimit;
   }

   private static String createMessage(long quotaLimit, long dataSize)
   {
      StringBuilder message = new StringBuilder();
      message.append("Global data size exceeded global quota limit on ");
      message.append(StringNumberParser.serializeLong(dataSize - quotaLimit));
      message.append("B");
      
      return message.toString();
   }

}
