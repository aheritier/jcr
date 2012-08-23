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
import org.exoplatform.services.jcr.util.TesterConfigurationHelper;

/**
 * @author <a href="abazko@exoplatform.com">Anatoliy Bazko</a>
 * @version $Id: AbstractQuotaManagerTest.java 34360 2009-07-22 23:58:59Z tolusha $
 */
public abstract class AbstractQuotaManagerTest extends JcrAPIBaseTest
{

   protected WorkspaceQuotaManager ws1QuotaManager;

   protected WorkspaceQuotaManager wsQuotaManager;

   protected RepositoryQuotaManager dbQuotaManager;

   protected QuotaManager quotaManager;

   protected TesterConfigurationHelper helper = TesterConfigurationHelper.getInstance();

   /**
    * {@inheritDoc}
    */
   public void setUp() throws Exception
   {
      super.setUp();

      quotaManager = (QuotaManager)repository.getWorkspaceContainer("ws").getComponent(QuotaManager.class);

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
      super.tearDown();
   }

   protected void waitTasksTermination(WorkspaceQuotaManager wqm) throws Exception
   {
      wqm.suspend();
      wqm.resume();
   }

   protected boolean quotaShouldNotExists(WorkspaceQuotaManager wqm, String nodePath) throws QuotaManagerException
   {
      try
      {
         wqm.getNodeQuota(nodePath);
         return false;
      }
      catch (UnknownQuotaLimitException e)
      {
         return true;
      }
   }

   protected boolean dataSizeShouldNotExists(WorkspaceQuotaManager wqm, String nodePath) throws QuotaManagerException
   {
      try
      {
         wqm.getNodeDataSize(nodePath);
         return false;
      }
      catch (UnknownDataSizeException e)
      {
         return true;
      }
   }

   protected void assertNodeDataSize(WorkspaceQuotaManager wqm, String nodePath) throws Exception
   {
      long expectedSize = session.itemExists(nodePath) ? wqm.getNodeDataSizeDirectly(nodePath) : 0;

      long startTime = System.currentTimeMillis();
      long maxTime = 60 * 1000;

      while (true)
      {
         try
         {
            long measuredSize = wqm.getNodeDataSize(nodePath);
            if (expectedSize == measuredSize)
            {
               return;
            }
         }
         catch (QuotaManagerException e)
         {
         }
         
         Thread.sleep(100);

         if (System.currentTimeMillis() - startTime > maxTime)
         {
            break;
         }
      }

      fail();
   }

   protected void assertWorkspaceSize(WorkspaceQuotaManager wqm) throws Exception
   {
      long expectedSize = wqm.getWorkspaceDataSizeDirectly();

      long startTime = System.currentTimeMillis();
      long maxTime = 60 * 1000;

      while (true)
      {
         try
         {
            long measuredSize = wqm.getWorkspaceDataSize();
            if (expectedSize == measuredSize)
            {
               return;
            }
         }
         catch (QuotaManagerException e)
         {
         }

         Thread.sleep(100);

         if (System.currentTimeMillis() - startTime > maxTime)
         {
            break;
         }
      }

      fail();
   }
}
