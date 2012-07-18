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
 * <code>/</code>: as only delimiter between entities
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
    * Returns <code>true</code> if a specified path matches by pattern
    * and has the same depth.
    * 
    * @param pattern
    *          pattern for node path
    * @param absPath
    *          node absolute path
    * @return a <code>boolean</code>.
    */
   public static boolean acceptName(String pattern, String absPath)
   {
      absPath = normalizePath(absPath);
      pattern = adopt2JavaPattern(pattern);

      return absPath.matches(pattern);
   }

   /**
    * Returns <code>true</code> if a specified path matches by pattern
    * and has not less elements.
    * 
    * @param pattern
    *          pattern for node path
    * @param absPath
    *          node absolute path
    * @return a <code>boolean</code>.
    */
   public static boolean acceptDescendant(String pattern, String absPath)
   {
      absPath = normalizePath(absPath);
      pattern = adopt2JavaPattern(pattern);

      // allows any children after
      pattern += "(/.+)?";

      return absPath.matches(pattern);
   }

   /**
    * Extract common ancestor.
    */
   public static String extractCommonAncestor(String pattern, String absPath)
   {
      pattern = normalizePath(pattern);
      absPath = normalizePath(absPath);
      
      String[] entries = absPath.split("/");
      
      StringBuilder ancestor = new StringBuilder();
      for (int i = 0; i < pattern.split("/").length; i++)
      {
         ancestor.append(entries[i]);
      }

      return ancestor.length() == 0 ? "/" : ancestor.toString();
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
