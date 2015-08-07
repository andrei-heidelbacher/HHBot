package hhbot.frontier

import akka.actor._
import akka.actor.SupervisorStrategy._

import robots.protocol.exclusion.robotstxt.Robotstxt
import robots.protocol.inclusion.Sitemap

import java.net.{URI, URL}

import scala.concurrent.duration._
import scala.util.Try

import hhbot.fetcher.Fetcher

/**
 * @author Andrei Heidelbacher
 */
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
    /*import java.io.PrintWriter
    val writer = new PrintWriter(manager.path.name)
    writer.write(new String(content, "UTF-8"))
    writer.close()*/
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