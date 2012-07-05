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

import org.exoplatform.container.configuration.ConfigurationManager;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;
import org.exoplatform.management.annotations.Managed;
import org.exoplatform.management.annotations.ManagedDescription;
import org.exoplatform.management.jmx.annotations.NameTemplate;
import org.exoplatform.management.jmx.annotations.Property;
import org.exoplatform.services.jcr.config.RepositoryConfigurationException;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.rpc.RPCService;
import org.picocontainer.Startable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * @author <a href="abazko@exoplatform.com">Anatoliy Bazko</a>
 * @version $Id: AbstractQuotaManager.java 34360 2009-07-22 23:58:59Z tolusha $
 */
@Managed
@NameTemplate(@Property(key = "service", value = "QuotaManager"))
public abstract class AbstractQuotaManager implements QuotaManager, Startable
{

   /**
    * Logger.
    */
   protected final Log LOG = ExoLogger.getLogger("exo.jcr.component.core.AbstractQuotaManager");

   /**
    * Cache configuration properties.
    */
   public static final String CACHE_CONFIGURATION_PROPERTIES_PARAM = "cache-configuration";

   /**
    * Exceeded quota behavior value parameter.
    */
   public static final String EXCEEDED_QUOTA_BEHAVIOUR = "exceeded-quota-behaviour";

   /**
    * All {@link WorkspaceQuotaManager} belonging to repository.
    */
   protected Map<String, RepositoryQuotaManager> rQuotaManagers =
      new ConcurrentHashMap<String, RepositoryQuotaManager>();

   /**
    * RPCService to communicate with other nodes in cluster.
    */
   protected final RPCService rpcService;

   /**
    * What should to do when node exceeds quota limit. There are two behavior:
    * <ul>
    *    <li>{@link ExceededQuotaBehavior#WARNING}: log a warning</li>
    *    <li>{@link ExceededQuotaBehavior#ERROR}: throw an exception</li>
    * </ul>
    */
   protected final ExceededQuotaBehavior exceededQuotaBehavior;
   
   /**
    * Executor service.
    */
   protected final Executor executor;

   /**
    * {@link QuotaPersister}
    */
   protected final QuotaPersister quotaPersister;

   /**
    * Initialization parameters.
    */
   protected final InitParams initParams;

   /**
    * Configuration manager.
    */
   protected final ConfigurationManager cfm;

   /**
    * Indicates if global data size exceeded quota limit.
    */
   protected boolean alerted;

   /**
    * QuotaManager constructor.
    */
   public AbstractQuotaManager(InitParams initParams, RPCService rpcService, ConfigurationManager cfm)
      throws RepositoryConfigurationException, QuotaManagerException
   {
      ValueParam param = initParams.getValueParam(EXCEEDED_QUOTA_BEHAVIOUR);
      this.exceededQuotaBehavior =
         param == null ? ExceededQuotaBehavior.WARNING : ExceededQuotaBehavior
            .valueOf(param.getValue().toUpperCase());
      
      this.cfm = cfm;
      this.initParams = initParams;
      this.rpcService = rpcService;

      this.quotaPersister = initQuotaPersister();
      this.executor = Executors.newFixedThreadPool(1, new ThreadFactory()
      {
         public Thread newThread(Runnable arg0)
         {
            return new Thread(arg0, "QuotaManagerThread");
         }
      });
   }

   /**
    * QuotaManager constructor.
    */
   public AbstractQuotaManager(InitParams initParams, ConfigurationManager cfm) throws RepositoryConfigurationException,
      QuotaManagerException
   {
      this(initParams, null, cfm);
   }

   /**
    * {@inheritDoc}
    */
   public long getNodeDataSize(String repositoryName, String workspaceName, String nodePath)
      throws QuotaManagerException
   {
      RepositoryQuotaManager rqm = getRepositoryQuotaManager(repositoryName);
      return rqm.getNodeDataSize(workspaceName, nodePath);
   }

   /**
    * {@inheritDoc}
    */
   public void setNodeQuota(String repositoryName, String workspaceName, String nodePath, long quotaLimit,
      boolean asyncUpdate) throws QuotaManagerException
   {
      RepositoryQuotaManager rqm = getRepositoryQuotaManager(repositoryName);
      rqm.setNodeQuota(workspaceName, nodePath, quotaLimit, asyncUpdate);
   }

   /**
    * {@inheritDoc}
    */
   public void removeNodeQuota(String repositoryName, String workspaceName, String nodePath)
      throws QuotaManagerException
   {
      RepositoryQuotaManager rqm = getRepositoryQuotaManager(repositoryName);
      rqm.removeNodeQuota(workspaceName, nodePath);
   }

   /**
    * {@inheritDoc}
    */
   public void removeGroupOfNodesQuota(String repositoryName, String workspaceName, String nodePath)
      throws QuotaManagerException
   {
      RepositoryQuotaManager rqm = getRepositoryQuotaManager(repositoryName);
      rqm.removeGroupOfNodesQuota(workspaceName, nodePath);
   }

   /**
    * {@inheritDoc}
    */
   public long getNodeQuota(String repositoryName, String workspaceName, String nodePath) throws QuotaManagerException
   {
      RepositoryQuotaManager rqm = getRepositoryQuotaManager(repositoryName);
      return rqm.getNodeQuota(workspaceName, nodePath);
   }

   /**
    * {@inheritDoc}
    */
   public void setGroupOfNodesQuota(String repositoryName, String workspaceName, String patternPath, long quotaLimit,
      boolean asyncUpdate) throws QuotaManagerException
   {
      RepositoryQuotaManager rqm = getRepositoryQuotaManager(repositoryName);
      rqm.setGroupOfNodesQuota(workspaceName, patternPath, quotaLimit, asyncUpdate);
   }

   /**
    * {@inheritDoc}
    */
   public void setWorkspaceQuota(String repositoryName, String workspaceName, long quotaLimit)
      throws QuotaManagerException
   {
      RepositoryQuotaManager rqm = getRepositoryQuotaManager(repositoryName);
      rqm.setWorkspaceQuota(workspaceName, quotaLimit);
   }

   /**
    * {@inheritDoc}
    */
   public void removeWorkspaceQuota(String repositoryName, String workspaceName) throws QuotaManagerException
   {
      RepositoryQuotaManager rqm = getRepositoryQuotaManager(repositoryName);
      rqm.removeWorkspaceQuota(workspaceName);
   }

   /**
    * {@inheritDoc}
    */
   public long getWorkspaceQuota(String repositoryName, String workspaceName) throws QuotaManagerException
   {
      RepositoryQuotaManager rqm = getRepositoryQuotaManager(repositoryName);
      return rqm.getWorkspaceQuota(workspaceName);
   }

   /**
    * {@inheritDoc}
    */
   public long getWorkspaceDataSize(String repositoryName, String workspaceName) throws QuotaManagerException
   {
      RepositoryQuotaManager rqm = getRepositoryQuotaManager(repositoryName);
      return rqm.getWorkspaceDataSize(workspaceName);
   }

   /**
    * {@inheritDoc}
    */
   public long getWorkspaceIndexSize(String repositoryName, String workspaceName) throws QuotaManagerException
   {
      RepositoryQuotaManager rqm = getRepositoryQuotaManager(repositoryName);
      return rqm.getWorkspaceIndexSize(workspaceName);
   }

   /**
    * {@inheritDoc}
    */
   public void setRepositoryQuota(String repositoryName, long quotaLimit) throws QuotaManagerException
   {
      RepositoryQuotaManager rqm = getRepositoryQuotaManager(repositoryName);
      rqm.setRepositoryQuota(quotaLimit);
   }

   /**
    * {@inheritDoc}
    */
   public void removeRepositoryQuota(String repositoryName) throws QuotaManagerException
   {
      RepositoryQuotaManager rqm = getRepositoryQuotaManager(repositoryName);
      rqm.removeRepositoryQuota();
   }

   /**
    * {@inheritDoc}
    */
   public long getRepositoryQuota(String repositoryName) throws QuotaManagerException
   {
      RepositoryQuotaManager rqm = getRepositoryQuotaManager(repositoryName);
      return rqm.getRepositoryQuota();
   }

   /**
    * {@inheritDoc}
    */
   public long getRepositoryDataSize(String repositoryName) throws QuotaManagerException
   {
      RepositoryQuotaManager rqm = getRepositoryQuotaManager(repositoryName);
      return rqm.getRepositoryDataSize();
   }

   /**
    * {@inheritDoc}
    */
   public long getRepositoryIndexSize(String repositoryName) throws QuotaManagerException
   {
      RepositoryQuotaManager rqm = getRepositoryQuotaManager(repositoryName);
      return rqm.getRepositoryIndexSize();
   }

   /**
    * {@inheritDoc}
    */
   @Managed
   @ManagedDescription("Returns global data size")
   public long getGlobalDataSize() throws QuotaManagerException
   {
      try
      {
         return quotaPersister.getGlobalDataSize();
      }
      catch (UnknownQuotaUsedException e)
      {
         return getGlobalDataSizeDirectly();
      }
   }

   /**
    * {@inheritDoc}
    */
   @Managed
   @ManagedDescription("Returns global quota limit")
   public void setGlobalQuota(long quotaLimit) throws QuotaManagerException
   {
      quotaPersister.setGlobalQuota(quotaLimit);

      TrackGlobalTask task = new TrackGlobalTask(this, quotaLimit);
      executor.execute(task);
   }

   /**
    * {@inheritDoc}
    */
   @Managed
   @ManagedDescription("Removes global quota limit")
   public void removeGlobalQuota() throws QuotaManagerException
   {
      alerted = false;
      quotaPersister.removeGlobalQuota();
   }

   /**
    * {@inheritDoc}
    */
   @Managed
   @ManagedDescription("Returns global quota limit")
   public long getGlobalQuota() throws QuotaManagerException
   {
      return quotaPersister.getGlobalQuota();
   }

   /**
    * Tracks whole JCR instance by calculating the size if a stored content,
    *
    * @param quotaLimit
    *          the quota limit         
    * @throws QuotaManagerException
    *          if any exception is occurred
    */
   protected void trackGlobal(long quotaLimit) throws QuotaManagerException
   {
      long dataSize;

      try
      {
         dataSize = quotaPersister.getGlobalDataSize();
      }
      catch (UnknownQuotaUsedException e)
      {
         dataSize = getGlobalDataSizeDirectly();
         quotaPersister.setGlobalDataSize(dataSize);
      }

      alerted = dataSize > quotaLimit;
   }

   /**
    * {@inheritDoc}
    */
   @Managed
   @ManagedDescription("Returns global index size")
   public long getGlobalIndexSize() throws QuotaManagerException
   {
      long size = 0;
      for (RepositoryQuotaManager rqm : rQuotaManagers.values())
      {
         size += rqm.getRepositoryIndexSize();
      }

      return size;
   }

   /**
    * {@inheritDoc}
    */
   public void start()
   {
   }

   /**
    * {@inheritDoc}
    */
   public void stop()
   {
      quotaPersister.destroy();
   }

   /**
    * Registers {@link RepositoryQuotaManager} by name. To delegate repository based operation
    * to appropriate level. 
    */
   protected void registerRepositoryQuotaManager(String repositoryName, RepositoryQuotaManager rQuotaManager)
   {
      rQuotaManagers.put(repositoryName, rQuotaManager);
   }

   /**
    * Unregisters {@link RepositoryQuotaManager} by name. 
    */
   protected void unregisterRepositoryQuotaManager(String repositoryName)
   {
      rQuotaManagers.remove(repositoryName);
   }

   /**
    * Behavior when quota exceeded.
    */
   public enum ExceededQuotaBehavior {
      WARNING, ERROR
   }

   /**
    * Returns {@link Executor} instance.
    */
   protected Executor getExecutorSevice()
   {
      return executor;
   }

   /**
    * Returns {@link QuotaPersister} instance.
    */
   protected QuotaPersister getQuotaPersister()
   {
      return quotaPersister;
   }

   /**
    * Returns behavior when quota limit is exceeded.
    */
   ExceededQuotaBehavior getExceededQuotaBehaviour()
   {
      return exceededQuotaBehavior;
   }

   /**
    * Calculates the global size by summing sized of all repositories.
    */
   private long getGlobalDataSizeDirectly() throws QuotaManagerException
   {
      long size = 0;
      for (RepositoryQuotaManager rqm : rQuotaManagers.values())
      {
         size += rqm.getRepositoryDataSize();
      }

      return size;
   }

   private RepositoryQuotaManager getRepositoryQuotaManager(String repositoryName) throws QuotaManagerException
   {
      RepositoryQuotaManager rqm = rQuotaManagers.get(repositoryName);
      if (rqm == null)
      {
         throw new QuotaManagerException("Repository " + repositoryName + " is not registered");
      }

      return rqm;
   }

   /**
    * Initialize persister.
    */
   protected abstract QuotaPersister initQuotaPersister() throws RepositoryConfigurationException,
      QuotaManagerException;
}
