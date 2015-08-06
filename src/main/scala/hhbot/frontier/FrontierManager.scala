package hhbot.frontier

import akka.actor._
import akka.actor.SupervisorStrategy._
import akka.pattern.{ask, pipe}
import akka.util.Timeout

import dispatch.Http

import java.net.URI

import scala.collection.immutable.Queue
import scala.concurrent.duration._
import scala.util.Try

import hhbot.crawler.Configuration
import hhbot.fetcher.Fetcher

/**
 * @author Andrei Heidelbacher
 */
class FrontierManager(configuration: Configuration, resolverProps: Props)
  extends Actor {
  import context._
  import HostManager._

  override val supervisorStrategy = OneForOneStrategy() {
    case _ => Stop
  }

  private var hostToManager = Map.empty[String, ActorRef]
  private var managerToHost = Map.empty[ActorRef, String]
  private var hostQueue = Queue.empty[String]
  private var history = Set.empty[URI]

  private def addHost(uri: URI): Unit = for {
    hostURI <- Try(new URI(uri.getScheme + "://" + uri.getAuthority))
    host <- Option(uri.getAuthority)
  } {
    val props =
      HostManager.props(hostURI, configuration, resolverProps)
    val manager = actorOf(props, host)
    watch(manager)
    hostToManager += host -> manager
    managerToHost += manager -> host
    hostQueue = hostQueue.enqueue(host)
  }

  private def pullHost(): String = {
    val (host, remainingHosts) = hostQueue.dequeue
    hostQueue = remainingHosts
    if (hostToManager.contains(host)) {
      hostQueue = hostQueue.enqueue(host)
      host
    } else {
      pullHost()
    }
  }

  def receive: Receive = {
    case Pull =>
      implicit val timeout =
        Timeout(configuration.maximumCrawlDelayInMs.milliseconds)
      (hostToManager(pullHost()) ? Pull).pipeTo(sender())(self)
    case Push(uri) =>
      if (!history.contains(uri)) {
        if (!hostToManager.contains(uri.getAuthority))
          addHost(uri)
        hostToManager.get(uri.getAuthority).foreach(_.forward(Push(uri)))
        history += uri
      }
    case Terminated(manager) =>
      val host = managerToHost(manager)
      hostToManager -= host
      managerToHost -= manager
  }
}

object FrontierManager {
  def props(configuration: Configuration, http: Http): Props =
    Props(new FrontierManager(configuration, Fetcher.props(http)))
}