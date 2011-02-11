/*
 * Copyright (C) 2003-2010 eXo Platform SAS.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see<http://www.gnu.org/licenses/>.
 */
package org.exoplatform.services.jcr.impl.util.jdbc.cleaner;

import org.exoplatform.services.jcr.config.WorkspaceEntry;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

/**
 * @author <a href="mailto:anatoliy.bazko@gmail.com">Anatoliy Bazko</a>
 * @version $Id: HSQLSingleDBCleaner.java 3655 2010-12-10 08:25:41Z tolusha $
 */
public class HSQLSingleDBCleaner extends SingleDBCleaner
{

   /**
    * HSQLSingleDBCleaner constructor.
    */
   public HSQLSingleDBCleaner(WorkspaceEntry wsEntry, Connection connection)
   {
      super(wsEntry, connection, true);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   protected List<String> getDBCleanScripts()
   {
      List<String> scripts = new ArrayList<String>(commonSingleDBCleanScripts);
      scripts.add("delete from JCR_SITEM where I_CLASS=2 and CONTAINER_NAME='" + containerName + "'");

      return scripts;
   }
}