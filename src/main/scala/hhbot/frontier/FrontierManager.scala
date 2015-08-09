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

package hhbot.frontier

import akka.actor._
import akka.actor.SupervisorStrategy._
import akka.pattern.{ask, AskTimeoutException, pipe}
import akka.util.Timeout

import java.net.URI

import scala.collection.immutable.Queue
import scala.concurrent.duration._
import scala.concurrent.Future
import scala.util.Try

import hhbot.crawler.Configuration

object FrontierManager {
  def props(configuration: Configuration, resolverProps: Props): Props =
    Props(new FrontierManager(configuration, resolverProps))
}

class FrontierManager private (
    configuration: Configuration,
    resolverProps: Props) extends Actor {
  import context._
  import HostManager._

  override val supervisorStrategy = OneForOneStrategy() {
    case _ => Stop
  }

  private var hostToManager = Map.empty[String, ActorRef]
  private var managerToHost = Map.empty[ActorRef, String]
  private var hostCoefficient = Map.empty[String, Int]
  private var hostQueue = Queue.empty[String]
  private var history = Set.empty[URI]

  private def addToHistory(uri: URI): Unit = {
    history += uri
    if (history.size > configuration.maximumHistorySize)
      history = history.filter(link => hostCoefficient.contains(link.host))
    if (history.size > configuration.maximumHistorySize / 2)
      history = Set.empty[URI]
  }

  private def addHost(host: String): Unit = {
    if (!hostToManager.contains(host)) {
      val props = HostManager.props(host, configuration, resolverProps)
      val manager = actorOf(props, new URI(host).getAuthority)
      hostToManager += host -> manager
      managerToHost += manager -> host
      hostCoefficient += host -> configuration.hostBatchSize
      hostQueue = hostQueue.enqueue(host)
      watch(manager)
    }
  }

  private def removeHost(manager: ActorRef): Unit = {
    for (host <- managerToHost.get(manager)) {
      hostToManager -= host
      managerToHost -= manager
      hostCoefficient -= host
    }
  }

  private def pullHost(): Option[String] = {
    if (hostQueue.isEmpty) None
    else {
      val (host, remainingHosts) = hostQueue.dequeue
      if (hostCoefficient.contains(host)) {
        val coefficient = hostCoefficient(host)
        if (coefficient > 1) hostCoefficient += host -> (coefficient - 1)
        else hostQueue = remainingHosts.enqueue(host)
        Some(host)
      } else {
        hostQueue = remainingHosts
        pullHost()
      }
    }
  }

  def receive: Receive = {
    case Pull =>
      implicit val timeout = Timeout(configuration.maximumCrawlDelayInMs.millis)
      val result = pullHost() match {
        case Some(host) => hostToManager(host) ? Pull
        case None => Future.failed(new AskTimeoutException("Empty host queue!"))
      }
      result.pipeTo(sender())(self)
    case Push(uri) =>
      for (host <- Try(uri.host) if !history.contains(uri)) {
        addHost(host)
        addToHistory(uri)
        hostToManager(host).forward(Push(uri))
      }
    case Terminated(manager) => removeHost(manager)
  }
}