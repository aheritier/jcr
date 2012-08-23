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

import org.exoplatform.services.jcr.impl.backup.ResumeException;
import org.exoplatform.services.jcr.impl.backup.SuspendException;
import org.exoplatform.services.jcr.impl.backup.Suspendable;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author <a href="abazko@exoplatform.com">Anatoliy Bazko</a>
 * @version $Id: QuotaExecutorService.java 34360 Aug 23, 2012 tolusha $
 */
public class QuotaExecutorService implements ExecutorService, Suspendable
{

   /**
    * Logger.
    */
   protected final Log LOG = ExoLogger.getLogger("exo.jcr.component.core.QuotaExecutorService");

   /**
    * Delegated {@link ExecutorService}.
    */
   private ExecutorService delegated;

   /**
    * Indicates if component suspended or not.
    */
   protected final AtomicBoolean isSuspended = new AtomicBoolean();

   /**
    * QuotaExecutorService constructor.
    */
   public QuotaExecutorService(final String uniqueName)
   {
      delegated = Executors.newFixedThreadPool(1, new ThreadFactory()
      {
         public Thread newThread(Runnable arg0)
         {
            return new Thread(arg0, "QuotaManagerThread " + uniqueName);
         }
      });
   }

   /**
    * {@inheritDoc}
    */
   public void execute(Runnable command)
   {
      if (isSuspended.get())
      {
         throw new IllegalStateException("Executor service is suspended");
      }

      delegated.execute(command);
   }

   /**
    * {@inheritDoc}
    */
   public void shutdown()
   {
      delegated.shutdown();
   }

   /**
    * {@inheritDoc}
    */
   public List<Runnable> shutdownNow()
   {
      return delegated.shutdownNow();
   }

   /**
    * {@inheritDoc}
    */
   public boolean isShutdown()
   {
      return delegated.isShutdown();
   }

   /**
    * {@inheritDoc}
    */
   public boolean isTerminated()
   {
      return delegated.isTerminated();
   }

   /**
    * {@inheritDoc}
    */
   public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException
   {
      return delegated.awaitTermination(timeout, unit);
   }

   /**
    * {@inheritDoc}
    */
   public <T> Future<T> submit(Callable<T> task)
   {
      return delegated.submit(task);
   }

   /**
    * {@inheritDoc}
    */
   public <T> Future<T> submit(Runnable task, T result)
   {
      return delegated.submit(task, result);
   }

   /**
    * {@inheritDoc}
    */
   public Future<?> submit(Runnable task)
   {
      return delegated.submit(task);
   }

   /**
    * {@inheritDoc}
    */
   public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException
   {
      return delegated.invokeAll(tasks);
   }

   /**
    * {@inheritDoc}
    */
   public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
      throws InterruptedException
   {
      return delegated.invokeAll(tasks, timeout, unit);
   }

   /**
    * {@inheritDoc}
    */
   public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException
   {
      return delegated.invokeAny(tasks);
   }

   /**
    * {@inheritDoc}
    */
   public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
      throws InterruptedException, ExecutionException, TimeoutException
   {
      return delegated.invokeAny(tasks, timeout, unit);
   }

   /**
    * {@inheritDoc}
    */
   public void suspend() throws SuspendException
   {
      isSuspended.set(true);
      awaitTasksTermination();
   }

   /**
    * {@inheritDoc}
    */
   public void resume() throws ResumeException
   {
      isSuspended.set(false);
   }

   /**
    * {@inheritDoc}
    */
   public boolean isSuspended()
   {
      return isSuspended.get();
   }

   /**
    * {@inheritDoc}
    */
   public int getPriority()
   {
      throw new UnsupportedOperationException("Method is not supported");
   }

   /**
    * Awaits until all tasks will be done.
    */
   protected void awaitTasksTermination()
   {
      delegated.shutdown();
      try
      {
         delegated.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
      }
      catch (InterruptedException e)
      {
         LOG.warn("Termination has been interrupted");
      }
   }
}
