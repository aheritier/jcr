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
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author <a href="abazko@exoplatform.com">Anatoliy Bazko</a>
 * @version $Id: BaseQuotaManager.java 34360 2009-07-22 23:58:59Z tolusha $
 */
@Managed
@NameTemplate(@Property(key = "service", value = "QuotaManager"))
public abstract class BaseQuotaManager implements QuotaManager, Startable
{

   /**
    * Logger.
    */
   protected static final Log LOG = ExoLogger.getLogger("exo.jcr.component.core.BaseQuotaManager");

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
    * What should to do when node exceeds quota limit. There are two behaviors:
    * <ul>
    *    <li>{@link ExceededQuotaBehavior#WARNING}: log a warning</li>
    *    <li>{@link ExceededQuotaBehavior#EXCEPTION}: throw an exception</li>
    * </ul>
    */
   protected final ExceededQuotaBehavior exceededQuotaBehavior;

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
   protected AtomicBoolean alerted = new AtomicBoolean();

   /**
    * QuotaManager constructor.
    */
   public BaseQuotaManager(InitParams initParams, RPCService rpcService, ConfigurationManager cfm)
      throws RepositoryConfigurationException, QuotaManagerException
   {
      ValueParam param = initParams.getValueParam(EXCEEDED_QUOTA_BEHAVIOUR);
      this.exceededQuotaBehavior =
         param == null ? ExceededQuotaBehavior.WARNING : ExceededQuotaBehavior.valueOf(param.getValue().toUpperCase());

      this.cfm = cfm;
      this.initParams = initParams;
      this.rpcService = rpcService;
      this.quotaPersister = initQuotaPersister();

      defineAlertedState();
   }

   /**
    * QuotaManager constructor.
    */
   public BaseQuotaManager(InitParams initParams, ConfigurationManager cfm) throws RepositoryConfigurationException,
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
      return quotaPersister.getGlobalDataSize();
   }

   /**
    * {@inheritDoc}
    */
   @Managed
   @ManagedDescription("Returns global quota limit")
   public void setGlobalQuota(long quotaLimit) throws QuotaManagerException
   {
      quotaPersister.setGlobalQuota(quotaLimit);
      defineAlertedState();
   }

   /**
    * {@inheritDoc}
    */
   @Managed
   @ManagedDescription("Removes global quota limit")
   public void removeGlobalQuota() throws QuotaManagerException
   {
      quotaPersister.removeGlobalQuota();
      defineAlertedState();
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
      rQuotaManagers.clear();
   }

   /**
    * Accumulate changed data size. Can be either positive
    * or negative.
    * 
    * @param delta
    *          the size on which entity was changed
    */
   protected void accumulateChanges(long delta)
   {
      long dataSize = 0;
      try
      {
         dataSize = quotaPersister.getGlobalDataSize();
      }
      catch (UnknownQuotaDataSizeException e)
      {
         if (LOG.isTraceEnabled())
         {
            LOG.trace(e.getMessage(), e);
         }
      }

      long newDataSize = Math.max(dataSize + delta, 0);
      quotaPersister.setGlobalDataSize(newDataSize);

      defineAlertedState();
   }

   /**
    * Checks if entity can accumulate new changes. It is not possible
    * when entity is alerted and {@link ExceededQuotaLimitException} 
    * will be thrown.
    * 
    * @throws ExceededQuotaLimitException if entity is alerted
    */
   protected void validateAccumulateChanges() throws ExceededQuotaLimitException
   {
      if (alerted.get())
      {
         behaveOnQuotaExceeded("Global data size exceeded quota limit");
      }
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
      WARNING, EXCEPTION
   }

   /**
    * Define global alerted state based on values of quota limit and data size.
    */
   private void defineAlertedState()
   {
      try
      {
         long quotaLimit = quotaPersister.getGlobalQuota();
         try
         {
            long dataSize = quotaPersister.getGlobalDataSize();
            alerted.set(dataSize > quotaLimit);
         }
         catch (UnknownQuotaDataSizeException e)
         {
            alerted.set(false);
         }
      }
      catch (UnknownQuotaLimitException e)
      {
         alerted.set(false);
      }
   }

   /**
    * Calculates the global size by summing sized of all repositories.
    */
   protected long getGlobalDataSizeDirectly() throws QuotaManagerException
   {
      long size = 0;
      for (RepositoryQuotaManager rqm : rQuotaManagers.values())
      {
         size += rqm.getRepositoryDataSizeDirectly();
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
    * What to do if data size exceeded quota limit. Throwing exception or logging only.

    * @param message
    *          the detail message for exception or log operation
    * @throws ExceededQuotaLimitException
    *          if current behavior is {@link ExceededQuotaBehavior#EXCEPTION}           
    */
   protected void behaveOnQuotaExceeded(String message) throws ExceededQuotaLimitException
   {
      switch (exceededQuotaBehavior)
      {
         case EXCEPTION :
            throw new ExceededQuotaLimitException(message);

         case WARNING :
            LOG.warn(message);
            break;
      }
   }

   /**
    * Initialize persister.
    */
   protected abstract QuotaPersister initQuotaPersister() throws RepositoryConfigurationException,
      QuotaManagerException;
}
