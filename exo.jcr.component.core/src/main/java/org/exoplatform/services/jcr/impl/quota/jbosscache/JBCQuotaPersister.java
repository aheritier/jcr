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
import org.exoplatform.services.jcr.config.MappedParametrizedObjectEntry;
import org.exoplatform.services.jcr.config.RepositoryConfigurationException;
import org.exoplatform.services.jcr.impl.backup.BackupException;
import org.exoplatform.services.jcr.impl.dataflow.serialization.ZipObjectReader;
import org.exoplatform.services.jcr.impl.dataflow.serialization.ZipObjectWriter;
import org.exoplatform.services.jcr.impl.quota.PathPatternUtils;
import org.exoplatform.services.jcr.impl.quota.QuotaPersister;
import org.exoplatform.services.jcr.impl.quota.UnknownQuotaDataSizeException;
import org.exoplatform.services.jcr.impl.quota.UnknownQuotaLimitException;
import org.exoplatform.services.jcr.jbosscache.ExoJBossCacheFactory;
import org.exoplatform.services.jcr.jbosscache.ExoJBossCacheFactory.CacheType;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.jboss.cache.Cache;
import org.jboss.cache.Fqn;
import org.jboss.cache.Node;

import java.io.IOException;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

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
   protected Cache<Serializable, Object> cache;

   /**
    * Backup, restore, clean utility.
    */
   protected final JBCBackupQuota backupUtil;

   /**
    * Based region where allowed quota sized is stored. Should not be covered by eviction.
    */
   public static final Fqn<String> QUOTA = Fqn.fromElements("$QUOTA");

   /**
    * Based region where used quota sized is stored. May be covered by eviction.
    */
   public static final Fqn<String> DATA_SIZE = Fqn.fromElements("$DATA_SIZE");

   /**
    * Key name.
    */
   public static final String SIZE = "$SIZE";

   /**
    * Key name.
    */
   public static final String ASYNC_UPATE = "$ASYNC_UPATE";

   /**
    * Relative element name.
    */
   public static final String QUOTA_PATHS = "$PATHS";

   /**
    * Relative element name.
    */
   public static final String QUOTA_PATTERNS = "$PATTERNS";

   /**
    * JBCQuotaPersister constructor.
    */
   protected JBCQuotaPersister(MappedParametrizedObjectEntry entry, ConfigurationManager cfm)
      throws RepositoryConfigurationException
   {
      // create cache using custom factory
      ExoJBossCacheFactory<Serializable, Object> factory = new ExoJBossCacheFactory<Serializable, Object>(cfm);

      cache = factory.createCache(entry);
      cache = ExoJBossCacheFactory.getShareableUniqueInstanceWithoutEviction(CacheType.QUOTA_CACHE, cache);
      cache.create();
      cache.start();

      createResidentNode(QUOTA);
      createResidentNode(DATA_SIZE);

      backupUtil = new JBCBackupQuota(cache);
   }

   /**
    * {@inheritDoc}
    */
   public long getNodeQuotaByPathOrPattern(String repositoryName, String workspaceName, String nodePath)
      throws UnknownQuotaLimitException
   {
      nodePath = escaping(nodePath);

      try
      {
         Fqn<String> fqn = Fqn.fromRelativeElements(QUOTA, repositoryName, workspaceName, QUOTA_PATHS, nodePath);
         return getQuota(fqn);
      }
      catch (UnknownQuotaLimitException e)
      {
         Fqn<String> fqnParentQuotaPattern = Fqn.fromRelativeElements(QUOTA, repositoryName, workspaceName, QUOTA_PATTERNS);

         Set<Object> children = cache.getChildrenNames(fqnParentQuotaPattern);
         for (Object pattern : children)
         {
            if (PathPatternUtils.acceptName(unescaping((String)pattern), unescaping(nodePath)))
            {
               fqnParentQuotaPattern = Fqn.fromRelativeElements(QUOTA, repositoryName, workspaceName, QUOTA_PATTERNS, (String)pattern);
               return getQuota(fqnParentQuotaPattern);
            }
         }

         throw e;
      }
   }

   /**
    * {@inheritDoc}
    */
   public void setNodeQuota(String repositoryName, String workspaceName, String nodePath, long quotaLimit,
      boolean asyncUpdate)
   {
      nodePath = escaping(nodePath);

      Fqn<String> fqn = Fqn.fromRelativeElements(QUOTA, repositoryName, workspaceName, QUOTA_PATHS, nodePath);
      cache.put(fqn, SIZE, quotaLimit);
      cache.put(fqn, ASYNC_UPATE, asyncUpdate);
   }

   /**
    * {@inheritDoc}
    */
   public void setGroupOfNodeQuota(String repositoryName, String workspaceName, String patternPath, long quotaLimit,
      boolean asyncUpdate)
   {
      patternPath = escaping(patternPath);

      Fqn<String> fqn = Fqn.fromRelativeElements(QUOTA, repositoryName, workspaceName, QUOTA_PATTERNS, patternPath);
      cache.put(fqn, SIZE, quotaLimit);
      cache.put(fqn, ASYNC_UPATE, asyncUpdate);
   }

   /**
    * {@inheritDoc}
    */
   public void removeNodeQuota(String repositoryName, String workspaceName, String nodePath)
   {
      nodePath = escaping(nodePath);

      Fqn<String> fqn = Fqn.fromRelativeElements(QUOTA, repositoryName, workspaceName, QUOTA_PATHS, nodePath);
      cache.removeNode(fqn);

      // remove node data size only if only node path does't match any pattern
      Fqn<String> parent = Fqn.fromRelativeElements(QUOTA, repositoryName, workspaceName, QUOTA_PATTERNS);
      for (Object patternPath : cache.getChildrenNames(parent))
      {
         if (PathPatternUtils.acceptName(unescaping((String)patternPath), unescaping(nodePath)))
         {
            return;
         }
      }

      fqn = Fqn.fromRelativeElements(DATA_SIZE, repositoryName, workspaceName, nodePath);
      cache.removeNode(fqn);
   }

   /**
    * {@inheritDoc}
    */
   public Set<String> removeGroupOfNodesQuota(String repositoryName, String workspaceName, String patternPath)
   {
      patternPath = escaping(patternPath);

      Set<String> removedPaths = new HashSet<String>();

      Fqn<String> fqn = Fqn.fromRelativeElements(QUOTA, repositoryName, workspaceName, QUOTA_PATTERNS, patternPath);
      cache.removeNode(fqn);
      
      // removes data size for all nodes matched by pattern
      // only if only quota was not set explicitly by setQuota() method 
      Fqn<String> fqnParentDataSize = Fqn.fromRelativeElements(DATA_SIZE, repositoryName, workspaceName);
      for (Object nodePath : cache.getChildrenNames(fqnParentDataSize))
      {
         if (PathPatternUtils.acceptName(unescaping(patternPath), unescaping((String)nodePath)))
         {
            Fqn<String> fqnQuota =
               Fqn.fromRelativeElements(QUOTA, repositoryName, workspaceName, QUOTA_PATHS, (String)nodePath);

            if (cache.getNode(fqnQuota) == null)
            {
               cache.removeNode(Fqn.fromRelativeElements(fqnParentDataSize, (String)nodePath));
               removedPaths.add(unescaping((String)nodePath));
            }
         }
      }

      return removedPaths;
   }
   
   /**
    * {@inheritDoc}
    */
   public Set<String> getAlertedPaths(String repositoryName, String workspaceName)
   {
      Set<String> result = new HashSet<String>();

      Fqn<String> fqnParentDataSize = Fqn.fromRelativeElements(DATA_SIZE, repositoryName, workspaceName);
      for (Object nodePath : cache.getChildrenNames(fqnParentDataSize))
      {
         Fqn<String> fqnDataSize = Fqn.fromRelativeElements(fqnParentDataSize, (String)nodePath);

         // check quota by path
         Fqn<String> fqnQuota =
            Fqn.fromRelativeElements(QUOTA, repositoryName, workspaceName, QUOTA_PATHS, (String)nodePath);
         Node quotaNoda = cache.getNode(fqnQuota);
         if (quotaNoda != null)
         {
            long quotaLimit = (Long)quotaNoda.get(SIZE);
            long dataSize = (Long)cache.get(fqnDataSize, SIZE);

            if (quotaLimit > dataSize)
            {
               result.add(unescaping((String)nodePath));
            }

            continue;
         }

         // check quota by pattern
         Fqn<String> fqnParentPattern = Fqn.fromRelativeElements(QUOTA, repositoryName, workspaceName, QUOTA_PATTERNS);
         for (Object patternPath : cache.getChildrenNames(fqnParentPattern))
         {
            if (PathPatternUtils.acceptName(unescaping((String)patternPath), unescaping((String)nodePath)))
            {
               Fqn<String> fqnPathern = Fqn.fromRelativeElements(fqnParentPattern, (String)patternPath);

               long quotaLimit = (Long)cache.get(fqnPathern, SIZE);
               long dataSize = (Long)cache.get(fqnDataSize, SIZE);

               if (quotaLimit > dataSize)
               {
                  result.add(unescaping((String)nodePath));
                  break;
               }
            }
         }
      }

      return result;
   }

   /**
    * {@inheritDoc}
    */
   public long getNodeDataSize(String repositoryName, String workspaceName, String nodePath)
      throws UnknownQuotaDataSizeException
   {
      nodePath = escaping(nodePath);

      Fqn<String> fqn = Fqn.fromRelativeElements(DATA_SIZE, repositoryName, workspaceName, nodePath);
      return getDataSize(fqn);
   }

   /**
    * {@inheritDoc}
    */
   public void setNodeDataSize(String repositoryName, String workspaceName, String nodePath, long dataSize)
   {
      nodePath = escaping(nodePath);

      Fqn<String> fqn = Fqn.fromRelativeElements(DATA_SIZE, repositoryName, workspaceName, nodePath);
      cache.put(fqn, SIZE, dataSize);
   }

   /**
    * {@inheritDoc}
    */
   public long getWorkspaceQuota(String repositoryName, String workspaceName) throws UnknownQuotaLimitException
   {
      Fqn<String> fqn = Fqn.fromRelativeElements(QUOTA, repositoryName, workspaceName);
      return getQuota(fqn);
   }

   /**
    * {@inheritDoc}
    */
   public void setWorkspaceQuota(String repositoryName, String workspaceName, long quotaLimit)
   {
      Fqn<String> fqn = Fqn.fromRelativeElements(QUOTA, repositoryName, workspaceName);
      cache.put(fqn, SIZE, quotaLimit);
   }

   /**
    * {@inheritDoc}
    */
   public void removeWorkspaceQuota(String repositoryName, String workspaceName)
   {
      Fqn<String> fqn = Fqn.fromRelativeElements(QUOTA, repositoryName, workspaceName);
      cache.remove(fqn, SIZE);
   }

   /**
    * {@inheritDoc}
    */
   public void clearWorkspaceData(String repositoryName, String workspaceName)
   {
      backupUtil.clearWorkspaceData(repositoryName, workspaceName);
   }

   /**
    * {@inheritDoc}
    * @throws IOException 
    */
   public void backupWorkspaceData(String repositoryName, String workspaceName, ZipObjectWriter writer)
      throws BackupException
   {
      try
      {
         backupUtil.backupWorkspaceData(repositoryName, workspaceName, writer);
      }
      catch (IOException e)
      {
         throw new BackupException(e.getMessage(), e);
      }
   }

   /**
    * {@inheritDoc}
    */
   public void restoreWorkspaceData(String repositoryName, String workspaceName, ZipObjectReader reader)
      throws BackupException
   {
      try
      {
         backupUtil.restoreWorkspaceData(repositoryName, workspaceName, reader);
      }
      catch (IOException e)
      {
         throw new BackupException(e.getMessage(), e);
      }
   }

   /**
    * {@inheritDoc}
    */
   public void setWorkspaceDataSize(String repositoryName, String workspaceName, long dataSize)
   {
      Fqn<String> fqn = Fqn.fromRelativeElements(DATA_SIZE, repositoryName, workspaceName);
      cache.put(fqn, SIZE, dataSize);
   }

   /**
    * {@inheritDoc}
    */
   public long getWorkspaceDataSize(String repositoryName, String workspaceName) throws UnknownQuotaDataSizeException
   {
      Fqn<String> fqn = Fqn.fromRelativeElements(DATA_SIZE, repositoryName, workspaceName);
      return getDataSize(fqn);
   }

   /**
    * {@inheritDoc}
    */
   public long getRepositoryDataSize(String repositoryName) throws UnknownQuotaDataSizeException
   {
      Fqn<String> fqn = Fqn.fromRelativeElements(DATA_SIZE, repositoryName);
      return getDataSize(fqn);
   }

   /**
    * {@inheritDoc}
    */
   public void setRepositoryDataSize(String repositoryName, long dataSize)
   {
      Fqn<String> fqn = Fqn.fromRelativeElements(DATA_SIZE, repositoryName);
      cache.put(fqn, SIZE, dataSize);
   }

   /**
    * {@inheritDoc}
    */
   public long getRepositoryQuota(String repositoryName) throws UnknownQuotaLimitException
   {
      Fqn<String> fqn = Fqn.fromRelativeElements(QUOTA, repositoryName);
      return getQuota(fqn);
   }

   /**
    * {@inheritDoc}
    */
   public void setRepositoryQuota(String repositoryName, long quotaLimit)
   {
      Fqn<String> fqn = Fqn.fromRelativeElements(QUOTA, repositoryName);
      cache.put(fqn, SIZE, quotaLimit);
   }

   /**
    * {@inheritDoc}
    */
   public void removeRepositoryQuota(String repositoryName)
   {
      Fqn<String> fqn = Fqn.fromRelativeElements(QUOTA, repositoryName);
      cache.remove(fqn, SIZE);
   }

   /**
    * {@inheritDoc}
    */
   public long getGlobalQuota() throws UnknownQuotaLimitException
   {
      return getQuota(QUOTA);
   }

   /**
    * {@inheritDoc}
    */
   public void setGlobalQuota(long quotaLimit)
   {
      cache.put(QUOTA, SIZE, quotaLimit);
   }

   /**
    * {@inheritDoc}
    */
   public void removeGlobalQuota()
   {
      cache.remove(QUOTA, SIZE);
   }

   /**
    * {@inheritDoc}
    */
   public long getGlobalDataSize() throws UnknownQuotaDataSizeException
   {
      return getDataSize(DATA_SIZE);
   }

   /**
    * @inheritDoc}
    */
   public void setGlobalDataSize(long dataSize)
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

   private long getQuota(Fqn<String> fqn) throws UnknownQuotaLimitException
   {
      cache.getInvocationContext().getOptionOverrides().setForceWriteLock(true);
      Long size = (Long)cache.get(fqn, SIZE);

      if (size == null)
      {
         throw new UnknownQuotaLimitException("Quota was not set early");
      }

      return size;
   }

   private long getDataSize(Fqn<String> fqn) throws UnknownQuotaDataSizeException
   {
      cache.getInvocationContext().getOptionOverrides().setForceWriteLock(true);
      Long size = (Long)cache.get(fqn, SIZE);

      if (size == null)
      {
         throw new UnknownQuotaDataSizeException("Data size is unknown");
      }

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

   private String escaping(String path)
   {
      return path.replace(Fqn.SEPARATOR, "\\");
   }

   private String unescaping(String path)
   {
      return path.replace("\\", Fqn.SEPARATOR);
   }
}
