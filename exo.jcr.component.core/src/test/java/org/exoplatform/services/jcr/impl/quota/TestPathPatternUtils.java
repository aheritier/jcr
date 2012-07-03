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

import junit.framework.TestCase;

/**
 * @author <a href="abazko@exoplatform.com">Anatoliy Bazko</a>
 * @version $Id: TestNodePathPattern.java 34360 2009-07-22 23:58:59Z tolusha $
 */
public class TestPathPatternUtils extends TestCase
{
   /**
    * Pattern testing.
    */
   public void testPattern() throws Exception
   {
      assertTrue(PathPatternUtils.matches("/", "/", true));
      assertTrue(PathPatternUtils.matches("/", "/a", true));
      assertTrue(PathPatternUtils.matches("/", "/a/c", true));

      assertTrue(PathPatternUtils.matches("/a", "/a", true));
      assertTrue(PathPatternUtils.matches("/a", "/a/c", true));
      assertTrue(PathPatternUtils.matches("/a", "/a/c/", true));
      assertFalse(PathPatternUtils.matches("/a", "/b", true));
      assertFalse(PathPatternUtils.matches("/a", "/b/c", true));
      assertFalse(PathPatternUtils.matches("/a", "/", true));

      assertTrue(PathPatternUtils.matches("/a/b", "/a/b", true));
      assertTrue(PathPatternUtils.matches("/a/b", "/a/b/c", true));
      assertTrue(PathPatternUtils.matches("/a/b", "/a/b/c/", true));
      assertFalse(PathPatternUtils.matches("/a/b", "/a/c", true));
      assertFalse(PathPatternUtils.matches("/a/b", "/a", true));
      assertFalse(PathPatternUtils.matches("/a/b", "/", true));

      assertTrue(PathPatternUtils.matches("/*", "/aaa", true));
      assertTrue(PathPatternUtils.matches("/*", "/aaa/bbb", true));
      assertFalse(PathPatternUtils.matches("/*", "/", true));

      assertTrue(PathPatternUtils.matches("/a/*", "/a/bbb", true));
      assertTrue(PathPatternUtils.matches("/a/*", "/a/bbb/c", true));
      assertFalse(PathPatternUtils.matches("/a/*", "/c", true));
      assertFalse(PathPatternUtils.matches("/a/*", "/", true));

      assertTrue(PathPatternUtils.matches("/a/*/b/*", "/a/ccc/b/ddd", true));
      assertTrue(PathPatternUtils.matches("/a/*/b/*", "/a/ccc/b/ddd/ggg", true));
      assertFalse(PathPatternUtils.matches("/a/*/b/*", "/d/ccc/b/ddd", true));
      assertFalse(PathPatternUtils.matches("/a/*/b/*", "/a/ccc/e/ddd", true));
      assertFalse(PathPatternUtils.matches("/a/*/b/*", "/a/ccc/b", true));
      assertFalse(PathPatternUtils.matches("/a/*/b/*", "/a/ccc", true));
      assertFalse(PathPatternUtils.matches("/a/*/b/*", "/a", true));
      assertFalse(PathPatternUtils.matches("/a/*/b/*", "/", true));

      assertTrue(PathPatternUtils.matches("/%", "/a/c", true));
      assertTrue(PathPatternUtils.matches("/%", "/a", true));
      assertFalse(PathPatternUtils.matches("/%", "/aa", true));
      assertFalse(PathPatternUtils.matches("/%", "/aa/c", true));
      assertFalse(PathPatternUtils.matches("/%", "/", true));

      assertTrue(PathPatternUtils.matches("/%%%", "/aaa", true));
      assertTrue(PathPatternUtils.matches("/%%%", "/aaa/bbb", true));
      assertFalse(PathPatternUtils.matches("/%%%", "/aaaa", true));
      assertFalse(PathPatternUtils.matches("/%%%", "/a", true));
      assertFalse(PathPatternUtils.matches("/%%%", "/a/b/c", true));
      assertFalse(PathPatternUtils.matches("/%%%", "/", true));

      assertTrue(PathPatternUtils.matches("/a/%%", "/a/bb", true));
      assertTrue(PathPatternUtils.matches("/a/%%", "/a/bb/c", true));
      assertFalse(PathPatternUtils.matches("/a/%%", "/a/bbb", true));
      assertFalse(PathPatternUtils.matches("/a/%%", "/a", true));
      assertFalse(PathPatternUtils.matches("/a/%%", "/", true));

      assertTrue(PathPatternUtils.matches("/a/%%/b/*", "/a/cc/b/eee", true));
      assertTrue(PathPatternUtils.matches("/a/%%/b/*", "/a/cc/b/eee/ggg", true));
      assertFalse(PathPatternUtils.matches("/a/%%/b/*", "/a/cc/d/eee/ggg", true));
   }
}
