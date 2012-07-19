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

import org.exoplatform.services.jcr.JcrAPIBaseTest;

/**
 * @author <a href="abazko@exoplatform.com">Anatoliy Bazko</a>
 * @version $Id: AbstractQuotaManagerTest.java 34360 2009-07-22 23:58:59Z tolusha $
 */
public abstract class AbstractQuotaManagerTest extends JcrAPIBaseTest
{

   protected WorkspaceQuotaManager ws1QuotaManager;

   protected WorkspaceQuotaManager wsQuotaManager;

   protected RepositoryQuotaManager dbQuotaManager;

   /**
    * {@inheritDoc}
    */
   public void setUp() throws Exception
   {
      super.setUp();

      dbQuotaManager =
         (RepositoryQuotaManager)repository.getWorkspaceContainer("ws").getComponent(RepositoryQuotaManager.class);
      
      ws1QuotaManager =
         (WorkspaceQuotaManager)repository.getWorkspaceContainer("ws1").getComponent(WorkspaceQuotaManager.class);

      wsQuotaManager =
         (WorkspaceQuotaManager)repository.getWorkspaceContainer("ws").getComponent(WorkspaceQuotaManager.class);
   }

   /**
    * {@inheritDoc}
    */
   public void tearDown() throws Exception
   {
      //      wsQuotaManager.clean();
      //      ws1QuotaManager.clean();

      super.tearDown();
   }
}
