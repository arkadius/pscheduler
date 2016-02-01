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

import java.time.Duration

import scala.concurrent.ExecutionContext
import scala.language.postfixOps

class PSchedulerBuilder[+EC, +P, +CS, +CI, +C](private[pscheduler] val definedExecutionContext: Option[ExecutionContext],
                                               private[pscheduler] val definedPersistence: Option[TasksPersistence],
                                               private[pscheduler] val definedCheckScheduler: Option[InMemoryScheduler],
                                               private[pscheduler] val definedCheckInterval: Option[Duration],
                                               private[pscheduler] val definedConfiguration: Option[Seq[TaskConfiguration]]) {

  def withExecutionContext(executionContext: ExecutionContext): PSchedulerBuilder[Defined, P, CS, CI, C] =
    new PSchedulerBuilder(Some(executionContext), definedPersistence, definedCheckScheduler, definedCheckInterval, definedConfiguration)

  def withPersistence(persistence: TasksPersistence): PSchedulerBuilder[EC, Defined, CS, CI, C] =
    new PSchedulerBuilder(definedExecutionContext, Some(persistence), definedCheckScheduler, definedCheckInterval, definedConfiguration)

  def withCheckScheduler(checkScheduler: InMemoryScheduler): PSchedulerBuilder[EC, P, Defined, CI, C] =
    new PSchedulerBuilder(definedExecutionContext, definedPersistence, Some(checkScheduler), definedCheckInterval, definedConfiguration)

  def withCheckInterval(checkInterval: Duration): PSchedulerBuilder[EC, P, CS, Defined, C] =
    new PSchedulerBuilder(definedExecutionContext, definedPersistence, definedCheckScheduler, Some(checkInterval), definedConfiguration)

  def withTasks(configuration: TaskConfiguration*): PSchedulerBuilder[EC, P, CS, CI, Defined] =
    new PSchedulerBuilder(definedExecutionContext, definedPersistence, definedCheckScheduler, definedCheckInterval, Some(configuration))
  
}

trait Defined

object PSchedulerBuilder extends PSchedulerBuilder(None, None, None, None, None) {

  implicit class FullyConfigured(builder: PSchedulerBuilder[Defined, Defined, Defined, Defined, Defined]) {
    def build: PScheduler = new PScheduler(
      persistence = builder.definedPersistence.get,
      checkScheduler = builder.definedCheckScheduler.get,
      checkInterval = builder.definedCheckInterval.get,
      configuration = builder.definedConfiguration.get
    )(builder.definedExecutionContext.get)
  }
}