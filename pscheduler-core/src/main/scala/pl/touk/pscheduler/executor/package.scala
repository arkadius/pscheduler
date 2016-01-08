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

import java.util.concurrent.{Executors, ScheduledExecutorService}

package object executor {
  implicit class ExecutorServiceSchedulerDefiner[P, CS, CI, C](builder: PSchedulerBuilder[P, CS, CI, C]) {
    def withJavaScheduler(executor: ScheduledExecutorService) =
      builder.withCheckScheduler(new ExecutorServiceScheduler(executor))

    def withJavaScheduler() = {
      val executor = Executors.newSingleThreadScheduledExecutor(Executors.defaultThreadFactory())
      builder.withCheckScheduler(new ExecutorServiceScheduler(executor))
    }
  }
}