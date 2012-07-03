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

import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.configuration.ConfigurationManager;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.services.jcr.config.MappedParametrizedObjectEntry;
import org.exoplatform.services.jcr.config.RepositoryConfigurationException;
import org.exoplatform.services.jcr.impl.quota.PathPatternUtils;
import org.exoplatform.services.jcr.impl.quota.QuotaManagerException;
import org.exoplatform.services.jcr.impl.quota.QuotaManagerImpl;
import org.exoplatform.services.jcr.impl.quota.UnknownQuotaLimitException;
import org.exoplatform.services.jcr.impl.quota.UnknownQuotaUsedException;
import org.exoplatform.services.jcr.jbosscache.ExoJBossCacheFactory;
import org.exoplatform.services.jcr.jbosscache.ExoJBossCacheFactory.CacheType;
import org.exoplatform.services.rpc.RPCService;
import org.jboss.cache.Cache;
import org.jboss.cache.Fqn;

import java.io.Serializable;
import java.util.Set;

import javax.jcr.RepositoryException;

/**
 * JBC implementation QuotamManager. Cache structure:
 * <ul>
 *    <li>$QUOTA_LIMIT</li>
 *    <li>$QUOTA_USED</li>
 * </ul>
 * 
 * @author <a href="abazko@exoplatform.com">Anatoliy Bazko</a>
 * @version $Id: JBCQuotaManagerImpl.java 34360 2009-07-22 23:58:59Z tolusha $
 */
public class JBCQuotaManagerImpl extends QuotaManagerImpl
{

   private Cache<Serializable, Object> cache;

   /**
    * Based region where allowed quota sized is stored. Should not be covered by eviction.
    */
   protected static final Fqn<String> QUOTA_LIMIT = Fqn.fromElements("$QUOTA_LIMIT");

   /**
    * Based region where used quota sized is stored. May be covered by eviction.
    */
   protected static final Fqn<String> QUOTA_USED = Fqn.fromElements("$QUOTA_USED");

   /**
    * Key name.
    */
   protected static final String SIZE = "$SIZE";

   /**
    * Relative element name.
    */
   protected static final String QUOTA_LIMIT_PATHES = "$PATHES";

   /**
    * Relative element name.
    */
   protected static final String QUOTA_LIMIT_PATTERNS = "$PATTERNS";

   /**
    * JBCQuotaManagerImpl constructor.
    */
   public JBCQuotaManagerImpl(InitParams initParams, ExoContainerContext ctx, RPCService rpcService,
      ConfigurationManager cfm) throws QuotaManagerException, RepositoryConfigurationException
   {
      super(initParams, rpcService, cfm);

      // create cache using custom factory
      ExoJBossCacheFactory<Serializable, Object> factory = new ExoJBossCacheFactory<Serializable, Object>(cfm);

      try
      {
         MappedParametrizedObjectEntry qmEntry = Utils.prepareJBCParameters(initParams);

         cache = factory.createCache(qmEntry);
         cache = ExoJBossCacheFactory.getShareableUniqueInstanceWithoutEviction(CacheType.QUOTA_CACHE, cache);
         cache.create();
         cache.start();
      }
      catch (RepositoryException e)
      {
         throw new QuotaManagerException(e.getMessage(), e);
      }
   }

   /**
    * JBCQuotaManagerImpl constructor.
    */
   public JBCQuotaManagerImpl(InitParams initParams, ExoContainerContext ctx, ConfigurationManager cfm)
      throws QuotaManagerException, RepositoryConfigurationException
   {
      this(initParams, ctx, null, cfm);
   }


   /**
    * {@inheritDoc}
    */
   public void stop()
   {
      try
      {
         ExoJBossCacheFactory.releaseUniqueInstance(CacheType.QUOTA_CACHE, cache);
      }
      catch (RepositoryConfigurationException e)
      {
         LOG.error("Can not release cache instance", e);
      }

      super.stop();
   }

   /**
    * {@inheritDoc}
    */
   public long getNodeQuota(String repositoryName, String workspaceName, String nodePath) throws QuotaManagerException
   {
      Fqn<String> relativeFqn = Fqn.fromElements(repositoryName, workspaceName, QUOTA_LIMIT_PATHES, nodePath);
      try
      {
         return getQuotaLimit(relativeFqn);
      }
      catch (UnknownQuotaLimitException e)
      {
         Fqn fqn = Fqn.fromRelativeElements(QUOTA_LIMIT, repositoryName, workspaceName, QUOTA_LIMIT_PATTERNS);

         Set<Object> children = cache.getNode(fqn).getChildrenNames();
         for (Object pattern : children)
         {
            if (PathPatternUtils.matches((String)pattern, nodePath, false))
            {
               return getQuotaLimit(fqn);
            }
         }

         throw e;
      }
   }

   /**
    * {@inheritDoc}
    */
   public long getNodeDataSize(String repositoryName, String workspaceName, String nodePath)
      throws QuotaManagerException
   {
      try
      {
         Fqn<String> relativeFqn = Fqn.fromElements(repositoryName, workspaceName, nodePath);
         return getQuotaUsed(relativeFqn);
      }
      catch (UnknownQuotaUsedException e)
      {
         return super.getNodeDataSize(repositoryName, workspaceName, nodePath);
      }
   }

   /**
    * {@inheritDoc}
    */
   public long getWorkspaceQuota(String repositoryName, String workspaceName) throws QuotaManagerException
   {
      Fqn<String> relativeFqn = Fqn.fromElements(repositoryName, workspaceName);
      return getQuotaLimit(relativeFqn);
   }

   /**
    * {@inheritDoc}
    */
   public long getWorkspaceDataSize(String repositoryName, String workspaceName) throws QuotaManagerException
   {
      try
      {
         Fqn<String> relativeFqn = Fqn.fromElements(repositoryName, workspaceName);
         return getQuotaUsed(relativeFqn);
      }
      catch (UnknownQuotaUsedException e)
      {
         return super.getWorkspaceDataSize(repositoryName, workspaceName);
      }
   }

   /**
    * {@inheritDoc}
    */
   public long getRepositoryQuota(String repositoryName) throws QuotaManagerException
   {
      Fqn<String> relativeFqn = Fqn.fromElements(repositoryName);
      return getQuotaLimit(relativeFqn);
   }

   /**
    * {@inheritDoc}
    */
   public long getRepositoryDataSize(String repositoryName) throws QuotaManagerException
   {
      try
      {
         Fqn<String> relativeFqn = Fqn.fromElements(repositoryName);
         return getQuotaUsed(relativeFqn);
      }
      catch (UnknownQuotaUsedException e)
      {
         return super.getRepositoryDataSize(repositoryName);
      }
   }

   /**
    * {@inheritDoc}
    */
   public long getGlobalQuota() throws QuotaManagerException
   {
      return getQuotaLimit(Fqn.ROOT);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public long getGlobalDataSize() throws QuotaManagerException
   {
      try
      {
         return getQuotaUsed(Fqn.ROOT);
      }
      catch (UnknownQuotaUsedException e)
      {
         return super.getGlobalDataSize();
      }
   }

   private long getQuotaLimit(Fqn relativeFqn) throws UnknownQuotaLimitException
   {
      Fqn<String> fqn = Fqn.fromRelativeFqn(QUOTA_LIMIT, relativeFqn);
      Long size = getSize(fqn);

      if (size == null)
      {
         throw new UnknownQuotaLimitException("Quota limit was not set early");
      }

      return size;
   }


   private long getQuotaUsed(Fqn relativeFqn) throws UnknownQuotaUsedException
   {
      Fqn<String> fqn = Fqn.fromRelativeFqn(QUOTA_USED, relativeFqn);
      Long size = getSize(fqn);

      if (size == null)
      {
         throw new UnknownQuotaUsedException("Quota used is unknown");
      }

      return size;
   }

   private Long getSize(Fqn<String> fqn)
   {
      cache.getInvocationContext().getOptionOverrides().setForceWriteLock(true);
      Long size = (Long)cache.get(fqn, SIZE);

      return size;
   }
}
