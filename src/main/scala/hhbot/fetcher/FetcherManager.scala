/*
 * Copyright 2015 Andrei Heidelbacher <andrei.heidelbacher@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package hhbot.fetcher

import akka.actor._
import akka.actor.SupervisorStrategy._

import scala.concurrent.duration._

object FetcherManager {
  sealed trait Message
  case object RequestsAvailable extends Message

  def props(fetcherProps: Props, fetcherCount: Int): Props =
    Props(new FetcherManager(fetcherProps, fetcherCount))
}

class FetcherManager private (fetcherProps: Props, fetcherCount: Int)
  extends Actor {
  import context._
  import FetcherManager._
  import Fetcher._

  override val supervisorStrategy = OneForOneStrategy() {
    case _ => Restart
  }

  createFetchers(fetcherCount)
  system.scheduler.schedule(1.seconds, 3.seconds) {
    notifyFetchers()
  }

  private def createFetchers(count: Int): Unit = {
    require(count >= 0)
    if (count > 0) {
      watch(actorOf(fetcherProps, "Fetcher-" + count))
      createFetchers(count - 1)
    }
  }

  private def notifyFetchers(): Unit = {
    children.foreach(fetcher => fetcher ! RequestsAvailable)
  }

  def receive: Receive = {
    case DemandRequest => parent.forward(DemandRequest)
    case result: FetchResult => parent ! result
  }
}