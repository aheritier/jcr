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
package org.exoplatform.services.jcr.config;

import org.exoplatform.container.configuration.ConfigurationManager;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;

/**
 * Builds configuration from template using map of template-variables <--> value.
 * Class provides extra functionality for filtering parameters by pattern, excluding 
 * unnecessary parameters. 
 * 
 * @author <a href="mailto:nikolazius@gmail.com">Nikolay Zamosenchuk</a>
 * @version $Id: TemplateConfigurationHelper.java 34360 2009-07-22 23:58:59Z nzamosenchuk $
 *
 */
public class TemplateConfigurationHelper
{
   // list with include-patterns
   private List<Pattern> includes = new ArrayList<Pattern>();

   // list with exclude-patterns
   private List<Pattern> excludes = new ArrayList<Pattern>();

   private ConfigurationManager cfm;

   /**
    * Creates instance of template configuration helper with given lists of filtering 
    * patterns. Parameter will be included only if it matches any include-pattern and
    * doesn't match any exclude-pattern. I.e. You can include "extended-*" and exclude
    * "extended-type". Please refer to Java regexp documentation. Filtering for this
    * example, should be defined as following:
    * include: "^extended-.*"
    * exclude: "^extended-type"
    * 
    * @param includes Array with string representation of include reg-exp patterns
    * @param excludes Array with string representation of exclude reg-exp patterns
    * @param ConfigurationManager instance for looking up resources
    */
   public TemplateConfigurationHelper(String[] includes, String[] excludes, ConfigurationManager cfm)
   {
      super();
      this.cfm = cfm;
      // compile include patterns
      for (String regex : includes)
      {
         this.includes.add(Pattern.compile(regex));
      }
      // compile exclude patterns
      for (String regex : excludes)
      {
         this.excludes.add(Pattern.compile(regex));
      }
   }

   /**
    * Creates instance of TemplateConfigurationHelper pre-configured for JBossCache parameters,<br>
    * including: "jbosscache-*" and "jgroups-configuration", and excluding "jbosscache-configuration"
    * 
    * @param ConfigurationManager instance for looking up resources
    * @return
    */
   public static TemplateConfigurationHelper createJBossCacheHelper(ConfigurationManager cfm)
   {
      return new TemplateConfigurationHelper(new String[]{"^jbosscache-.*", "^jgroups-configuration"},
         new String[]{"^jbosscache-configuration"}, cfm);
   }

   /**
    * Reads configuration file from a stream and replaces all the occurrences of template-variables 
    * (like : "${parameter.name}") with values provided in the map. 
    * 
    * @param inputStream
    * @param parameters
    * @return
    * @throws IOException
    */
   public InputStream fillTemplate(InputStream inputStream, Map<String, String> parameters) throws IOException
   {
      if (inputStream == null || parameters == null || parameters.size() == 0)
      {
         return inputStream;
      }
      // parameters filtering
      Map<String, String> preparedParams = prepareParameters(parameters);
      // read stream
      String configuration = readStream(inputStream);
      for (Entry<String, String> entry : preparedParams.entrySet())
      {
         configuration = configuration.replace(entry.getKey(), entry.getValue());
      }
      // create new stream
      InputStream configurationStream = new ByteArrayInputStream(configuration.getBytes());
      return configurationStream;
   }

   /**
    * Reads configuration file from a stream and replaces all the occurrences of template-variables 
    * (like : "${parameter.name}") with values provided in the map. 
    * 
    * @param filename
    * @param parameters
    * @return
    * @throws IOException
    */
   public InputStream fillTemplate(String filename, Map<String, String> parameters) throws IOException
   {
      InputStream inputStream = null;
      // try to get using configuration manager
      try
      {
         inputStream = cfm.getInputStream(filename);
      }
      catch (Exception e)
      {
         // will try to use another resolve mechanism 
      }

      // try to get resource by class loader
      if (inputStream == null)
      {
         ClassLoader cl = Thread.currentThread().getContextClassLoader();
         inputStream = cl == null ? null : cl.getResourceAsStream(filename);
      }
      
      // check system class loader
      if (inputStream == null)
      {
         inputStream = getClass().getClassLoader().getResourceAsStream(filename);
      }

      // try to get as file stream
      if (inputStream == null)
      {
         try
         {
            inputStream = new FileInputStream(filename);
         }
         catch (IOException e)
         {
            // Still can't resolve
         }
      }
      // inputStream still remains null, so file was not opened
      if (inputStream == null)
      {
         throw new IOException("Can't find or open file:" + filename);
      }
      return fillTemplate(inputStream, parameters);
   }

   /**
    * Reads configuration file from a stream and replaces all the occurrences of template-variables 
    * (like : "${parameter.name}") with values provided in the map. 
    * 
    * @param inputStream
    * @param parameters
    * @return
    * @throws IOException
    */
   public InputStream fillTemplate(InputStream inputStream, List<SimpleParameterEntry> parameters) throws IOException
   {
      Map<String, String> map = new HashMap<String, String>();
      for (SimpleParameterEntry parameterEntry : parameters)
      {
         map.put(parameterEntry.getName(), parameterEntry.getValue());
      }
      return fillTemplate(inputStream, map);
   }

   /**
    * Reads configuration file from file-system and replaces all the occurrences of template-variables 
    * (like : "${parameter.name}") with values provided in the map. 
    * 
    * @param filename
    * @param parameters
    * @return
    * @throws IOException
    */
   public InputStream fillTemplate(String filename, List<SimpleParameterEntry> parameters) throws IOException
   {
      Map<String, String> map = new HashMap<String, String>();
      for (SimpleParameterEntry parameterEntry : parameters)
      {
         map.put(parameterEntry.getName(), parameterEntry.getValue());
      }
      return fillTemplate(filename, map);
   }

   /**
    * Checks if String mathes to any pattern from the list
    * 
    * @param patterns
    * @param parameter
    * @return
    */
   private boolean matches(List<Pattern> patterns, String parameter)
   {
      for (Pattern pattern : patterns)
      {
         if (pattern.matcher(parameter).matches())
         {
            // string matched
            return true;
         }
      }
      return false;
   }

   /**
    * Filters the map of parameters, leaving only those than matches filtering regular expressions.
    * Also adds "${}" to the parameter key: <br>
    * I.e. such map provided on input:
    * 
    * "jbosscache-cache.loader":"org.exoplatform"
    * "jbosscache-configuration":"/conf/test.xml"
    * "max-volatile-size":"100Kb"
    * 
    * the output will be like:
    * 
    * "${jbosscache-cache.loader}":"org.exoplatform"
    * 
    * Other will be ignored (depending on includes/excludes lists provided in constructor).
    * 
    * @param parameters
    * @return
    */
   protected Map<String, String> prepareParameters(Map<String, String> parameters)
   {
      Map<String, String> map = new HashMap<String, String>();
      for (Entry<String, String> entry : parameters.entrySet())
      {
         if (matches(includes, entry.getKey()) && !matches(excludes, entry.getKey()))
         {
            map.put("${" + entry.getKey() + "}", entry.getValue());
         }
      }
      return map;
   }

   /**
    * Reads bytes from input stream and builds a string from them
    * 
    * @param inputStream
    * @return
    * @throws IOException
    */
   protected String readStream(InputStream inputStream) throws IOException
   {
      StringBuffer out = new StringBuffer();
      byte[] b = new byte[4096];
      for (int n; (n = inputStream.read(b)) != -1;)
      {
         out.append(new String(b, 0, n));
      }
      return out.toString();
   }
}
