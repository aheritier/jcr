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
public class TestWorkspaceQuotaManager extends JcrAPIBaseTest
{
   /**
    * Check the index size of non system workspace.
    */
   public void testWorkspaceIndexSize() throws Exception
   {
      WorkspaceQuotaManager wQuotaManager =
         (WorkspaceQuotaManager)repository.getWorkspaceContainer("ws1").getComponent(WorkspaceQuotaManager.class);

      long measuredSize = wQuotaManager.getWorkspaceIndexSize();
      long exptectedSize = DirectoryHelper.getSize(new File("target/temp/index/db1/ws1"));

      assertEquals(exptectedSize, measuredSize);
   }

   /**
    * Check the index size of system workspace.
    */
   public void testSystemWorkspaceIndexSize() throws Exception
   {
      WorkspaceQuotaManager wQuotaManager =
         (WorkspaceQuotaManager)repository.getWorkspaceContainer("ws").getComponent(WorkspaceQuotaManager.class);

      long measuredSize = wQuotaManager.getWorkspaceIndexSize();

      long exptectedSize = DirectoryHelper.getSize(new File("target/temp/index/db1/ws"));
      exptectedSize += DirectoryHelper.getSize(new File("target/temp/index/db1/ws_system"));

      assertEquals(exptectedSize, measuredSize);
   }

   /**
    * Checks if workspace data size is positive value.
    */
   public void testWorkspaceDataSize() throws Exception
   {
      WorkspaceQuotaManager wQuotaManager =
         (WorkspaceQuotaManager)repository.getWorkspaceContainer("ws").getComponent(WorkspaceQuotaManager.class);

      long measuredSize = wQuotaManager.getWorkspaceDataSize();

      assertTrue(measuredSize > 0);
   }

   /**
    * Checks if data size of root node is equals to data size of workspace.
    */
   public void testRootNodeDataSize() throws Exception
   {
      WorkspaceQuotaManager wQuotaManager =
         (WorkspaceQuotaManager)repository.getWorkspaceContainer("ws").getComponent(WorkspaceQuotaManager.class);

      assertEquals(wQuotaManager.getWorkspaceDataSize(), wQuotaManager.getNodeDataSize(JCRPath.ROOT_PATH));
   }

   /**
    * Checks if node data size returns correct value.
    */
   public void testNodeDataSize() throws Exception
   {
      Node node = session.getRootNode().addNode("test");
      session.save();

      WorkspaceQuotaManager wQuotaManager =
         (WorkspaceQuotaManager)repository.getWorkspaceContainer("ws").getComponent(WorkspaceQuotaManager.class);

      long measuredSize =  wQuotaManager.getNodeDataSize("/test");
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

      WorkspaceQuotaManager wQuotaManager =
         (WorkspaceQuotaManager)repository.getWorkspaceContainer("ws").getComponent(WorkspaceQuotaManager.class);

      long measuredSize = wQuotaManager.getNodeDataSize("/test");

      long expectedSize = ((ByteArrayInputStream)node.getProperty("jcr:primaryType").getStream()).available();
      expectedSize += node.getProperty("value").getStream().available();

      assertEquals(expectedSize, measuredSize);
   }

   /**
    * Checks if workspace data size is equals to size of all children nodes. 
    */
   public void testWorkspaceSizeEqualToAllNodesSize() throws Exception
   {
      WorkspaceQuotaManager wQuotaManager =
         (WorkspaceQuotaManager)repository.getWorkspaceContainer("ws").getComponent(WorkspaceQuotaManager.class);

      long workspaceSize = wQuotaManager.getWorkspaceDataSizeDirectly();
      long nodesSize = 0;
      
      Iterator<Node> nodes = session.getRootNode().getNodes();
      while (nodes.hasNext())
      {
         nodesSize += wQuotaManager.getNodeDataSizeDirectly(nodes.next().getPath());
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
}
