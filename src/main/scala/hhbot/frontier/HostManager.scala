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

import robots.protocol.exclusion.robotstxt.RuleSet

import java.net.URI
import java.util.Calendar

import scala.collection.immutable.Queue
import scala.concurrent.duration._

import hhbot.crawler.Configuration

object HostManager {
  sealed trait Message
  case class Push(uri: URI) extends Message
  case object Pull extends Message
  case class PullResult(uri: Option[URI]) extends Message

  implicit class URIHost(uri: URI) {
    def host: String = uri.getScheme + "://" + uri.getAuthority
  }

  def props(
      host: String,
      configuration: Configuration,
      resolverProps: Props): Props =
    Props(new HostManager(host, configuration, resolverProps))
}

class HostManager private (
    host: String,
    configuration: Configuration,
    resolverProps: Props) extends Actor {
  import context._
  import HostManager._
  import HostResolver._

  setReceiveTimeout(15.seconds)

  private val resolver = context.actorOf(resolverProps, "Resolver")
  private var robotstxt = RuleSet.empty
  private var lastRequest = 0.seconds

  resolver ! ResolveHost(host)

  private def getTimeNow = Calendar.getInstance().getTimeInMillis.millis

  def receive: Receive = emptyHost

  private def emptyHost: Receive = {
    case PushRobotstxt(robots) =>
      robotstxt = robots.getRules(configuration.agentName)
    case Push(uri) if uri.host == host =>
      if (robotstxt.isAllowed(uri.getPath))
        become(nonEmptyHost(Queue(uri)))
    case Pull => sender() ! PullResult(None)
    case ReceiveTimeout => self ! PoisonPill
  }

  private def nonEmptyHost(URIs: Queue[URI]): Receive = {
    case PushRobotstxt(robots) =>
      robotstxt = robots.getRules(configuration.agentName)
    case Push(uri) if uri.host == host =>
      if (robotstxt.isAllowed(uri.getPath))
        become(nonEmptyHost(URIs.enqueue(uri)))
    case Pull =>
      val (uri, remainingURIs) = URIs.dequeue
      val delay = robotstxt.delayInMs.millis - (getTimeNow - lastRequest)
        .max(configuration.minimumCrawlDelayInMs.millis)
      system.scheduler.scheduleOnce(delay, sender(), PullResult(Some(uri)))
      lastRequest = getTimeNow + delay
      if (remainingURIs.isEmpty) become(emptyHost)
      else become(nonEmptyHost(remainingURIs))
  }
}