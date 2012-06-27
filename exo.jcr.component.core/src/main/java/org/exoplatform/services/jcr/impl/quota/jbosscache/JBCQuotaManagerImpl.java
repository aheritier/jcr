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

import org.exoplatform.container.xml.InitParams;
import org.exoplatform.services.jcr.impl.quota.QuotaManagerImpl;
import org.exoplatform.services.rpc.RPCService;

/**
 * @author <a href="abazko@exoplatform.com">Anatoliy Bazko</a>
 * @version $Id: JBCQuotaManagerImpl.java 34360 2009-07-22 23:58:59Z tolusha $
 */
public class JBCQuotaManagerImpl extends QuotaManagerImpl
{

   //   <property name="jbosscache-configuration" value="test-jbosscache-lock.xml" />
   //   <property name="jgroups-configuration" value="udp-mux.xml" />
   //   <property name="jgroups-multiplexer-stack" value="false" />
   //   <property name="jbosscache-cluster-name" value="JCR-cluster-locks" />
   //   <property name="jbosscache-cl-cache.jdbc.table.name" value="jcrlocks" />
   //   <property name="jbosscache-cl-cache.jdbc.table.create" value="true" />
   //   <property name="jbosscache-cl-cache.jdbc.table.drop" value="false" />
   //   <property name="jbosscache-cl-cache.jdbc.table.primarykey" value="jcrlocks_pk" />
   //   <property name="jbosscache-cl-cache.jdbc.fqn.column" value="fqn" />
   //   <property name="jbosscache-cl-cache.jdbc.node.column" value="node" />
   //   <property name="jbosscache-cl-cache.jdbc.parent.column" value="parent" />
   //   <property name="jbosscache-cl-cache.jdbc.datasource" value="jdbcjcr" />
   //   <property name="jbosscache-shareable" value="${jbosscache-shareable}" />

   /**
    * JBCQuotaManagerImpl constructor.
    */
   public JBCQuotaManagerImpl(InitParams initParams, RPCService rpcService)
   {
      super(initParams, rpcService);
   }

   /**
    * JBCQuotaManagerImpl constructor.
    */
   public JBCQuotaManagerImpl(InitParams initParams)
   {
      super(initParams);
   }


}
