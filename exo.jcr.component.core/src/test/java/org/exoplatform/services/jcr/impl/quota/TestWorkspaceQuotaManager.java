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

import org.exoplatform.services.jcr.impl.backup.DataRestore;
import org.exoplatform.services.jcr.impl.backup.rdbms.DataRestoreContext;
import org.exoplatform.services.jcr.impl.core.JCRPath;
import org.exoplatform.services.jcr.impl.core.PropertyImpl;
import org.exoplatform.services.jcr.impl.util.io.DirectoryHelper;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.Iterator;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.Value;

/**
 * @author <a href="abazko@exoplatform.com">Anatoliy Bazko</a>
 * @version $Id: TestWorkspaceQuotaManager.java 34360 2009-07-22 23:58:59Z tolusha $
 */
public class TestWorkspaceQuotaManager extends AbstractQuotaManagerTest
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
    * Backup operation.
    */
   public void testBackupRestoreClean() throws Exception
   {
      File tempDir = new File("target/temp");
      
      Node testRoot = root.addNode("testRoot");

      testRoot.addNode("test1").addNode("content");
      testRoot.addNode("test2").addNode("content").addNode("content");
      root.save();
      

      //      wsQuotaManager.setWorkspaceQuota(10000);
      wsQuotaManager.setNodeQuota("/testRoot/test1", 1000, false);
      wsQuotaManager.setNodeQuota("/testRoot/test2", 1000, false);
      wsQuotaManager.setGroupOfNodesQuota("/testRoot/*", 2000, true);

      wsQuotaManager.suspend(); // waits until all tasks are done
      wsQuotaManager.resume();

      wsQuotaManager.removeNodeQuota("/testRoot/test2");

      wsQuotaManager.suspend();
      assertTrue(wsQuotaManager.isSuspended());

      long repDataSize = dbQuotaManager.getRepositoryDataSize();
      long wsDataSize = wsQuotaManager.getWorkspaceDataSize();
      long node1DataSize = wsQuotaManager.getNodeDataSize("/testRoot/test1");
      long node2DataSize = wsQuotaManager.getNodeDataSize("/testRoot/test2");
      long node1Quota = wsQuotaManager.getNodeQuota("/testRoot/test1");
      long node2Quota = wsQuotaManager.getNodeQuota("/testRoot/test2");

      assertEquals(node1Quota, 1000);
      assertEquals(node2Quota, 2000);

      wsQuotaManager.backup(tempDir);

      DataRestoreContext context = new DataRestoreContext(new String[]{DataRestoreContext.STORAGE_DIR}, new Object[]{tempDir});
      DataRestore restorer = wsQuotaManager.getDataRestorer(context);

      restorer.clean();

      assertEquals(dbQuotaManager.getRepositoryDataSize(), repDataSize - wsDataSize);

      try
      {
         wsQuotaManager.getWorkspaceQuota();
         fail("Quota should be unknown after clean");
      }
      catch (UnknownQuotaLimitException e)
      {
      }

      try
      {
         wsQuotaManager.getWorkspaceDataSize();
         fail("Data size should be unknown after clean");
      }
      catch (UnknownQuotaDataSizeException e)
      {
      }

      try
      {
         wsQuotaManager.getNodeDataSize("/testRoot/test1");
         fail("Data size should be unknown after clean");
      }
      catch (UnknownQuotaDataSizeException e)
      {
      }

      try
      {
         wsQuotaManager.getNodeDataSize("/testRoot/test2");
         fail("Data size should be unknown after clean");
      }
      catch (UnknownQuotaDataSizeException e)
      {
      }

      try
      {
         wsQuotaManager.getNodeQuota("/testRoot/test1");
         fail("Quota should be unknown after clean");
      }
      catch (UnknownQuotaLimitException e)
      {
      }

      try
      {
         wsQuotaManager.getNodeQuota("/testRoot/test2");
         fail("Quota should be unknown after clean");
      }
      catch (UnknownQuotaLimitException e)
      {
      }

      restorer.rollback();

      assertEquals(dbQuotaManager.getRepositoryDataSize(), repDataSize);
      assertEquals(wsDataSize, wsQuotaManager.getWorkspaceDataSize());
      assertEquals(node1DataSize, wsQuotaManager.getNodeDataSize("/testRoot/test1"));
      assertEquals(node2DataSize, wsQuotaManager.getNodeDataSize("/testRoot/test2"));
      assertEquals(node1Quota, wsQuotaManager.getNodeQuota("/testRoot/test1"));
      assertEquals(node2Quota, wsQuotaManager.getNodeQuota("/testRoot/test2"));
      
      restorer.clean();
      restorer.restore();

      assertEquals(dbQuotaManager.getRepositoryDataSize(), repDataSize);
      assertEquals(wsDataSize, wsQuotaManager.getWorkspaceDataSize());
      assertEquals(node1DataSize, wsQuotaManager.getNodeDataSize("/testRoot/test1"));
      assertEquals(node2DataSize, wsQuotaManager.getNodeDataSize("/testRoot/test2"));
      assertEquals(node1Quota, wsQuotaManager.getNodeQuota("/testRoot/test1"));
      assertEquals(node2Quota, wsQuotaManager.getNodeQuota("/testRoot/test2"));

      restorer.commit();
      restorer.close();

      wsQuotaManager.resume();
      assertFalse(wsQuotaManager.isSuspended());
   }
}
