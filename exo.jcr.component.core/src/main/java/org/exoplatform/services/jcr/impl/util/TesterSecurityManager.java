/*
 * Copyright (C) 2010 eXo Platform SAS.
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
package org.exoplatform.services.jcr.impl.util;

import java.security.Permission;

/**
 * @author <a href="anatoliy.bazko@exoplatform.org">Anatoliy Bazko</a>
 * @version $Id: TesterSecurityManager.java 111 2010-11-11 11:11:11Z tolusha $
 */
public class TesterSecurityManager extends SecurityManager
{

   /**
    * {@inheritDoc}
    */
   @Override
   public void checkPermission(Permission perm)
   {
      try
      {
         super.checkPermission(perm);
      }
      catch (SecurityException se)
      {
         Throwable e = se;

         boolean srcCode = false;
         boolean testCode = false;

         while (e != null)
         {
            StackTraceElement[] traceElements = e.getStackTrace();
            for (int i = 0; i < traceElements.length; i++)
            {
               String className = traceElements[i].getClassName();
               String fileName = traceElements[i].getFileName();
               String methodName = traceElements[i].getMethodName();

               if (className.startsWith("org.exoplatform"))
               {
                  // TesterSecurityManager is not a part of source code
                  if (fileName.equals("TesterSecurityManager.java"))
                  {
                     continue;
                  }

                  // hide Exception
                  if (fileName.equals("BaseStandaloneTest.java"))
                  {
                     return;
                  }

                  if (fileName.startsWith("Test") || fileName.endsWith("Test.java")
                     || fileName.endsWith("TestBase.java") || fileName.equals("Probe.java"))
                  {
                     testCode = true;
                  }
                  else
                  {
                     srcCode = true;
                  }
               }
               else if (className.startsWith("org.apache.jackrabbit.test"))
               {
                  // hide Exception 
                  if (fileName.equals("RepositoryHelper.java") && methodName.equals("getRepository"))
                  {
                     return;
                  }

                  if (fileName.endsWith("Test.java") || fileName.equals("JCRTestResult.java"))
                  {
                     testCode = true;
                  }
                  else
                  {
                     srcCode = true;
                  }
               }
               else if (className.startsWith("org.slf4j.impl.Log4jLoggerFactory"))
               {
                  return;
               }
            }

            e = e.getCause();
         }

         // hide Exception if only test code exists
         if (!srcCode && testCode)
         {
            return;
         }

         throw se;
      }
   }
}
