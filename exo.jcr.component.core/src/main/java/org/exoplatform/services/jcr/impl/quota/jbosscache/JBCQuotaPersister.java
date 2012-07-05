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

import org.exoplatform.container.configuration.ConfigurationManager;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.services.jcr.config.MappedParametrizedObjectEntry;
import org.exoplatform.services.jcr.config.RepositoryConfigurationException;
import org.exoplatform.services.jcr.impl.quota.PathPatternUtils;
import org.exoplatform.services.jcr.impl.quota.QuotaManagerException;
import org.exoplatform.services.jcr.impl.quota.QuotaPersister;
import org.exoplatform.services.jcr.impl.quota.UnknownQuotaLimitException;
import org.exoplatform.services.jcr.impl.quota.UnknownQuotaUsedException;
import org.exoplatform.services.jcr.jbosscache.ExoJBossCacheFactory;
import org.exoplatform.services.jcr.jbosscache.ExoJBossCacheFactory.CacheType;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.jboss.cache.Cache;
import org.jboss.cache.Fqn;
import org.jboss.cache.Node;

import java.io.Serializable;
import java.util.Set;

import javax.jcr.RepositoryException;

/**
 * Cache structure:
 * <ul>
 *    <li>$QUOTA</li>
 *    <li>$DATA_SIZE</li>
 * </ul>
 * 
 * @author <a href="abazko@exoplatform.com">Anatoliy Bazko</a>
 * @version $Id: JBCQuotaPersister.java 34360 2009-07-22 23:58:59Z tolusha $
 */
public class JBCQuotaPersister implements QuotaPersister
{
   /**
    * Logger.
    */
   protected final Log LOG = ExoLogger.getLogger("exo.jcr.component.core.JBCQuotaPersister");

   /**
    * JBoss cache.
    */
   private Cache<Serializable, Object> cache;

   /**
    * Based region where allowed quota sized is stored. Should not be covered by eviction.
    */
   protected static final Fqn<String> QUOTA = Fqn.fromElements("$QUOTA");

   /**
    * Based region where used quota sized is stored. May be covered by eviction.
    */
   protected static final Fqn<String> DATA_SIZE = Fqn.fromElements("$DATA_SIZE");

   /**
    * Key name.
    */
   protected static final String SIZE = "$SIZE";

   /**
    * Key name.
    */
   protected static final String ASYNC_UPATE = "$ASYNC_UPATE";

   /**
    * Relative element name.
    */
   protected static final String QUOTA_PATHES = "$PATHES";

   /**
    * Relative element name.
    */
   protected static final String QUOTA_PATTERNS = "$PATTERNS";

   /**
    * JBCQuotaPersister constructor.
    */
   protected JBCQuotaPersister(InitParams initParams, ConfigurationManager cfm)
      throws RepositoryConfigurationException, QuotaManagerException
   {
      // create cache using custom factory
      ExoJBossCacheFactory<Serializable, Object> factory = new ExoJBossCacheFactory<Serializable, Object>(cfm);

      try
      {
         MappedParametrizedObjectEntry qmEntry = JBCQuotaManagerUtil.prepareJBCParameters(initParams);

         cache = factory.createCache(qmEntry);
         cache = ExoJBossCacheFactory.getShareableUniqueInstanceWithoutEviction(CacheType.QUOTA_CACHE, cache);
         cache.create();
         cache.start();

         createResidentNode(QUOTA);
         createResidentNode(DATA_SIZE);
      }
      catch (RepositoryException e)
      {
         throw new QuotaManagerException(e.getMessage(), e);
      }
   }

   /**
    * {@inheritDoc}
    */
   public long getNodeQuota(String repositoryName, String workspaceName, String nodePath) throws QuotaManagerException
   {
      Fqn<String> relativeFqn = Fqn.fromElements(repositoryName, workspaceName, QUOTA_PATHES, nodePath);
      try
      {
         return getQuota(relativeFqn);
      }
      catch (UnknownQuotaLimitException e)
      {
         Fqn<String> fqn = Fqn.fromRelativeElements(QUOTA, repositoryName, workspaceName, QUOTA_PATTERNS);

         Set<Object> children = cache.getChildrenNames(fqn);
         for (Object pattern : children)
         {
            if (PathPatternUtils.matches((String)pattern, nodePath, false))
            {
               relativeFqn = Fqn.fromElements(repositoryName, workspaceName, QUOTA_PATHES, (String)pattern);
               return getQuota(relativeFqn);
            }
         }

         throw e;
      }
   }

   /**
    * {@inheritDoc}
    */
   public void setNodeQuota(String repositoryName, String workspaceName, String nodePath, long quotaLimit,
      boolean asyncUpdate) throws QuotaManagerException
   {
      Fqn<String> fqn = Fqn.fromRelativeElements(QUOTA, repositoryName, workspaceName, QUOTA_PATHES);
      cache.put(fqn, SIZE, quotaLimit);
      cache.put(fqn, ASYNC_UPATE, asyncUpdate);
   }

   /**
    * {@inheritDoc}
    */
   public void setGroupOfNodeQuota(String repositoryName, String workspaceName, String patternPath, long quotaLimit,
      boolean asyncUpdate) throws QuotaManagerException
   {
      Fqn<String> fqn = Fqn.fromRelativeElements(QUOTA, repositoryName, workspaceName, QUOTA_PATTERNS);
      cache.put(fqn, SIZE, quotaLimit);
      cache.put(fqn, ASYNC_UPATE, asyncUpdate);
   }

   /**
    * {@inheritDoc}
    */
   public void removeNodeQuota(String repositoryName, String workspaceName, String nodePath)
      throws QuotaManagerException
   {
      Fqn<String> fqn = Fqn.fromRelativeElements(QUOTA, repositoryName, workspaceName, QUOTA_PATHES, nodePath);
      cache.remove(fqn, SIZE);
      cache.remove(fqn, ASYNC_UPATE);

      fqn = Fqn.fromRelativeElements(DATA_SIZE, repositoryName, workspaceName, nodePath);
      cache.remove(fqn, SIZE);
   }

   /**
    * {@inheritDoc}
    */
   public void removeGroupOfNodesQuota(String repositoryName, String workspaceName, String nodePath)
      throws QuotaManagerException
   {
      Fqn<String> fqn = Fqn.fromRelativeElements(QUOTA, repositoryName, workspaceName, QUOTA_PATTERNS, nodePath);
      cache.remove(fqn, SIZE);
      cache.remove(fqn, ASYNC_UPATE);
   }

   /**
    * {@inheritDoc}
    */
   public long getNodeDataSize(String repositoryName, String workspaceName, String nodePath)
      throws QuotaManagerException
   {
      Fqn<String> relativeFqn = Fqn.fromElements(repositoryName, workspaceName, nodePath);
      return getDataSize(relativeFqn);
   }

   /**
    * {@inheritDoc}
    */
   public void setNodeDataSize(String repositoryName, String workspaceName, String nodePath, long dataSize)
      throws QuotaManagerException
   {
      Fqn<String> fqn = Fqn.fromRelativeElements(DATA_SIZE, repositoryName, workspaceName, nodePath);
      cache.put(fqn, SIZE, dataSize);
   }

   /**
    * {@inheritDoc}
    */
   public long getWorkspaceQuota(String repositoryName, String workspaceName) throws QuotaManagerException
   {
      Fqn<String> relativeFqn = Fqn.fromElements(repositoryName, workspaceName);
      return getQuota(relativeFqn);
   }

   /**
    * {@inheritDoc}
    */
   public void setWorkspaceQuota(String repositoryName, String workspaceName, long quotaLimit)
      throws QuotaManagerException
   {
      Fqn<String> fqn = Fqn.fromRelativeElements(QUOTA, repositoryName, workspaceName);
      cache.put(fqn, SIZE, quotaLimit);
   }

   /**
    * {@inheritDoc}
    */
   public void removeWorkspaceQuota(String repositoryName, String workspaceName) throws QuotaManagerException
   {
      Fqn<String> fqn = Fqn.fromRelativeElements(QUOTA, repositoryName, workspaceName);
      cache.remove(fqn, SIZE);

      fqn = Fqn.fromRelativeElements(DATA_SIZE, repositoryName, workspaceName);
      cache.remove(fqn, SIZE);
   }

   /**
    * {@inheritDoc}
    */
   public void setWorkspaceDataSize(String repositoryName, String workspaceName, long dataSize)
      throws QuotaManagerException
   {
      Fqn<String> fqn = Fqn.fromRelativeElements(DATA_SIZE, repositoryName, workspaceName);
      cache.put(fqn, SIZE, dataSize);
   }

   /**
    * {@inheritDoc}
    */
   public long getWorkspaceDataSize(String repositoryName, String workspaceName) throws QuotaManagerException
   {
      Fqn<String> relativeFqn = Fqn.fromElements(repositoryName, workspaceName);
      return getDataSize(relativeFqn);
   }

   /**
    * {@inheritDoc}
    */
   public long getRepositoryDataSize(String repositoryName) throws QuotaManagerException
   {
      Fqn<String> relativeFqn = Fqn.fromElements(repositoryName);
      return getDataSize(relativeFqn);
   }

   /**
    * {@inheritDoc}
    */
   public void setRepositoryDataSize(String repositoryName, long dataSize) throws QuotaManagerException
   {
      Fqn<String> fqn = Fqn.fromRelativeElements(DATA_SIZE, repositoryName);
      cache.put(fqn, SIZE, dataSize);
   }

   /**
    * {@inheritDoc}
    */
   public long getRepositoryQuota(String repositoryName) throws QuotaManagerException
   {
      Fqn<String> relativeFqn = Fqn.fromElements(repositoryName);
      return getQuota(relativeFqn);
   }

   /**
    * {@inheritDoc}
    */
   public void setRepositoryQuota(String repositoryName, long quotaLimit) throws QuotaManagerException
   {
      Fqn<String> fqn = Fqn.fromRelativeElements(QUOTA, repositoryName);
      cache.put(fqn, SIZE, quotaLimit);
   }

   /**
    * {@inheritDoc}
    */
   public void removeRepositoryQuota(String repositoryName) throws QuotaManagerException
   {
      Fqn<String> fqn = Fqn.fromRelativeElements(QUOTA, repositoryName);
      cache.remove(fqn, SIZE);

      fqn = Fqn.fromRelativeElements(DATA_SIZE, repositoryName);
      cache.remove(fqn, SIZE);
   }

   /**
    * {@inheritDoc}
    */
   public void registerRepository(String repositoryName) throws QuotaManagerException
   {
      cache.getNode(QUOTA).addChild(Fqn.fromElements(repositoryName));
   }

   /**
    * {@inheritDoc}
    */
   public long getGlobalQuota() throws QuotaManagerException
   {
      return getQuota(Fqn.ROOT);
   }

   /**
    * {@inheritDoc}
    */
   public void setGlobalQuota(long quotaLimit) throws QuotaManagerException
   {
      cache.put(QUOTA, SIZE, quotaLimit);
   }

   /**
    * {@inheritDoc}
    */
   public void removeGlobalQuota() throws QuotaManagerException
   {
      cache.remove(QUOTA, SIZE);
      cache.remove(DATA_SIZE, SIZE);
   }

   /**
    * {@inheritDoc}
    */
   public long getGlobalDataSize() throws QuotaManagerException
   {
      return getDataSize(Fqn.ROOT);
   }

   /**
    * @inheritDoc}
    */
   public void setGlobalDataSize(long dataSize) throws QuotaManagerException
   {
      cache.put(DATA_SIZE, SIZE, dataSize);
   }

   /**
    * {@inheritDoc}
    */
   public void destroy()
   {
      try
      {
         ExoJBossCacheFactory.releaseUniqueInstance(CacheType.QUOTA_CACHE, cache);
      }
      catch (RepositoryConfigurationException e)
      {
         LOG.error("Can not release cache instance", e);
      }
   }

   private long getQuota(Fqn<String> relativeFqn) throws UnknownQuotaLimitException
   {
      Fqn<String> fqn = Fqn.fromRelativeFqn(QUOTA, relativeFqn);
      Long size = getSize(fqn);

      if (size == null)
      {
         throw new UnknownQuotaLimitException("Quota was not set early");
      }

      return size;
   }

   private long getDataSize(Fqn<String> relativeFqn) throws UnknownQuotaUsedException
   {
      Fqn<String> fqn = Fqn.fromRelativeFqn(DATA_SIZE, relativeFqn);
      Long size = getSize(fqn);

      if (size == null)
      {
         throw new UnknownQuotaUsedException("Data size is unknown");
      }

      return size;
   }

   private Long getSize(Fqn<String> fqn)
   {
      cache.getInvocationContext().getOptionOverrides().setForceWriteLock(true);
      Long size = (Long)cache.get(fqn, SIZE);

      return size;
   }

   /**
    * Checks if node with give FQN not exists and creates resident node.
    */
   private void createResidentNode(Fqn<String> fqn)
   {
      Node<Serializable, Object> cacheRoot = cache.getRoot();
      if (!cacheRoot.hasChild(fqn))
      {
         cache.getInvocationContext().getOptionOverrides().setCacheModeLocal(true);
         cacheRoot.addChild(fqn).setResident(true);
      }
      else
      {
         cache.getNode(fqn).setResident(true);
      }
   }
}
