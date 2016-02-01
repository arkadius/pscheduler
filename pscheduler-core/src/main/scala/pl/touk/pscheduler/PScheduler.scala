/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package pl.touk.pscheduler

import java.time._

import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class PScheduler(persistence: TasksPersistence,
                 checkScheduler: InMemoryScheduler,
                 checkInterval: Duration,
                 configuration: Seq[TaskConfiguration]) {

  protected def now: Instant = Instant.now()

  protected def zone: ZoneId = ZoneId.systemDefault()

  private val logger = LoggerFactory.getLogger(getClass)

  private var ran: Boolean = false
  private var scheduledCheck: Option[Cancellable] = None
  @volatile var currentTaskRunFuture: Future[Unit] = Future.successful(Unit)

  def start()(implicit ec: ExecutionContext): Unit = {
    synchronized {
      if (!ran) {
        ran = true
        runScheduledTasks()
      }
    }
  }

  private def runScheduledTasks()(implicit ec: ExecutionContext): Future[Unit] = {
    runScheduledTasks(now)
  }

  private def runScheduledTasks(now: Instant)
                               (implicit ec: ExecutionContext): Future[Unit] = {
    val tasksRunF = for {
      saved <- persistence.savedTasks
      _ = {
        logger.debug(saved.mkString("Fetched tasks:\n", "\n", "\nChecking if some of them should be run"))
      }
      savedByName = saved.map(task => task.name -> task).toMap
      shouldRun = shouldRunNow(savedByName, now) _
      tasksToRun = configuration.filter(shouldRun)
      tasksResult <- Future.sequence(tasksToRun.map { task =>
        runTaskThanUpdateLastRun(now, task)
      })
    } yield ()
    currentTaskRunFuture = tasksRunF
    tasksRunF.onComplete { _ =>
      synchronized {
        if (ran) {
          scheduledCheck = Some(checkScheduler.schedule(runScheduledTasks(), checkInterval))
        }
      }
    }
    tasksRunF
  }

  private def shouldRunNow(savedByName: Map[String, Task], now: Instant)
                          (config: TaskConfiguration): Boolean = {
    val optionalLastRun = savedByName.get(config.taskName).map(_.lastRun)
    config.schedule.shouldRun(
      optionalLastRun.map(LocalDateTime.ofInstant(_, zone)),
      LocalDateTime.ofInstant(now, zone)
    )
  }

  private def runTaskThanUpdateLastRun(now: Instant, task: TaskConfiguration)
                                      (implicit ec: ExecutionContext): Future[Unit] = {
    logger.debug(s"Running task: ${task.taskName}")
    val taskRunF = task.run()
    taskRunF.onFailure {
      case NonFatal(ex) => logger.error("Error while running task", ex)
    }
    for {
      _ <- taskRunF
      _ <- {
        val saveF = persistence.save(Task(task.taskName, now))
        saveF.onFailure {
          case NonFatal(ex) => logger.error("Error while saving task", ex)
        }
        saveF
      }
    } yield ()
  }

  def stop(): Future[Unit] = {
    synchronized {
      scheduledCheck.foreach(_.cancel())
      ran = false
    }
    currentTaskRunFuture
  }
}

object PScheduler {
  import executor._
  def builder = PSchedulerBuilder.withJavaScheduler().withCheckInterval(Duration.ofMinutes(5))
}

case class TaskConfiguration(taskName: String, schedule: TaskSchedule, run: () => Future[Unit])