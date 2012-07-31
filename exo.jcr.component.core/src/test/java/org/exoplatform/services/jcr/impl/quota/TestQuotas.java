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

import org.exoplatform.services.jcr.config.SimpleParameterEntry;
import org.exoplatform.services.jcr.config.WorkspaceEntry;
import org.exoplatform.services.jcr.core.ManageableRepository;
import org.exoplatform.services.jcr.impl.core.SessionImpl;
import org.exoplatform.services.jcr.impl.storage.jdbc.JDBCDataContainerConfig.DatabaseStructureType;
import org.exoplatform.services.jcr.storage.WorkspaceDataContainer;
import org.exoplatform.services.jcr.util.TesterConfigurationHelper;

import javax.jcr.Node;

/**
 * @author <a href="abazko@exoplatform.com">Anatoliy Bazko</a>
 * @version $Id: TestQuotas.java 34360 2009-07-22 23:58:59Z tolusha $
 */
public class TestQuotas extends AbstractQuotaManagerTest
{

   /**
    * Checks if data size of removed node equals to zero.
    */
   public void testDataSizeRemovedNode() throws Exception
   {
      try
      {
         wsQuotaManager.getNodeDataSize("/a/b");
         fail("Node data size should be unknown");
      }
      catch (UnknownQuotaDataSizeException e)
      {
         // ok
      }

      root.addNode("a").addNode("b");
      root.save();
      
      wsQuotaManager.setNodeQuota("/a/b", 1000, false);

      waitCalculationNodeDataSize(wsQuotaManager, "/a/b");
      assertEquals(wsQuotaManager.getNodeDataSize("/a/b"), wsQuotaManager.getNodeDataSizeDirectly("/a/b"));

      root.getNode("a").remove();
      root.save();

      assertTrue(wsQuotaManager.getNodeDataSize("/a/b") == 0);

      wsQuotaManager.removeNodeQuota("/a/b");
   }

   /**
    * Checks if data size of moved node equals to zero.
    */
   public void testDataSizeMovedNode() throws Exception
   {
      try
      {
         wsQuotaManager.getNodeDataSize("/a/b");
         fail("Node data size should be unknown");
      }
      catch (UnknownQuotaDataSizeException e)
      {
         // ok
      }

      root.addNode("a").addNode("b");
      root.save();

      wsQuotaManager.setNodeQuota("/a/b", 1000, false);

      waitCalculationNodeDataSize(wsQuotaManager, "/a/b");
      assertEquals(wsQuotaManager.getNodeDataSize("/a/b"), wsQuotaManager.getNodeDataSizeDirectly("/a/b"));

      session.move("/a/b", "/a/c");
      root.save();

      assertTrue(wsQuotaManager.getNodeDataSize("/a/b") == 0);

      wsQuotaManager.removeNodeQuota("/a/b");
   }

   /**
    * Checks if data size of moved node equals to zero.
    */
   public void testDataSizeMovedNodeNoDescendentEvents() throws Exception
   {
      TesterConfigurationHelper helper = TesterConfigurationHelper.getInstance();
      WorkspaceEntry wsEntry = helper.createWorkspaceEntry(DatabaseStructureType.ISOLATED, null);
      wsEntry.getContainer().getParameters()
         .add(new SimpleParameterEntry(WorkspaceDataContainer.TRIGGER_EVENTS_FOR_DESCENDENTS_ON_RENAME, "false"));

      ManageableRepository repository = helper.createRepository(container, DatabaseStructureType.ISOLATED, null);
      helper.addWorkspace(repository, wsEntry);

      SessionImpl session = (SessionImpl)repository.login(credentials, wsEntry.getName());

      Node root = session.getRootNode();
      WorkspaceQuotaManager wsQuotaManager =
         (WorkspaceQuotaManager)repository.getWorkspaceContainer(wsEntry.getName()).getComponent(
            WorkspaceQuotaManager.class);

      try
      {
         wsQuotaManager.getNodeDataSize("/a/b");
         fail("Node data size should be unknown");
      }
      catch (UnknownQuotaDataSizeException e)
      {
         // ok
      }

      try
      {
         wsQuotaManager.getNodeDataSize("/a/c");
         fail("Node data size should be unknown");
      }
      catch (UnknownQuotaDataSizeException e)
      {
         // ok
      }

      root.addNode("a").addNode("b");
      root.save();

      wsQuotaManager.setNodeQuota("/a/b", 1000, false);
      wsQuotaManager.setNodeQuota("/a/c", 1000, false);

      waitCalculationNodeDataSize(wsQuotaManager, "/a/b");

      assertTrue(wsQuotaManager.getNodeDataSize("/a/b") > 0);

      session.move("/a/b", "/a/c");
      root.getNode("a").getNode("c").addNode("d");
      root.save();

      waitCalculationNodeDataSize(wsQuotaManager, "/a/c");

      assertEquals(wsQuotaManager.getNodeDataSize("/a/c"), wsQuotaManager.getNodeDataSizeDirectly("/a/c"));
      try
      {
         wsQuotaManager.getNodeDataSize("/a/b");
         fail("Node data size should be unknown");
      }
      catch (UnknownQuotaDataSizeException e)
      {
         // ok
      }

      wsQuotaManager.removeNodeQuota("/a/b");
      wsQuotaManager.removeNodeQuota("/a/c");
   }
}
