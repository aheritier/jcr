/*
 * Copyright (C) 2009 eXo Platform SAS.
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
package org.exoplatform.services.jcr.impl.core.query;

import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

import javax.jcr.RepositoryException;

/**
 * Created by The eXo Platform SAS.
 * 
 * @author <a href="mailto:Sergey.Kabashnyuk@gmail.com">Sergey Kabashnyuk</a>
 * @version $Id: $
 */
public class IndexException extends RepositoryException
{
   /**
    * 
    */
   private static final long serialVersionUID = 2247843831064852072L;

   /**
    * Class logger.
    */
   private static final Log LOG = ExoLogger.getLogger(IndexException.class);

   /**
    * 
    */
   public IndexException()
   {
      super();
   }

   /**
    * @param message
    * @param cause
    */
   public IndexException(String message, Throwable cause)
   {
      super(message, cause);
   }

   /**
    * @param message
    */
   public IndexException(String message)
   {
      super(message);
   }

   /**
    * @param cause
    */
   public IndexException(Throwable cause)
   {
      super(cause);
   }
}
