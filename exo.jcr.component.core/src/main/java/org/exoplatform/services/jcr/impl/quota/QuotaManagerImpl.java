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
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.rpc.RPCService;
import org.picocontainer.Startable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author <a href="abazko@exoplatform.com">Anatoliy Bazko</a>
 * @version $Id: QuotaManagerImpl.java 34360 2009-07-22 23:58:59Z tolusha $
 */
@Managed
@NameTemplate(@Property(key = "service", value = "QuotaManager"))
public abstract class QuotaManagerImpl implements ExtendedQuotaManager, Startable
{

   /**
    * Logger.
    */
   protected final Log LOG = ExoLogger.getLogger("exo.jcr.component.core.QuotaManagerImpl");

   /**
    * Cache configuration properties.
    */
   public static final String CACHE_CONFIGURATION_PROPERTIES_PARAM = "cache-configuration";

   /**
    * Exceeded quota behaviour value parameter.
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
    * QuotaManager constructor.
    */
   public QuotaManagerImpl(InitParams initParams, RPCService rpcService, ConfigurationManager cfm)
   {
      this.rpcService = rpcService;

      ValueParam param = initParams.getValueParam(EXCEEDED_QUOTA_BEHAVIOUR);
      this.exceededQuotaBehavior =
         param == null ? ExceededQuotaBehavior.WARNING : ExceededQuotaBehavior
            .valueOf(param.getValue().toUpperCase());
   }

   /**
    * QuotaManager constructor.
    */
   public QuotaManagerImpl(InitParams initParams, ConfigurationManager cfm)
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
   public void setNodeQuota(String repositoryName, String workspaceName, String nodePath, long quotaSize,
      boolean asyncUpdate) throws QuotaManagerException
   {
      // TODO Auto-generated method stub

   }

   /**
    * {@inheritDoc}
    */
   public void setGroupOfNodesQuota(String repositoryName, String workspaceName, String ancestorPath, long quotaSize,
      boolean asyncUpdate) throws QuotaManagerException
   {
      // TODO Auto-generated method stub

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
   public void setWorkspaceQuota(String repositoryName, String workspaceName, long quotaSize)
      throws QuotaManagerException
   {
      // TODO Auto-generated method stub

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
   public long getRepositoryDataSize(String repositoryName) throws QuotaManagerException
   {
      RepositoryQuotaManager rqm = getRepositoryQuotaManager(repositoryName);
      return rqm.getRepositoryDataSize();
   }

   /**
    * {@inheritDoc}
    */
   public void setRepositoryQuota(String repositoryName, long quotaSize)
      throws QuotaManagerException
   {
      // TODO Auto-generated method stub

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
      long size = 0;
      for (RepositoryQuotaManager rqm : rQuotaManagers.values())
      {
         size += rqm.getRepositoryDataSize();
      }

      return size;
   }

   /**
    * {@inheritDoc}
    */
   public void setGlobalQuota(long quotaSize) throws QuotaManagerException
   {
      // TODO Auto-generated method stub

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
   }

   /**
    * {@inheritDoc}
    */
   public void registerRepositoryQuotaManager(String repositoryName, RepositoryQuotaManager rQuotaManager)
   {
      rQuotaManagers.put(repositoryName, rQuotaManager);
   }

   /**
    * {@inheritDoc}
    */
   public void unregisterRepositoryQuotaManager(String repositoryName)
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
    * Returns behaviour when quota limit is exceeded.
    */
   ExceededQuotaBehavior getExceededQuotaBehaviour()
   {
      return exceededQuotaBehavior;
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
}
