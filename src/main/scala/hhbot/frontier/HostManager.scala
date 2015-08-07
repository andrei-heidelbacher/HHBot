package hhbot.frontier

import akka.actor._

import robots.protocol.exclusion.robotstxt.RuleSet

import java.net.URI
import java.util.Calendar

import scala.collection.immutable.Queue
import scala.concurrent.duration._

import hhbot.crawler.Configuration

/**
 * @author Andrei Heidelbacher
 */
object HostManager {
  case class Push(uri: URI)
  case object Pull
  case class PullResult(uri: URI)

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
      val requester = sender()
      val delay = robotstxt.delayInMs.millis - (getTimeNow - lastRequest)
        .max(configuration.minimumCrawlDelayInMs.millis)
      system.scheduler.scheduleOnce(delay) {
        lastRequest = getTimeNow
        requester ! PullResult(uri)
      }
      if (remainingURIs.isEmpty) become(emptyHost)
      else become(nonEmptyHost(remainingURIs))
  }
}