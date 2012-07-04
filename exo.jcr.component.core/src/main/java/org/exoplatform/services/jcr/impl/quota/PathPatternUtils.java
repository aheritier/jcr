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


import java.util.regex.Pattern;

/**
 * Node absolute path pattern. Supports such elements:</br>
 * <code>*</code>: any node name</br>
 * <code>%</code>: any character</br>
 * 
 * @author <a href="abazko@exoplatform.com">Anatoliy Bazko</a>
 * @version $Id: PathPatternUtils.java 34360 2009-07-22 23:58:59Z tolusha $
 */
public class PathPatternUtils
{
   public static final String ANY_NAME = "*";

   public static final String ANY_CHAR = "%";

   /**
    * Adopts pattern introduced in simple user friendly form to Java {@link Pattern}.
    */
   private static String adopt2JavaPattern(String pattern)
   {
      pattern = normalizePath(pattern);

      // any character except '/', one or more times
      pattern = pattern.replaceAll("\\" + ANY_NAME, "[^/]+");

      // any character except '/' exactly one time
      pattern = pattern.replaceAll(ANY_CHAR, "[^/]{1}");

      return pattern;
   }

   /**
    * Returns <code>true</code> if a specified path matches by pattern.
    * 
    * @param pattern
    *          pattern for node path
    * @param absPath
    *          node absolute path
    * @param isDeep
    *          indicates if children nodes can be matched by pattern or not
    * 
    * @return a <code>boolean</code>.
    */
   public static boolean matches(String pattern, String absPath, boolean isDeep)
   {
      absPath = normalizePath(absPath);
      pattern = adopt2JavaPattern(pattern);

      if (isDeep)
      {
         // allows any children after
         pattern += "(/.+)?";
      }

      return absPath.matches(pattern);
   }

   /**
    * Normalizes path. Returns string without character </code>/</code> at the end.
    */
   private static String normalizePath(String absPath)
   {
      if (absPath.endsWith("/"))
      {
         return absPath.substring(0, absPath.length() - 1);
      }

      return absPath;
   }
}
