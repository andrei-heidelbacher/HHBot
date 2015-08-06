package hhbot.frontier

import akka.actor._

import robots.protocol.exclusion.robotstxt.{Robotstxt, RuleSet}
import robots.protocol.inclusion.Sitemap

import java.net.{URI, URL}
import java.util.Calendar

import scala.collection.immutable.Queue
import scala.concurrent.duration._
import scala.util.Try

import hhbot.crawler.Configuration
import hhbot.fetcher._

/**
 * @author Andrei Heidelbacher
 */
class HostManager(host: URI, configuration: Configuration, resolverProps: Props)
  extends Actor {
  import context._
  import HostManager._
  import Fetcher._

  setReceiveTimeout(15.seconds)

  private val resolver = context.actorOf(resolverProps, "resolver")
  private var robotstxt = RuleSet.empty
  private var lastRequest = 0.seconds

  resolver ! FetchRequest(new URL(host.toURL, "/robots.txt").toURI)

  private def getTimeNow: FiniteDuration =
    Calendar.getInstance().getTimeInMillis.milliseconds

  private def handleRobots(content: Array[Byte]): Unit = {
    val robots = Robotstxt(content)
    robotstxt = robots.getRules(configuration.agentName)
    robots.sitemaps.flatMap(link => Try(new URI(link)).toOption).foreach {
      resolver ! FetchRequest(_)
    }
  }

  private def handleSitemap(uri: URI, content: Array[Byte]): Unit = for {
    sitemap <- Try(Sitemap(uri.toURL, new String(content, "UTF-8")))
    link <- sitemap.links
    linkURI <- Try(link.toURI)
  } {
    if (sitemap.isSitemapIndex) resolver ! FetchRequest(linkURI)
    else self ! Push(linkURI)
  }

  private def resolve(result: FetchResult): Unit = for {
    content <- result.content
    uri = result.uri
  } {
    if (uri.getPath == "/robots.txt") handleRobots(content)
    else handleSitemap(uri, content)
  }

  def receive: Receive = emptyHost

  private def emptyHost: Receive = {
    case result: FetchResult => resolve(result)
    case Push(uri) =>
      if (robotstxt.isAllowed(uri.getPath))
        become(nonEmptyHost(Queue(uri)))
    case ReceiveTimeout => self ! PoisonPill
  }

  private def nonEmptyHost(uris: Queue[URI]): Receive = {
    case result: FetchResult => resolve(result)
    case Push(uri) =>
      if (robotstxt.isAllowed(uri.getPath))
        become(nonEmptyHost(uris.enqueue(uri)))
    case Pull =>
      val (uri, remainingURIs) = uris.dequeue
      val requester = sender()
      val delay = configuration.minimumCrawlDelayInMs.milliseconds
        .max(robotstxt.delayInMs.milliseconds - (getTimeNow - lastRequest))
      system.scheduler.scheduleOnce(delay) {
        lastRequest = getTimeNow
        requester ! PullResult(uri)
      }
      if (remainingURIs.isEmpty) become(emptyHost)
      else become(nonEmptyHost(remainingURIs))
  }
}

object HostManager {
  case class Push(uri: URI)
  case object Pull
  case class PullResult(uri: URI)

  def props(
      host: URI,
      configuration: Configuration,
      resolverProps: Props): Props =
    Props(new HostManager(host, configuration, resolverProps))
}