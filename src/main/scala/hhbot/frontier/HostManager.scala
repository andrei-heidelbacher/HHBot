package hhbot.frontier

import akka.actor._

import robots.protocol.exclusion.robotstxt.{Robotstxt, RuleSet}
import robots.protocol.inclusion.Sitemap

import java.net.URL
import java.util.Calendar

import scala.collection.immutable.Queue
import scala.concurrent.duration._
import scala.util.Try

import hhbot.fetcher._

/**
 * @author andrei
 */
class HostManager(host: URL, agentName: String, resolverProps: Props)
  extends Actor {
  import context._
  import HostManager._
  import Fetcher._

  setReceiveTimeout(30.seconds)

  private val resolver = context.actorOf(resolverProps, "resolver")
  private var robotstxt = RuleSet.empty
  private var lastRequest = 0.seconds

  resolver ! FetchRequest(new URL(host, "/robots.txt"))

  private def getTimeNow: FiniteDuration =
    Calendar.getInstance().getTimeInMillis.milliseconds

  private def handleRobots(content: Array[Byte]): Unit = {
    val robots = Robotstxt(content)
    robotstxt = robots.getRules(agentName)
    robots.sitemaps.flatMap(link => Try(new URL(link)).toOption).foreach {
      resolver ! FetchRequest(_)
    }
  }

  private def handleSitemap(url: URL, content: Array[Byte]): Unit = {
    val sitemap = Sitemap(url, new String(content, "UTF-8"))
    sitemap.links.foreach { link =>
      if (sitemap.isSitemapIndex)
        resolver ! FetchRequest(link)
      else
        self ! Push(link)
    }
  }

  private def resolve(result: FetchResult): Unit = {
    for (content <- result.content) {
      if (result.url.getPath == "/robots.txt")
        handleRobots(content)
      else
        handleSitemap(result.url, content)
    }
  }

  def receive: Receive = emptyHost

  def emptyHost: Receive = {
    case result: FetchResult => resolve(result)
    case Push(url) =>
      if (robotstxt.isAllowed(url.getPath))
        become(nonEmptyHost(Queue(url)))
    case ReceiveTimeout => self ! PoisonPill
  }

  def nonEmptyHost(urls: Queue[URL]): Receive = {
    case result: FetchResult => resolve(result)
    case Push(url) =>
      if (robotstxt.isAllowed(url.getPath))
        become(nonEmptyHost(urls.enqueue(url)))
    case Pull =>
      val (url, remainingURLs) = urls.dequeue
      val requester = sender()
      val delay = robotstxt.delayInMs.milliseconds - (getTimeNow - lastRequest)
      //system.scheduler.scheduleOnce(delay) {
        lastRequest = getTimeNow
        requester ! PullResult(url)
      //}
      if (remainingURLs.isEmpty)
        become(emptyHost)
      else
        become(nonEmptyHost(remainingURLs))
  }
}

object HostManager {
  case class Push(url: URL)
  case object Pull
  case class PullResult(url: URL)

  def props(host: URL, agentName: String, resolverProps: Props): Props =
    Props(new HostManager(host, agentName, resolverProps))
}