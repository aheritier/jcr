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
import org.exoplatform.services.jcr.impl.RepositoryContainer;
import org.exoplatform.services.jcr.impl.core.JCRPath;
import org.exoplatform.services.jcr.impl.core.PropertyImpl;
import org.exoplatform.services.jcr.impl.core.SessionImpl;
import org.exoplatform.services.jcr.impl.storage.jdbc.JDBCDataContainerConfig.DatabaseStructureType;
import org.exoplatform.services.jcr.impl.util.io.DirectoryHelper;
import org.exoplatform.services.jcr.storage.WorkspaceDataContainer;
import org.exoplatform.services.jcr.util.TesterConfigurationHelper;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.Iterator;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Value;

/**
 * @author <a href="abazko@exoplatform.com">Anatoliy Bazko</a>
 * @version $Id: TestQuotas.java 34360 2009-07-22 23:58:59Z tolusha $
 */
public class TestQuotas extends AbstractQuotaManagerTest
{

   /**
    * Check the index size of non system workspace.
    */
   public void testWorkspaceIndexSize() throws Exception
   {
      long measuredSize = ws1QuotaManager.getWorkspaceIndexSize();
      long exptectedSize = DirectoryHelper.getSize(new File("target/temp/index/db1/ws1"));

      assertEquals(exptectedSize, measuredSize);
   }

   /**
    * Check the index size of system workspace.
    */
   public void testSystemWorkspaceIndexSize() throws Exception
   {
      long measuredSize = wsQuotaManager.getWorkspaceIndexSize();

      long exptectedSize = DirectoryHelper.getSize(new File("target/temp/index/db1/ws"));
      exptectedSize += DirectoryHelper.getSize(new File("target/temp/index/db1/ws_system"));

      assertEquals(exptectedSize, measuredSize);
   }

   /**
    * Checks if workspace data size is positive value.
    */
   public void testWorkspaceDataSize() throws Exception
   {
      long measuredSize = wsQuotaManager.getWorkspaceDataSize();

      assertTrue(measuredSize > 0);
   }

   /**
    * Checks if data size of root node is equals to data size of workspace.
    */
   public void testRootNodeDataSize() throws Exception
   {
      assertEquals(wsQuotaManager.getWorkspaceDataSize(), wsQuotaManager.getNodeDataSize(JCRPath.ROOT_PATH));
   }

   /**
    * Checks if node data size returns correct value.
    */
   public void testNodeDataSize() throws Exception
   {
      Node node = session.getRootNode().addNode("test");
      session.save();

      long measuredSize = wsQuotaManager.getNodeDataSizeDirectly("/test");
      long expectedSize = ((ByteArrayInputStream)node.getProperty("jcr:primaryType").getStream()).available();

      assertEquals(expectedSize, measuredSize);
   }

   /**
    * Checks if node data size returns correct value.
    */
   public void testNodeDataSizeFromValueStorage() throws Exception
   {
      Node node = session.getRootNode().addNode("test");
      node.setProperty("value", new FileInputStream(createBLOBTempFile(1000)));
      session.save();

      long measuredSize = wsQuotaManager.getNodeDataSizeDirectly("/test");

      long expectedSize = ((ByteArrayInputStream)node.getProperty("jcr:primaryType").getStream()).available();
      expectedSize += node.getProperty("value").getStream().available();

      assertEquals(expectedSize, measuredSize);
   }

   /**
    * Checks if workspace data size is equals to size of all children nodes. 
    */
   public void testWorkspaceSizeEqualToAllNodesSize() throws Exception
   {
      long workspaceSize = wsQuotaManager.getWorkspaceDataSizeDirectly();
      long nodesSize = 0;

      Iterator<Node> nodes = session.getRootNode().getNodes();
      while (nodes.hasNext())
      {
         nodesSize += wsQuotaManager.getNodeDataSizeDirectly(nodes.next().getPath());
      }

      Iterator<Property> props = session.getRootNode().getProperties();
      while (props.hasNext())
      {
         PropertyImpl prop = (PropertyImpl)props.next();
         if (!prop.isMultiValued())
         {
            Value value = prop.getValue();
            nodesSize += ((ByteArrayInputStream)value.getStream()).available();
         }
         else
         {
            for (Value value : prop.getValues())
            {
               nodesSize += ((ByteArrayInputStream)value.getStream()).available();
            }
         }
      }

      assertEquals(nodesSize, workspaceSize);
   }

   /**
    * Checks if data size of removed node equals to zero.
    */
   public void testDataSizeRemovedNode() throws Exception
   {
      assertTrue(dataSizeShouldNotExists(wsQuotaManager, "/a/b"));

      root.addNode("a").addNode("b");
      root.save();
      
      wsQuotaManager.setNodeQuota("/a/b", 1000, false);
      assertNodeDataSize(wsQuotaManager, "/a/b");

      root.getNode("a").remove();
      root.save();

      assertNodeDataSize(wsQuotaManager, "/a/b");

      wsQuotaManager.removeNodeQuota("/a/b");
   }

   /**
    * Checks if data size of moved node equals to zero.
    */
   public void testDataSizeMovedNode() throws Exception
   {
      assertTrue(dataSizeShouldNotExists(wsQuotaManager, "/a/b"));

      root.addNode("a").addNode("b");
      root.save();

      wsQuotaManager.setNodeQuota("/a/b", 1000, false);
      assertNodeDataSize(wsQuotaManager, "/a/b");

      session.move("/a/b", "/a/c");
      root.save();

      assertNodeDataSize(wsQuotaManager, "/a/b");

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

      assertTrue(dataSizeShouldNotExists(wsQuotaManager, "/a/b"));
      assertTrue(dataSizeShouldNotExists(wsQuotaManager, "/a/c"));

      root.addNode("a").addNode("b");
      root.save();

      wsQuotaManager.setNodeQuota("/a/b", 1000, false);
      wsQuotaManager.setNodeQuota("/a/c", 1000, false);

      waitTasksTermination(wsQuotaManager);
      waitTasksTermination(wsQuotaManager);

      assertTrue(wsQuotaManager.getNodeDataSize("/a/b") > 0);

      session.move("/a/b", "/a/c");
      root.getNode("a").getNode("c").addNode("d");
      root.save();

      waitTasksTermination(wsQuotaManager);

      try
      {
         assertEquals(wsQuotaManager.getNodeDataSize("/a/c"), wsQuotaManager.getNodeDataSizeDirectly("/a/c"));
      }
      catch (UnknownDataSizeException e)
      {
         // may happen
      }

      assertTrue(dataSizeShouldNotExists(wsQuotaManager, "/a/b"));

      wsQuotaManager.removeNodeQuota("/a/b");
      wsQuotaManager.removeNodeQuota("/a/c");
   }

   /**
    * Testing setNodeQuota.
    */
   public void testSetNodeQuota() throws Exception
   {
      Node testA = root.addNode("a");
      Node testB = testA.addNode("b");
      root.save();

      assertTrue(quotaShouldNotExists(wsQuotaManager, "/a"));
      assertTrue(quotaShouldNotExists(wsQuotaManager, "/a/b"));

      wsQuotaManager.setNodeQuota("/a", 20000, false);
      wsQuotaManager.setNodeQuota("/a/b", 1000, false);

      assertNodeDataSize(wsQuotaManager, "/a/b");
      assertNodeDataSize(wsQuotaManager, "/a");

      // small changes
      testA.setProperty("prop1", "value1");
      testA.save();

      // big changes NOT OK for B
      testB.setProperty("prop1", new FileInputStream(createBLOBTempFile(5)));
      try
      {
         root.save();
         fail();
      }
      catch (RepositoryException e)
      {
      }

      session.refresh(false);

      // big changes OK for A
      testA.setProperty("prop2", new FileInputStream(createBLOBTempFile(5)));
      root.save();

      // increase quota
      wsQuotaManager.setNodeQuota("/a/b", 10000, false);

      // big changes OK for B now
      testB.setProperty("prop1", new FileInputStream(createBLOBTempFile(5)));
      root.save();

      wsQuotaManager.setNodeQuota("/a/b", 10, false);

      // should be possible to remove content
      testB.getProperty("prop1").remove();
      root.save();

      wsQuotaManager.removeNodeQuota("/a");
      wsQuotaManager.removeNodeQuota("/a/b");

      assertTrue(quotaShouldNotExists(wsQuotaManager, "/a"));
      assertTrue(dataSizeShouldNotExists(wsQuotaManager, "/a"));
      assertTrue(quotaShouldNotExists(wsQuotaManager, "/a/b"));
      assertTrue(dataSizeShouldNotExists(wsQuotaManager, "/a/b"));
   }

   /**
    * Testing setGroupOfNodeQuota.
    */
   public void testSetGroupOfNodeQuota() throws Exception
   {
      Node testA = root.addNode("a");
      Node testB = root.addNode("b");
      root.save();

      assertTrue(quotaShouldNotExists(wsQuotaManager, "/a"));
      assertTrue(quotaShouldNotExists(wsQuotaManager, "/b"));

      wsQuotaManager.setGroupOfNodesQuota("/*", 1000, false);
      wsQuotaManager.setNodeQuota("/b", 10000, false);

      assertTrue(dataSizeShouldNotExists(wsQuotaManager, "/a"));
      assertNodeDataSize(wsQuotaManager, "/b");

      // small changes
      testA.setProperty("prop1", "value1");
      root.save();

      assertNodeDataSize(wsQuotaManager, "/a");

      // big changes OK for B
      testB.setProperty("prop1", new FileInputStream(createBLOBTempFile(5)));
      root.save();

      // big changes NOT OK for A
      testA.setProperty("prop1", new FileInputStream(createBLOBTempFile(5)));
      try
      {
         root.save();
         fail();
      }
      catch (RepositoryException e)
      {
      }

      session.refresh(false);

      // increase quota
      wsQuotaManager.setGroupOfNodesQuota("/*", 10000, false);

      // big changes OK for A now
      testA.setProperty("prop1", new FileInputStream(createBLOBTempFile(5)));
      root.save();

      wsQuotaManager.removeGroupOfNodesQuota("/*");
      assertTrue(quotaShouldNotExists(wsQuotaManager, "/a"));
      assertTrue(dataSizeShouldNotExists(wsQuotaManager, "/a"));
      
      assertTrue(wsQuotaManager.getNodeQuota("/b") > 0);
      assertTrue(wsQuotaManager.getNodeDataSize("/b") > 0);

      wsQuotaManager.removeNodeQuota("/b");
      assertTrue(quotaShouldNotExists(wsQuotaManager, "/b"));
      assertTrue(dataSizeShouldNotExists(wsQuotaManager, "/b"));
   }

   /**
    * Testing setWorkspaceQuota method.
    */
   public void testSetWorkspaceQuota() throws Exception
   {
      assertWorkspaceSize(wsQuotaManager);

      root.addNode("fakeQuotaNode");
      root.save();

      wsQuotaManager.setWorkspaceQuota(10);

      root.addNode("testQuotaNode");
      try
      {
         root.save();
         fail();
      }
      catch (RepositoryException e)
      {
      }

      wsQuotaManager.setWorkspaceQuota(1000000);

      root.addNode("testQuotaNode");
      root.save();

      wsQuotaManager.setWorkspaceQuota(1000);

      assertWorkspaceSize(wsQuotaManager);

      // should possible to remove 
      root.getNode("testQuotaNode").remove();
      root.save();

      wsQuotaManager.removeWorkspaceQuota();

      assertTrue(wsQuotaManager.getWorkspaceDataSize() > 0);
   }

   /**
    * Testing setRepositoryQuota method.
    */
   public void testSetRepositoryQuota() throws Exception
   {
      assertWorkspaceSize(wsQuotaManager);

      assertTrue(dbQuotaManager.getRepositoryDataSize() > 0);

      root.addNode("fakeQuotaNode");
      root.save();

      dbQuotaManager.setRepositoryQuota(10);

      root.addNode("testQuotaNode");
      try
      {
         root.save();
         fail();
      }
      catch (RepositoryException e)
      {
      }

      dbQuotaManager.setRepositoryQuota(1000000);

      root.addNode("testQuotaNode");
      root.save();

      dbQuotaManager.setRepositoryQuota(1000);

      assertWorkspaceSize(wsQuotaManager);

      // should possible to remove 
      root.getNode("testQuotaNode").remove();
      root.save();

      dbQuotaManager.removeRepositoryQuota();
      assertTrue(dbQuotaManager.getRepositoryDataSize() > 0);
   }

   /**
    * Testing setGlobalQuota method.
    */
   public void testGlobalQuota() throws Exception
   {
      assertWorkspaceSize(wsQuotaManager);

      assertTrue(quotaManager.getGlobalDataSize() > 0);

      root.addNode("fakeQuotaNode");
      root.save();

      quotaManager.setGlobalQuota(10);

      root.addNode("testQuotaNode");
      try
      {
         root.save();
         fail();
      }
      catch (RepositoryException e)
      {
      }

      quotaManager.setGlobalQuota(1000000);

      root.addNode("testQuotaNode");
      root.save();

      quotaManager.setGlobalQuota(1000);

      assertWorkspaceSize(wsQuotaManager);

      // should possible to remove 
      root.getNode("testQuotaNode").remove();
      root.save();

      quotaManager.removeGlobalQuota();
      assertTrue(quotaManager.getGlobalDataSize() > 0);
   }

   /**
    * Checks that quota is decreased when workspace is removed but not
    * when is just stopped. 
    */
   public void testQuotaWhenWorkspaceIsRemoved() throws Exception
   {
      ManageableRepository repository = helper.createRepository(container, DatabaseStructureType.MULTI, null);
      WorkspaceEntry ws1Entry = helper.createWorkspaceEntry(DatabaseStructureType.MULTI, null);
      WorkspaceEntry ws2Entry = helper.createWorkspaceEntry(DatabaseStructureType.MULTI, null);

      helper.addWorkspace(repository, ws1Entry);
      helper.addWorkspace(repository, ws2Entry);

      WorkspaceQuotaManager ws1QuotaManager =
         (WorkspaceQuotaManager)repository.getWorkspaceContainer(ws1Entry.getName()).getComponent(
            WorkspaceQuotaManager.class);

      long dataSize = quotaManager.getGlobalDataSize();
      long ws1DataSize = ws1QuotaManager.getWorkspaceDataSize();

      repository.removeWorkspace(ws1Entry.getName());
      assertEquals(dataSize - ws1DataSize, quotaManager.getGlobalDataSize());

      dataSize = quotaManager.getGlobalDataSize();

      RepositoryContainer repoContainer =
         (RepositoryContainer)container.getComponentInstance(repository.getConfiguration().getName());
      repoContainer.stop();

      assertEquals(dataSize, quotaManager.getGlobalDataSize());
   }
}
