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
package org.exoplatform.services.jcr.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by The eXo Platform SAS .
 * 
 * @author <a href="mailto:gavrikvetal@gmail.com">Vitaliy Gulyy</a>
 * @version $
 */

public abstract class AbstractRepositoryServiceConfiguration
{

   protected List<RepositoryEntry> repositoryConfigurations = new ArrayList<RepositoryEntry>();

   protected String defaultRepositoryName;

   /**
    * Set default repository name
    * 
    * @param defaultRepositoryName
    */
   public void setDefaultRepositoryName(String defaultRepositoryName)
   {
      this.defaultRepositoryName = defaultRepositoryName;
   }

   /**
    * 
    * Get default repository name
    * 
    * @return
    */
   public final String getDefaultRepositoryName()
   {
      return defaultRepositoryName;
   }

   public List<RepositoryEntry> getRepositoryConfigurations()
   {
      return repositoryConfigurations;
   }

   /**
    * Checks if current configuration can be saved.
    * 
    * @return
    */
   public boolean isRetainable()
   {
      return false;
   }

}
