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
import org.exoplatform.services.jcr.config.RepositoryEntry;
import org.exoplatform.services.jcr.config.WorkspaceEntry;
import org.exoplatform.services.jcr.impl.backup.BackupException;
import org.exoplatform.services.jcr.impl.backup.Backupable;
import org.exoplatform.services.jcr.impl.backup.DataRestore;
import org.exoplatform.services.jcr.impl.backup.ResumeException;
import org.exoplatform.services.jcr.impl.backup.SuspendException;
import org.exoplatform.services.jcr.impl.backup.Suspendable;
import org.exoplatform.services.jcr.impl.backup.rdbms.DBBackup;
import org.exoplatform.services.jcr.impl.backup.rdbms.DataRestoreContext;
import org.exoplatform.services.jcr.impl.core.RepositoryImpl;
import org.exoplatform.services.jcr.impl.dataflow.persistent.WorkspacePersistentDataManager;
import org.exoplatform.services.jcr.impl.dataflow.serialization.ZipObjectReader;
import org.exoplatform.services.jcr.impl.dataflow.serialization.ZipObjectWriter;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author <a href="abazko@exoplatform.com">Anatoliy Bazko</a>
 * @version $Id: BackupableWorkspaceQuotaManager.java 34360 2009-07-22 23:58:59Z tolusha $
 */
public abstract class BackupableWorkspaceQuotaManager extends BaseWorkspaceQuotaManager implements Backupable,
   Suspendable
{

   /**
    * File name for backuped data.
    */
   protected static final String BACKUP_FILE_NAME = "quota";

   /**
    * Indicates if component suspended or not.
    */
   protected AtomicBoolean isSuspended = new AtomicBoolean();

   /**
    * BaseWorkspaceQuotaManager constructor.
    */
   public BackupableWorkspaceQuotaManager(RepositoryImpl repository, RepositoryQuotaManager rQuotaManager,
      RepositoryEntry rEntry, WorkspaceEntry wsEntry, WorkspacePersistentDataManager dataManager)
   {
      super(repository, rQuotaManager, rEntry, wsEntry, dataManager);
   }

   /**
    * {@inheritDoc}
    */
   public void backup(File storageDir) throws BackupException
   {
      File backupFile = new File(storageDir, BACKUP_FILE_NAME + DBBackup.CONTENT_FILE_SUFFIX);
      doBackup(backupFile);
   }

   /**
    * {@inheritDoc}
    */
   public void clean() throws BackupException
   {
      doClean();
   }

   /**
    * {@inheritDoc}
    */
   public DataRestore getDataRestorer(DataRestoreContext context) throws BackupException
   {
      return new WorkspaceQuotaRestore(context);
   }

   /**
    * {@link DataRestore} implementation for quota. 
    */
   private class WorkspaceQuotaRestore implements DataRestore
   {

      private final File tempFile;

      private final File backupFile;

      /**
       * WorkspaceQuotaRestore constructor.
       */
      WorkspaceQuotaRestore(DataRestoreContext context)
      {
         File storageDir = (File)context.getObject(DataRestoreContext.STORAGE_DIR);
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
   }

   /**
    * Restores content.
    */
   private void doRestore(File backupFile) throws BackupException
   {
      ZipObjectReader in = null;
      try
      {
         in = new ZipObjectReader(PrivilegedFileHelper.zipInputStream(backupFile));
         quotaPersister.restoreWorkspaceData(rName, wsName, in);
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
         long dataSize = quotaPersister.getWorkspaceDataSize(rName, wsName);
         repositoryQuotaManager.accumulateChanges(dataSize);
      }
      catch (UnknownQuotaDataSizeException e)
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
   private void doBackup(File backupFile) throws BackupException
   {
      ZipObjectWriter out = null;
      try
      {
         out = new ZipObjectWriter(PrivilegedFileHelper.zipOutputStream(backupFile));
         quotaPersister.backupWorkspaceData(rName, wsName, out);
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

   private void doClean() throws BackupException
   {
      try
      {
         long dataSize = quotaPersister.getWorkspaceDataSize(rName, wsName);
         repositoryQuotaManager.accumulateChanges(-dataSize);
      }
      catch (UnknownQuotaDataSizeException e)
      {
         if (LOG.isTraceEnabled())
         {
            LOG.trace(e.getMessage(), e);
         }
      }

      quotaPersister.clearWorkspaceData(rName, wsName);
   }

   /**
    * {@inheritDoc}
    */
   public void suspend() throws SuspendException
   {
      executor.shutdownNow();
      isSuspended.set(true);
   }

   /**
    * {@inheritDoc}
    */
   public void resume() throws ResumeException
   {
      initExecutorService();
      isSuspended.set(false);
   }

   /**
    * {@inheritDoc}
    */
   public boolean isSuspended()
   {
      return isSuspended.get();
   }

   /**
    * {@inheritDoc}
    */
   public int getPriority()
   {
      return Suspendable.PRIORITY_LOW;
   }
}
