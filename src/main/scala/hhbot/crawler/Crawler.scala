package hhbot.crawler

import akka.actor._
import akka.actor.SupervisorStrategy._
import akka.pattern.{ask, pipe}
import akka.util.Timeout

import dispatch.Http

import robots.protocol.exclusion.html._

import java.net.URL

import scala.concurrent.duration._
import scala.util.{Failure, Success}

import hhbot.fetcher._
import hhbot.frontier._

/**
 * @author andrei
 */
class Crawler(configuration: Configuration, requester: ActorRef) extends Actor {
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
  private val manager = actorOf(
      FetcherManager.props(http),
      "Fetcher-Manager")
  private val frontier = actorOf(
      FrontierManager.props(configuration.agentName, http),
      "URL-Frontier")

  watch(manager)
  watch(frontier)

  private def crawlURL(url: URL): Unit = {
    if (configuration.filterURL(url))
      frontier ! Push(url)
  }

  def receive: Receive = {
    case CrawlRequest(url) => frontier ! Push(url)
    case DemandRequest =>
      val requester = sender()
      implicit val timeout = Timeout(15.seconds)
      val request = (frontier ? Pull).mapTo[PullResult]
        .map { case PullResult(url) => FetchRequest(url) }
      request.pipeTo(requester)(self)
    case FetchResult(url, result) =>
      result match {
        case Success(content) =>
          val page = Page(url, content)
          val tags = page.metaTags(configuration.agentName)
          if (tags.contains(All) || tags.contains(Index))
            requester ! Result(url, content)
          if (tags.contains(All) || tags.contains(Follow))
            page.outlinks.foreach(crawlURL)
        case Failure(t) => requester ! Failed(url, t)
      }
    case Status.Failure(t) =>
  }
}

object Crawler {
  case class CrawlRequest(url: URL)

  def props(configuration: Configuration, requester: ActorRef): Props =
    Props(new Crawler(configuration, requester))
}