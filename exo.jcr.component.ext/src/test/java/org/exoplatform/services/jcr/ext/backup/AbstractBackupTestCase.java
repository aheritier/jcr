/*
 * Copyright (C) 2009 eXo Platform SAS.
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
package org.exoplatform.services.jcr.ext.backup;

import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.PropertiesParam;
import org.exoplatform.services.jcr.config.ContainerEntry;
import org.exoplatform.services.jcr.config.QueryHandlerEntry;
import org.exoplatform.services.jcr.config.QueryHandlerParams;
import org.exoplatform.services.jcr.config.RepositoryConfigurationException;
import org.exoplatform.services.jcr.config.RepositoryEntry;
import org.exoplatform.services.jcr.config.SimpleParameterEntry;
import org.exoplatform.services.jcr.config.ValueStorageEntry;
import org.exoplatform.services.jcr.config.ValueStorageFilterEntry;
import org.exoplatform.services.jcr.config.WorkspaceEntry;
import org.exoplatform.services.jcr.core.ManageableRepository;
import org.exoplatform.services.jcr.core.WorkspaceContainerFacade;
import org.exoplatform.services.jcr.ext.BaseStandaloneTest;
import org.exoplatform.services.jcr.ext.backup.impl.BackupManagerImpl;
import org.exoplatform.services.jcr.ext.backup.impl.JobRepositoryRestore;
import org.exoplatform.services.jcr.ext.backup.impl.JobWorkspaceRestore;
import org.exoplatform.services.jcr.impl.clean.rdbms.DBCleanService;
import org.exoplatform.services.jcr.impl.core.RepositoryImpl;
import org.exoplatform.services.jcr.impl.core.SessionImpl;
import org.exoplatform.services.jcr.impl.core.SessionRegistry;
import org.exoplatform.services.jcr.impl.core.query.SystemSearchManager;
import org.exoplatform.services.jcr.impl.storage.jdbc.JDBCDataContainerConfig.DatabaseStructureType;
import org.exoplatform.services.jcr.impl.storage.value.fs.FileValueStorage;
import org.exoplatform.services.jcr.impl.util.io.DirectoryHelper;
import org.exoplatform.services.jcr.util.TesterConfigurationHelper;
import org.exoplatform.services.rest.ContainerResponseWriter;
import org.exoplatform.services.rest.impl.ContainerResponse;
import org.exoplatform.services.rest.tools.ByteArrayContainerResponseWriter;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;

import javax.jcr.ItemExistsException;
import javax.jcr.Node;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.ValueFormatException;
import javax.jcr.lock.LockException;
import javax.jcr.nodetype.ConstraintViolationException;
import javax.jcr.version.VersionException;

/**
 * Created by The eXo Platform SAS Author : Peter Nedonosko peter.nedonosko@exoplatform.com.ua
 * 04.02.2008
 * 
 * @author <a href="mailto:peter.nedonosko@exoplatform.com.ua">Peter Nedonosko</a>
 * @version $Id: AbstractBackupTestCase.java 760 2008-02-07 15:08:07Z pnedonosko $
 */
public abstract class AbstractBackupTestCase extends BaseStandaloneTest
{

   protected TesterConfigurationHelper helper = TesterConfigurationHelper.getInstance();

   protected File blob;

   protected ExtendedBackupManager backup;

   protected SessionImpl ws1Session;

   protected Node ws1TestRoot;

   protected String repositoryNameToBackup = "db7";

   protected String workspaceNameToBackup = "ws1";

   protected String dataSourceToWorkspaceRestore = "jdbcjcr_workspace_restore";

   protected String dataSourceToRepositoryRestore = "jdbcjcr_to_repository_restore";
   
   protected String dataSourceToRepositoryRestoreSingleDB = "jdbcjcr_to_repository_restore_singel_db";
   
   protected String repositoryNameToBackupSingleDB = "db7";

   protected String repositoryNameToRestore = "db8backup";

   protected String workspaceNameToRestore = "ws1backup";

   class LogFilter
      implements FileFilter
   {

      public boolean accept(File pathname)
      {
         return pathname.getName().startsWith("backup-") && pathname.getName().endsWith(".xml");
      }
   }

   /**
    * {@inheritDoc}
    */
   public void setUp() throws Exception
   {
      super.setUp();// this

      backup = getBackupManager();
      blob = createBLOBTempFile(300);
   }

   /**
    * {@inheritDoc}
    */
   protected void tearDown() throws Exception
   {
      super.tearDown();

      blob.delete();
   }

   protected abstract ExtendedBackupManager getBackupManager();

   protected ExtendedBackupManager getJCRBackupManager()
   {
      if (backup == null)
      {
         InitParams initParams = new InitParams();
         PropertiesParam pps = new PropertiesParam();
         pps.setProperty(BackupManagerImpl.FULL_BACKUP_TYPE,
            "org.exoplatform.services.jcr.ext.backup.impl.fs.FullBackupJob");
         pps.setProperty(BackupManagerImpl.INCREMENTAL_BACKUP_TYPE,
            "org.exoplatform.services.jcr.ext.backup.impl.fs.IncrementalBackupJob");
         pps.setProperty(BackupManagerImpl.BACKUP_DIR, "target/backup");
         pps.setProperty(BackupManagerImpl.DEFAULT_INCREMENTAL_JOB_PERIOD, "3600");

         initParams.put(BackupManagerImpl.BACKUP_PROPERTIES, pps);

         BackupManagerImpl backup = new BackupManagerImpl(initParams, repositoryService);
         backup.start();

         return backup;
      }

      return backup;
   }

   protected ExtendedBackupManager getRDBMSBackupManager()
   {
      if (backup == null)
      {
         InitParams initParams = new InitParams();
         PropertiesParam pps = new PropertiesParam();
         pps.setProperty(BackupManagerImpl.FULL_BACKUP_TYPE,
            "org.exoplatform.services.jcr.ext.backup.impl.rdbms.FullBackupJob");
         pps.setProperty(BackupManagerImpl.INCREMENTAL_BACKUP_TYPE,
            "org.exoplatform.services.jcr.ext.backup.impl.fs.IncrementalBackupJob");
         pps.setProperty(BackupManagerImpl.BACKUP_DIR, "target/backup");
         pps.setProperty(BackupManagerImpl.DEFAULT_INCREMENTAL_JOB_PERIOD, "3600");

         initParams.put(BackupManagerImpl.BACKUP_PROPERTIES, pps);

         BackupManagerImpl backup = new BackupManagerImpl(initParams, repositoryService);
         backup.start();

         return backup;
      }

      return backup;
   }

   protected RepositoryImpl getReposityToBackup() throws RepositoryException, RepositoryConfigurationException
   {
      return (RepositoryImpl) repositoryService.getRepository(repositoryNameToBackup);
   }


   protected WorkspaceEntry makeWorkspaceEntry(String name, String sourceName)
   {
      WorkspaceEntry ws1e = (WorkspaceEntry) ws1Session.getContainer().getComponentInstanceOfType(WorkspaceEntry.class);

      WorkspaceEntry ws1back = new WorkspaceEntry();
      ws1back.setName(name);
      // RepositoryContainer rcontainer = (RepositoryContainer)
      // container.getComponentInstanceOfType(RepositoryContainer.class);
      ws1back.setUniqueName(((RepositoryImpl) ws1Session.getRepository()).getName() + "_" + ws1back.getName()); // EXOMAN

      ws1back.setAccessManager(ws1e.getAccessManager());
      ws1back.setCache(ws1e.getCache());
      //      ws1back.setContainer(ws1e.getContainer());
      ws1back.setLockManager(ws1e.getLockManager());
      ws1back.setInitializer(ws1e.getInitializer());

      // Indexer
      ArrayList qParams = new ArrayList();
      // qParams.add(new SimpleParameterEntry("indexDir", "target" + File.separator+ "temp" +
      // File.separator +"index" + name));
      qParams.add(new SimpleParameterEntry(QueryHandlerParams.PARAM_INDEX_DIR, "target/temp/index/" + name
         + System.currentTimeMillis()));
      QueryHandlerEntry qEntry =
               new QueryHandlerEntry("org.exoplatform.services.jcr.impl.core.query.lucene.SearchIndex", qParams);

      ws1back.setQueryHandler(qEntry); // EXOMAN

      ArrayList params = new ArrayList();
      for (Iterator i = ws1e.getContainer().getParameters().iterator(); i.hasNext();)
      {
         SimpleParameterEntry p = (SimpleParameterEntry) i.next();
         SimpleParameterEntry newp = new SimpleParameterEntry(p.getName(), p.getValue());

         if (newp.getName().equals("source-name"))
            newp.setValue(sourceName);
         else if (newp.getName().equals("swap-directory"))
            newp.setValue("target/temp/swap/" + name + System.currentTimeMillis());

         params.add(newp);
      }

      ContainerEntry ce =
               new ContainerEntry("org.exoplatform.services.jcr.impl.storage.jdbc.JDBCWorkspaceDataContainer", params);
      
      ArrayList<ValueStorageEntry> list = new ArrayList<ValueStorageEntry>();

      // value storage
      ArrayList<ValueStorageFilterEntry> vsparams = new ArrayList<ValueStorageFilterEntry>();
      ValueStorageFilterEntry filterEntry = new ValueStorageFilterEntry();
      filterEntry.setPropertyType("Binary");
      vsparams.add(filterEntry);

      ValueStorageEntry valueStorageEntry =
         new ValueStorageEntry("org.exoplatform.services.jcr.impl.storage.value.fs.TreeFileValueStorage", vsparams);
      ArrayList<SimpleParameterEntry> spe = new ArrayList<SimpleParameterEntry>();
      spe.add(new SimpleParameterEntry("path", "target/temp/values/" + name + "_" + System.currentTimeMillis()));
      valueStorageEntry.setId("draft");
      valueStorageEntry.setParameters(spe);
      valueStorageEntry.setFilters(vsparams);

      // containerEntry.setValueStorages();
      list.add(valueStorageEntry);
      ce.setValueStorages(list);


      ws1back.setContainer(ce);

      return ws1back;
   }

   protected void restoreAndCheck(String workspaceName, String datasourceName, String backupLogFilePath, File backDir,
            int startIndex, int stopIndex) throws RepositoryConfigurationException, RepositoryException,
            BackupOperationException, BackupConfigurationException
   {
      // restore
      RepositoryEntry re =
               (RepositoryEntry) ws1Session.getContainer().getComponentInstanceOfType(RepositoryEntry.class);
      WorkspaceEntry ws1back = makeWorkspaceEntry(workspaceName, datasourceName);

      repository.configWorkspace(ws1back);

      File backLog = new File(backupLogFilePath);
      if (backLog.exists())
      {
         BackupChainLog bchLog = new BackupChainLog(backLog);
         backup.restore(bchLog, re.getName(), ws1back, false);

         // check
         SessionImpl back1 = null;
         try
         {
            back1 = (SessionImpl) repository.login(credentials, ws1back.getName());
            Node ws1backTestRoot = back1.getRootNode().getNode("backupTest");
            for (int i = startIndex; i < stopIndex; i++)
            {
               assertEquals("Restored content should be same", "property-" + i, ws1backTestRoot.getNode("node_" + i)
                        .getProperty("exo:data").getString());
            }
         }
         catch (Exception e)
         {
            e.printStackTrace();
            fail(e.getMessage());
         }
         finally
         {
            if (back1 != null)
               back1.logout();
         }
      }
      else
         fail("There are no backup files in " + backDir.getAbsolutePath());
   }

   protected void addContent(Node node, int startIndex, int stopIndex, long sleepTime) throws ValueFormatException,
            VersionException, LockException, ConstraintViolationException, ItemExistsException, PathNotFoundException,
            RepositoryException, InterruptedException
   {
      for (int i = startIndex; i <= stopIndex; i++)
      {
         node.addNode("node_" + i).setProperty("exo:data", "property-" + i);
         Thread.sleep(sleepTime);
         if (i % 10 == 0)
            node.save(); // log here via listener
      }
      node.save();
   }

   protected void waitTime(Date time) throws InterruptedException
   {
      while (Calendar.getInstance().getTime().before(time))
      {
         Thread.yield();
         Thread.sleep(50);
      }
      Thread.sleep(250);
   }

   protected void removeWorkspaceFully(String repositoryName, String workspaceName) throws Exception
   {
      // get current workspace configuration
      WorkspaceEntry wEntry = null;;
      for (WorkspaceEntry entry : repositoryService.getRepository(repositoryName).getConfiguration()
         .getWorkspaceEntries())
      {
         if (entry.getName().equals(workspaceName))
         {
            wEntry = entry;
            break;
         }
      }

      if (wEntry == null)
      {
         throw new WorkspaceRestoreException("Workspace " + workspaceName + " did not found in current repository "
            + repositoryName + " configuration");
      }

      boolean isSystem =
         repositoryService.getRepository(repositoryName).getConfiguration().getSystemWorkspaceName()
            .equals(wEntry.getName());

      // remove workspace 
      forceCloseSession(repositoryName, wEntry.getName());
      repositoryService.getRepository(repositoryName).removeWorkspace(wEntry.getName());

      // clean db
      DBCleanService.cleanWorkspaceData(wEntry);

      // clean value storage
      if (wEntry.getContainer().getValueStorages() != null)
      {
         for (ValueStorageEntry valueStorage : wEntry.getContainer().getValueStorages())
         {
            DirectoryHelper.removeDirectory(new File(valueStorage.getParameterValue(FileValueStorage.PATH)));
         }
      }

      // clean index
      if (wEntry.getQueryHandler() != null)
      {
         DirectoryHelper.removeDirectory(new File(wEntry.getQueryHandler().getParameterValue(
            QueryHandlerParams.PARAM_INDEX_DIR, null)));
         if (isSystem)
         {
            DirectoryHelper.removeDirectory(new File(wEntry.getQueryHandler().getParameterValue(
               QueryHandlerParams.PARAM_INDEX_DIR,
               null)
               + "_" + SystemSearchManager.INDEX_DIR_SUFFIX));
         }
      }
   }

   protected void removeWorkspaceFullySingleDB(String repositoryName, String workspaceName) throws Exception
   {
      // get current workspace configuration
      WorkspaceEntry wEntry = null;;
      for (WorkspaceEntry entry : repositoryService.getRepository(repositoryName).getConfiguration()
         .getWorkspaceEntries())
      {
         if (entry.getName().equals(workspaceName))
         {
            wEntry = entry;
            break;
         }
      }

      if (wEntry == null)
      {
         throw new WorkspaceRestoreException("Workspace " + workspaceName + " did not found in current repository "
            + repositoryName + " configuration");
      }

      boolean isSystem =
         repositoryService.getRepository(repositoryName).getConfiguration().getSystemWorkspaceName()
            .equals(wEntry.getName());

      //close all session
      forceCloseSession(repositoryName, wEntry.getName());

      repositoryService.getRepository(repositoryName).removeWorkspace(wEntry.getName());

      DBCleanService.cleanWorkspaceData(wEntry);

      if (wEntry.getContainer().getValueStorages() != null)
      {
         for (ValueStorageEntry valueStorage : wEntry.getContainer().getValueStorages())
         {
            DirectoryHelper.removeDirectory(new File(valueStorage.getParameterValue(FileValueStorage.PATH)));
         }
      }

      if (wEntry.getQueryHandler() != null)
      {
         DirectoryHelper.removeDirectory(new File(wEntry.getQueryHandler().getParameterValue(
            QueryHandlerParams.PARAM_INDEX_DIR, null)));
         if (isSystem)
         {
            DirectoryHelper.removeDirectory(new File(wEntry.getQueryHandler().getParameterValue(
               QueryHandlerParams.PARAM_INDEX_DIR,
               null)
               + "_" + SystemSearchManager.INDEX_DIR_SUFFIX));
         }
      }
   }

   protected void removeRepositoryFully(String repositoryName) throws Exception
   {
      // get current repository configuration
      RepositoryEntry repositoryEntry = repositoryService.getConfig().getRepositoryConfiguration(repositoryName);

      if (repositoryEntry == null)
      {
         throw new RepositoryRestoreExeption("Current repository configuration " + repositoryName + " did not found");
      }

      //Create local copy of WorkspaceEntry for all workspaces
      ArrayList<WorkspaceEntry> workspaceList = new ArrayList<WorkspaceEntry>();
      workspaceList.addAll(repositoryEntry.getWorkspaceEntries());

      //close all session
      for (WorkspaceEntry wEntry : workspaceList)
      {
         forceCloseSession(repositoryEntry.getName(), wEntry.getName());
      }

      String systemWorkspaceName =
         repositoryService.getRepository(repositoryName).getConfiguration().getSystemWorkspaceName();

      //remove repository
      repositoryService.removeRepository(repositoryEntry.getName());

      // clean data
      for (WorkspaceEntry wEntry : workspaceList)
      {
         DBCleanService.cleanWorkspaceData(wEntry);

         if (wEntry.getContainer().getValueStorages() != null)
         {
            for (ValueStorageEntry valueStorage : wEntry.getContainer().getValueStorages())
            {
               DirectoryHelper.removeDirectory(new File(valueStorage.getParameterValue(FileValueStorage.PATH)));
            }
         }

         boolean isSystem = systemWorkspaceName.equals(wEntry.getName());

         if (wEntry.getQueryHandler() != null)
         {
            DirectoryHelper.removeDirectory(new File(wEntry.getQueryHandler().getParameterValue(
               QueryHandlerParams.PARAM_INDEX_DIR, null)));
            if (isSystem)
            {
               DirectoryHelper.removeDirectory(new File(wEntry.getQueryHandler().getParameterValue(
                  QueryHandlerParams.PARAM_INDEX_DIR, null)
                  + "_" + SystemSearchManager.INDEX_DIR_SUFFIX));
            }
         }
      }
   }

   /**
    * forceCloseSession. Close sessions on specific workspace.
    * 
    * @param repositoryName
    *          repository name
    * @param workspaceName
    *          workspace name
    * @return int return the how many sessions was closed
    * @throws RepositoryConfigurationException
    *           will be generate RepositoryConfigurationException
    * @throws RepositoryException
    *           will be generate RepositoryException
    */
   private int forceCloseSession(String repositoryName, String workspaceName) throws RepositoryException,
            RepositoryConfigurationException
   {
      ManageableRepository mr = repositoryService.getRepository(repositoryName);
      WorkspaceContainerFacade wc = mr.getWorkspaceContainer(workspaceName);

      SessionRegistry sessionRegistry = (SessionRegistry) wc.getComponent(SessionRegistry.class);

      return sessionRegistry.closeSessions(workspaceName);
   }

   public void waitEndOfBackup(BackupChain bch) throws Exception
   {
      while (bch.getFullBackupState() != BackupChain.FINISHED)
      {
         Thread.yield();
         Thread.sleep(50);
      }
   }

   public void waitEndOfBackup(RepositoryBackupChain bch) throws Exception
   {
      while (bch.getState() != RepositoryBackupChain.FINISHED
         && bch.getState() != RepositoryBackupChain.FULL_BACKUP_FINISHED_INCREMENTAL_BACKUP_WORKING)
      {
         Thread.yield();
         Thread.sleep(50);
      }
   }

   public void waitEndOfRestore(String repositoryName) throws Exception
   {
      while (backup.getLastRepositoryRestore(repositoryName).getStateRestore() != JobRepositoryRestore.REPOSITORY_RESTORE_SUCCESSFUL
         && backup.getLastRepositoryRestore(repositoryName).getStateRestore() != JobRepositoryRestore.REPOSITORY_RESTORE_FAIL)
      {
         Thread.sleep(50);
      }
   }

   public void waitEndOfRestore(String repositoryName, String workspaceName) throws Exception
   {
      while (backup.getLastRestore(repositoryName, workspaceName).getStateRestore() != JobWorkspaceRestore.RESTORE_SUCCESSFUL
         && backup.getLastRestore(repositoryName, workspaceName).getStateRestore() != JobWorkspaceRestore.RESTORE_FAIL)
      {
         Thread.sleep(50);
      }
   }

   public void addIncrementalConent(ManageableRepository repository, String wsName) throws Exception
   {
      SessionImpl session = (SessionImpl)repository.login(credentials, wsName);
      Node rootNode = session.getRootNode().addNode("testIncremental");

      // add some changes which will be logged in incremental log
      rootNode.addNode("node1").setProperty("prop1", "value1");
      rootNode.addNode("node2").setProperty("prop2", new FileInputStream(blob));
      rootNode.addNode("node3").addMixin("mix:lockable");
      session.save();
   }

   public void addConent(ManageableRepository repository, String wsName) throws Exception
   {
      SessionImpl session = (SessionImpl)repository.login(credentials, wsName);
      Node rootNode = session.getRootNode().addNode("test");

      // add some changes which will be logged in incremental log
      rootNode.addNode("node1").setProperty("prop1", "value1");
      rootNode.addNode("node2").setProperty("prop2", new FileInputStream(blob));
      rootNode.addNode("node3").addMixin("mix:lockable");
      session.save();
   }

   public void checkConent(ManageableRepository repository, String wsName) throws Exception
   {
      SessionImpl session = (SessionImpl)repository.login(credentials, wsName);

      Node rootNode = session.getRootNode().getNode("test");
      assertEquals(rootNode.getNode("node1").getProperty("prop1").getString(), "value1");

      InputStream in = rootNode.getNode("node2").getProperty("prop2").getStream();
      try
      {
         compareStream(new FileInputStream(blob), in);
      }
      finally
      {
         in.close();
      }
   }

   public void checkIncrementalConent(ManageableRepository repository, String wsName) throws Exception
   {
      SessionImpl session = (SessionImpl)repository.login(credentials, wsName);

      Node rootNode = session.getRootNode().getNode("testIncremental");
      assertEquals(rootNode.getNode("node1").getProperty("prop1").getString(), "value1");

      InputStream in = rootNode.getNode("node2").getProperty("prop2").getStream();
      try
      {
         compareStream(new FileInputStream(blob), in);
      }
      finally
      {
         in.close();
      }
   }

   protected RepoInfo createRepositoryAndGetSession() throws Exception
   {
      ManageableRepository repository = helper.createRepository(container, DatabaseStructureType.MULTI, null);
      WorkspaceEntry wsEntry = helper.createWorkspaceEntry(DatabaseStructureType.MULTI, null);
      helper.addWorkspace(repository, wsEntry);

      RepoInfo rInfo = new RepoInfo();
      rInfo.rName = repository.getConfiguration().getName();
      rInfo.wsName = wsEntry.getName();
      rInfo.sysWsName = repository.getConfiguration().getSystemWorkspaceName();
      rInfo.session = repositoryService.getRepository(rInfo.rName).login(credentials, rInfo.wsName);

      return rInfo;
   }

   /**
    * Class for tests purpose only. To have ability to access to {@link ContainerResponseWriter}.
    */
   protected class TesterContainerResponce extends ContainerResponse
   {

      public ByteArrayContainerResponseWriter responseWriter;

      public TesterContainerResponce(ByteArrayContainerResponseWriter responseWriter)
      {
         super(responseWriter);
         this.responseWriter = responseWriter;
      }
   }

   /**
    * Aggregate info about newly created repository.
    */
   protected class RepoInfo
   {
      public String rName;

      public String wsName;

      public String sysWsName;

      public Session session;
   }

}
