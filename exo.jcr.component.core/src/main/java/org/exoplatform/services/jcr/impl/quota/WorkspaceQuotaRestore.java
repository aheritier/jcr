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

import org.exoplatform.commons.utils.PrivilegedFileHelper;
import org.exoplatform.commons.utils.PrivilegedSystemHelper;
import org.exoplatform.services.jcr.impl.backup.BackupException;
import org.exoplatform.services.jcr.impl.backup.Backupable;
import org.exoplatform.services.jcr.impl.backup.DataRestore;
import org.exoplatform.services.jcr.impl.backup.rdbms.DBBackup;
import org.exoplatform.services.jcr.impl.backup.rdbms.DataRestoreContext;
import org.exoplatform.services.jcr.impl.dataflow.serialization.ZipObjectReader;
import org.exoplatform.services.jcr.impl.dataflow.serialization.ZipObjectWriter;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.zip.ZipInputStream;

/**
 * {@link DataRestore} implementation for quota.
 * 
 * @author <a href="abazko@exoplatform.com">Anatoliy Bazko</a>
 * @version $Id: WorkspaceQuotaRestore.java Aug 13, 2012 tolusha $
 */
public class WorkspaceQuotaRestore implements DataRestore
{

   /**
    * Logger.
    */
   protected final Log LOG = ExoLogger.getLogger("exo.jcr.component.core.WorkspaceQuotaRestore");

   /**
    * File name for backuped data.
    */
   protected static final String BACKUP_FILE_NAME = "quota";

   private final File tempFile;

   private final File backupFile;

   /**
    * {@link WorkspaceQuotaManager} instance.
    */
   private final WorkspaceQuotaManager wqm;

   /**
    * WorkspaceQuotaRestore constructor.
    */
   WorkspaceQuotaRestore(WorkspaceQuotaManager wqm, DataRestoreContext context)
   {
      this(wqm, (File)context.getObject(DataRestoreContext.STORAGE_DIR));
   }

   /**
    * WorkspaceQuotaRestore constructor.
    */
   WorkspaceQuotaRestore(WorkspaceQuotaManager wqm, File storageDir)
   {
      this.wqm = wqm;
      this.backupFile = new File(storageDir, BACKUP_FILE_NAME + DBBackup.CONTENT_FILE_SUFFIX);

      File tempDir = new File(PrivilegedSystemHelper.getProperty("java.io.tmpdir"));
      this.tempFile = new File(tempDir, "temp.dump");
   }

   /**
    * {@inheritDoc}
    */
   public void clean() throws BackupException
   {
      doBackup(tempFile);
      doClean();
   }

   /**
    * {@inheritDoc}
    */
   public void restore() throws BackupException
   {
      doRestore(backupFile);
   }

   /**
    * {@inheritDoc}
    */
   public void commit() throws BackupException
   {
   }

   /**
    * {@inheritDoc}
    */
   public void rollback() throws BackupException
   {
      doClean();
      doRestore(tempFile);
   }

   /**
    * {@inheritDoc}
    */
   public void close() throws BackupException
   {
      PrivilegedFileHelper.delete(tempFile);
   }

   /**
    * {@link Backupable#backup(File)}
    */
   public void backup() throws BackupException
   {
      doBackup(backupFile);
   }

   /**
    * Restores content.
    */
   protected void doRestore(File backupFile) throws BackupException
   {
      if (!backupFile.exists())
      {
         LOG.warn("Nothing to restore for quotas");
         return;
      }

      ZipObjectReader in = null;
      try
      {
         in = new ZipObjectReader(new ZipInputStream(new FileInputStream(backupFile)));
         wqm.quotaPersister.restoreWorkspaceData(wqm.rName, wqm.wsName, in);
      }
      catch (IOException e)
      {
         throw new BackupException(e);
      }
      finally
      {
         if (in != null)
         {
            try
            {
               in.close();
            }
            catch (IOException e)
            {
               LOG.error("Can't close input stream", e);
            }
         }
      }

      try
      {
         long dataSize = wqm.quotaPersister.getWorkspaceDataSize(wqm.rName, wqm.wsName);
         wqm.repositoryQuotaManager.accumulatePersistedChanges(dataSize);
      }
      catch (UnknownDataSizeException e)
      {
         if (LOG.isTraceEnabled())
         {
            LOG.trace(e.getMessage(), e);
         }
      }
   }

   /**
    * Backups data to define file.
    */
   protected void doBackup(File backupFile) throws BackupException
   {
      ZipObjectWriter out = null;
      try
      {
         out = new ZipObjectWriter(PrivilegedFileHelper.zipOutputStream(backupFile));
         wqm.quotaPersister.backupWorkspaceData(wqm.rName, wqm.wsName, out);
      }
      catch (IOException e)
      {
         throw new BackupException(e);
      }
      finally
      {
         if (out != null)
         {
            try
            {
               out.close();
            }
            catch (IOException e)
            {
               LOG.error("Can't close output stream", e);
            }
         }
      }
   }

   protected void doClean() throws BackupException
   {
      try
      {
         long dataSize = wqm.quotaPersister.getWorkspaceDataSize(wqm.rName, wqm.wsName);
         wqm.repositoryQuotaManager.accumulatePersistedChanges(-dataSize);
      }
      catch (UnknownDataSizeException e)
      {
         if (LOG.isTraceEnabled())
         {
            LOG.trace(e.getMessage(), e);
         }
      }

      wqm.quotaPersister.clearWorkspaceData(wqm.rName, wqm.wsName);
   }
}

