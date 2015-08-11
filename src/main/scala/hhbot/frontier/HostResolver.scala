/*
 * Copyright 2015 Andrei Heidelbacher <andrei.heidelbacher@gmail.com>
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

package hhbot.frontier

import akka.actor._
import akka.actor.SupervisorStrategy._

import robots.protocol.exclusion.robotstxt.Robotstxt
import robots.protocol.inclusion.Sitemap

import java.net.{URI, URL}

import scala.concurrent.duration._
import scala.util.Try

import hhbot.fetcher.Fetcher

object HostResolver {
  case class ResolveHost(host: String)
  case class PushRobotstxt(robots: Robotstxt)

  def props(fetcherProps: Props): Props = Props(new HostResolver(fetcherProps))
}

class HostResolver private (fetcherProps: Props) extends Actor {
  import context._
  import HostResolver._
  import Fetcher._
  import HostManager._

  setReceiveTimeout(10.seconds)

  override val supervisorStrategy = OneForOneStrategy() {
    case _ => Restart
  }

  private val fetcher = actorOf(fetcherProps, "Robots-Fetcher")

  private def resolveRobots(manager: ActorRef, content: Array[Byte]): Unit = {
    val robots = Robotstxt(content)
    import java.io.PrintWriter
    val writer = new PrintWriter("hhbot/logs/robotstxt/" + manager.path.name)
    writer.write(new String(content, "UTF-8"))
    writer.close()
    manager ! PushRobotstxt(robots)
    robots.sitemaps
      .flatMap(link => Try(new URI(link)).toOption)
      .foreach(uri => fetcher ! FetchRequest(uri))
  }

  private def resolveSitemap(
      manager: ActorRef,
      uri: URI,
      content: Array[Byte]): Unit = for {
    sitemap <- Try(Sitemap(uri.toURL, new String(content, "UTF-8")))
    link <- sitemap.links
    linkURI <- Try(link.toURI)
  } {
    if (sitemap.isSitemapIndex) fetcher ! FetchRequest(linkURI)
    else manager ! Push(linkURI)
  }

  def receive: Receive = idle

  private def idle: Receive = {
    case ResolveHost(host) =>
      for (link <- Try(new URL(new URL(host), "/robots.txt").toURI)) {
        fetcher ! FetchRequest(link)
        context.become(resolving(sender(), host))
      }
    case ReceiveTimeout => self ! PoisonPill
  }

  private def resolving(manager: ActorRef, host: String): Receive = {
    case FetchResult(uri, result) =>
      for (content <- result if uri.host == host) {
        if (uri.getPath == "/robots.txt") resolveRobots(manager, content)
        else resolveSitemap(manager, uri, content)
      }
    case ReceiveTimeout => context.become(idle)
  }
}