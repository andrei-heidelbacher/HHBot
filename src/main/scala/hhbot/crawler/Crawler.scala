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

package hhbot.crawler

import akka.actor._
import akka.actor.SupervisorStrategy._
import akka.pattern.{ask, pipe}
import akka.util.Timeout

import dispatch.Http

import robots.protocol.exclusion.html._

import java.net.URI

import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

import hhbot.fetcher._
import hhbot.frontier._

object Crawler {
  case class CrawlRequest(uri: URI)

  def props(configuration: Configuration, requester: ActorRef): Props =
    Props(new Crawler(configuration, requester))
}

class Crawler private (
    configuration: Configuration,
    requester: ActorRef) extends Actor {
  import context._
  import Crawler._
  import Fetcher._
  import HostManager._
  import Requester._

  override val supervisorStrategy = OneForOneStrategy() {
    case _ => Restart
  }

  private val http = Http.configure { _
    .setUserAgent(configuration.userAgentString)
    .setConnectionTimeoutInMs(configuration.connectionTimeoutInMs)
    .setRequestTimeoutInMs(configuration.requestTimeoutInMs)
    .setFollowRedirects(configuration.followRedirects)
    .setMaximumNumberOfRedirects(configuration.maximumNumberOfRedirects)
  }
  private val fetcherProps = Fetcher.props(http)
  private val resolverProps = HostResolver.props(fetcherProps)
  private val fetcherManagerProps = FetcherManager.props(fetcherProps)
  private val frontierManagerProps =
    FrontierManager.props(configuration, resolverProps)
  private val manager = actorOf(fetcherManagerProps, "Fetcher-Manager")
  private val frontier = actorOf(frontierManagerProps, "URI-Frontier")

  watch(manager)
  watch(frontier)

  private def crawlURI(uri: URI): Unit = {
    if (configuration.filterURI(uri))
      frontier ! Push(uri)
  }

  def receive: Receive = {
    case CrawlRequest(uri) => frontier ! Push(uri)
    case DemandRequest =>
      val requester = sender()
      implicit val timeout = Timeout(configuration.maximumCrawlDelayInMs.millis)
      val request = (frontier ? Pull).mapTo[PullResult]
        .map { case PullResult(uri) => FetchRequest(uri) }
      request.pipeTo(requester)(self)
    case FetchResult(uri, result) =>
      result match {
        case Success(content) =>
          for (page <- Try(Page(uri.toURL, content))) {
            val tags = page.metaTags(configuration.agentName)
            if (tags.contains(All) || tags.contains(Index))
              requester ! Result(uri, content)
            if (tags.contains(All) || tags.contains(Follow))
              page.outlinks
                .flatMap(link => Try(link.toURI).toOption)
                .foreach(crawlURI)
          }
        case Failure(t) => requester ! Failed(uri, t)
      }
  }
}