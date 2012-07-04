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
import org.exoplatform.services.jcr.config.RepositoryConfigurationException;
import org.exoplatform.services.jcr.impl.quota.QuotaManagerException;
import org.exoplatform.services.jcr.impl.quota.AbstractQuotaManager;
import org.exoplatform.services.jcr.impl.quota.QuotaPersister;
import org.exoplatform.services.rpc.RPCService;

/**
 * JBC implementation QuotamManager.
 * 
 * @author <a href="abazko@exoplatform.com">Anatoliy Bazko</a>
 * @version $Id: JBCQuotaManagerImpl.java 34360 2009-07-22 23:58:59Z tolusha $
 */
public class JBCQuotaManagerImpl extends AbstractQuotaManager
{

   /**
    * JBCQuotaManagerImpl constructor.
    */
   public JBCQuotaManagerImpl(InitParams initParams, RPCService rpcService, ConfigurationManager cfm)
      throws RepositoryConfigurationException, QuotaManagerException
   {
      super(initParams, rpcService, cfm);
   }

   /**
    * JBCQuotaManagerImpl constructor.
    */
   public JBCQuotaManagerImpl(InitParams initParams, ConfigurationManager cfm) throws RepositoryConfigurationException,
      QuotaManagerException
   {
      this(initParams, null, cfm);
   }

   /**
    * {@inheritDoc}
    */
   protected QuotaPersister initQuotaPersister() throws RepositoryConfigurationException, QuotaManagerException
   {
      return new JBCQuotaPersister(initParams, cfm);
   }
}
