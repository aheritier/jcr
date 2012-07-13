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
package org.exoplatform.services.jcr.impl.quota.jbosscache;

import org.exoplatform.services.jcr.impl.dataflow.serialization.ZipObjectReader;
import org.exoplatform.services.jcr.impl.dataflow.serialization.ZipObjectWriter;
import org.exoplatform.services.jcr.impl.quota.QuotaPersister;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.jboss.cache.Cache;
import org.jboss.cache.Fqn;

import java.io.EOFException;
import java.io.IOException;
import java.io.Serializable;
import java.util.Set;
import java.util.zip.ZipEntry;

/**
 * @author <a href="abazko@exoplatform.com">Anatoliy Bazko</a>
 * @version $Id: JBCBackupQuota.java 34360 2009-07-22 23:58:59Z tolusha $
 */
public class JBCBackupQuota
{
   /**
    * Logger.
    */
   protected final Log LOG = ExoLogger.getLogger("exo.jcr.component.core.JBCBackupQuota");

   /**
    * Cache.
    */
   private final Cache<Serializable, Object> cache;

   /**
    * BackupUtil constructor.
    */
   JBCBackupQuota(Cache<Serializable, Object> cache)
   {
      this.cache = cache;
   }

   /**
    * @see QuotaPersister#clearWorkspaceData(String, String)
    */
   public void clearWorkspaceData(String rName, String wsName)
   {
      Fqn<String> fqn = Fqn.fromRelativeElements(JBCQuotaPersister.DATA_SIZE, rName, wsName);
      cache.removeNode(fqn);

      fqn = Fqn.fromRelativeElements(JBCQuotaPersister.QUOTA, rName, wsName);
      cache.removeNode(fqn);
   }

   /**
    * @see QuotaPersister#backupWorkspaceData(String, String, ZipObjectWriter)
    * @throws IOException 
    */
   public void backupWorkspaceData(String rName, String wsName, ZipObjectWriter writer) throws IOException
   {
      backupWorkspaceDataSize(rName, wsName, writer);
      backupWorkspaceQuotaLimit(rName, wsName, writer);
      backupWorkspaceNodesDataSize(rName, wsName, writer);
      backupWorkspaceNodesQuotaPatterns(rName, wsName, writer);
      backupWorkspaceNodesQuotaPathes(rName, wsName, writer);
   }

   private void backupWorkspaceQuotaLimit(String rName, String wsName, ZipObjectWriter writer) throws IOException
   {
      writer.putNextEntry(new ZipEntry("workspace-quota-limit"));

      Fqn<String> fqn = Fqn.fromRelativeElements(JBCQuotaPersister.QUOTA, rName, wsName);
      Long quotaLimit = (Long)cache.get(fqn, JBCQuotaPersister.SIZE);
      if (quotaLimit != null)
      {
         writer.writeLong(quotaLimit);
      }

      writer.closeEntry();
   }

   private void backupWorkspaceDataSize(String rName, String wsName, ZipObjectWriter writer) throws IOException
   {
      writer.putNextEntry(new ZipEntry("workspace-data-size"));

      Fqn<String> fqn = Fqn.fromRelativeElements(JBCQuotaPersister.DATA_SIZE, rName, wsName);
      Long dataSize = (Long)cache.get(fqn, JBCQuotaPersister.SIZE);

      if (dataSize != null)
      {
         writer.writeLong(dataSize);
      }

      writer.closeEntry();
   }

   private void backupWorkspaceNodesDataSize(String rName, String wsName, ZipObjectWriter writer) throws IOException
   {
      writer.putNextEntry(new ZipEntry("workspace-nodes-data-size"));

      Fqn<String> parentFqn = Fqn.fromRelativeElements(JBCQuotaPersister.DATA_SIZE, rName, wsName);
      backupNodes(parentFqn, writer, false);

      writer.closeEntry();
   }

   private void backupWorkspaceNodesQuotaPatterns(String rName, String wsName, ZipObjectWriter writer)
      throws IOException
   {
      writer.putNextEntry(new ZipEntry("workspace-nodes-quota-patterns"));
      
      Fqn<String> parentFqn =
         Fqn.fromRelativeElements(JBCQuotaPersister.QUOTA, rName, wsName, JBCQuotaPersister.QUOTA_PATTERNS);
      backupNodes(parentFqn, writer, true);

      writer.closeEntry();
   }

   private void backupWorkspaceNodesQuotaPathes(String rName, String wsName, ZipObjectWriter writer) throws IOException
   {
      writer.putNextEntry(new ZipEntry("workspace-nodes-quota-paths"));

      Fqn<String> parentFqn =
         Fqn.fromRelativeElements(JBCQuotaPersister.QUOTA, rName, wsName, JBCQuotaPersister.QUOTA_PATHS);
      backupNodes(parentFqn, writer, true);

      writer.closeEntry();
   }

   private void backupNodes(Fqn<String> parentFqn, ZipObjectWriter writer, boolean asyncKeyInclusive)
      throws IOException
   {
      Set<Object> children = cache.getChildrenNames(parentFqn);
      writer.writeInt(children.size());

      for (Object path : children)
      {
         Fqn<String> nodeFqn = Fqn.fromRelativeElements(parentFqn, (String)path);

         Long dataSize = (Long)cache.get(nodeFqn, JBCQuotaPersister.SIZE);

         writer.writeString((String)path);
         writer.writeLong(dataSize);

         if (asyncKeyInclusive)
         {
            Boolean asyncUpdate = (Boolean)cache.get(nodeFqn, JBCQuotaPersister.ASYNC_UPATE);
            writer.writeBoolean(asyncUpdate);
         }
      }
   }

   /**
    * @see QuotaPersister#restoreWorkspaceData(String, String, ZipObjectReader)
    */
   public void restoreWorkspaceData(String rName, String wsName, ZipObjectReader reader) throws IOException
   {
      restoreWorkspaceDataSize(rName, wsName, reader);
      restoreWorkspaceQuotaLimit(rName, wsName, reader);
      restoreWorkspaceNodesDataSize(rName, wsName, reader);
      restoreWorkspaceNodesQuotaPatterns(rName, wsName, reader);
      restoreWorkspaceNodesQuotaPathes(rName, wsName, reader);
   }

   private void restoreWorkspaceNodesQuotaPathes(String rName, String wsName, ZipObjectReader reader)
      throws IOException
   {
      reader.getNextEntry();

      Fqn<String> base =
         Fqn.fromRelativeElements(JBCQuotaPersister.QUOTA, rName, wsName, JBCQuotaPersister.QUOTA_PATHS);
      restoreNodes(base, true, reader);
   }

   private void restoreWorkspaceNodesQuotaPatterns(String rName, String wsName, ZipObjectReader reader)
      throws IOException
   {
      reader.getNextEntry();

      Fqn<String> base =
         Fqn.fromRelativeElements(JBCQuotaPersister.QUOTA, rName, wsName, JBCQuotaPersister.QUOTA_PATTERNS);
      restoreNodes(base, true, reader);
   }

   private void restoreWorkspaceNodesDataSize(String rName, String wsName, ZipObjectReader reader) throws IOException
   {
      reader.getNextEntry();

      Fqn<String> base = Fqn.fromRelativeElements(JBCQuotaPersister.DATA_SIZE, rName, wsName);
      restoreNodes(base, false, reader);
   }

   private void restoreNodes(Fqn<String> base, boolean asyncKeyInclusive, ZipObjectReader reader) throws IOException
   {
      int count = reader.readInt();
      for (int i = 0; i < count; i++)
      {
         String nodePath = reader.readString();
         Long dataSize = reader.readLong();

         Fqn<String> fqn = Fqn.fromRelativeElements(base, nodePath);
         cache.put(fqn, JBCQuotaPersister.SIZE, dataSize);

         if (asyncKeyInclusive)
         {
            Boolean asyncUpdate = reader.readBoolean();
            cache.put(fqn, JBCQuotaPersister.ASYNC_UPATE, asyncUpdate);
         }
      }
   }

   private void restoreWorkspaceQuotaLimit(String rName, String wsName, ZipObjectReader reader) throws IOException
   {
      reader.getNextEntry();

      try
      {
         Long dataSize = reader.readLong();

         Fqn<String> fqn = Fqn.fromRelativeElements(JBCQuotaPersister.QUOTA, rName, wsName);
         cache.put(fqn, JBCQuotaPersister.SIZE, dataSize);
      }
      catch (EOFException e)
      {
         if (LOG.isTraceEnabled())
         {
            LOG.trace(e.getMessage(), e);
         }
      }
   }

   private void restoreWorkspaceDataSize(String rName, String wsName, ZipObjectReader reader) throws IOException
   {
      reader.getNextEntry();

      try
      {
         Long dataSize = reader.readLong();

         Fqn<String> fqn = Fqn.fromRelativeElements(JBCQuotaPersister.DATA_SIZE, rName, wsName);
         cache.put(fqn, JBCQuotaPersister.SIZE, dataSize);
      }
      catch (EOFException e)
      {
         if (LOG.isTraceEnabled())
         {
            LOG.trace(e.getMessage(), e);
         }
      }
   }
}
