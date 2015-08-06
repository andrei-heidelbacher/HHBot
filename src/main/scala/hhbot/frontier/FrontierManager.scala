package hhbot.frontier

import akka.actor._
import akka.pattern.{ask, pipe}
import akka.util.Timeout

import dispatch.Http

import java.net.{MalformedURLException, URL}

import scala.collection.immutable.Queue
import scala.concurrent.duration._

import hhbot.fetcher.Fetcher

/**
 * @author andrei
 */
class FrontierManager(agentName: String, resolverProps: Props) extends Actor {
  import context._
  import HostManager._

  private var hostToManager = Map.empty[String, ActorRef]
  private var managerToHost = Map.empty[ActorRef, String]
  private var hostQueue = Queue.empty[String]

  private def addHost(url: URL): Unit = {
    try {
      val host = new URL(url.getProtocol + "://" + url.getHost)
      val props = HostManager.props(host, agentName, resolverProps)
      val manager = actorOf(props, url.getHost)
      watch(manager)
      hostToManager += url.getHost -> manager
      managerToHost += manager -> url.getHost
      hostQueue = hostQueue.enqueue(url.getHost)
    } catch {
      case e: MalformedURLException =>
      case e: InvalidActorNameException =>
    }
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
      val requester = sender()
      val host = pullHost()
      implicit val timeout = Timeout(15.seconds)
      (hostToManager(host) ? Pull).pipeTo(requester)(self)
    case Push(url) =>
      if (!hostToManager.contains(url.getHost))
        addHost(url)
      hostToManager.get(url.getHost).foreach(_.forward(Push(url)))
    case Terminated(manager) =>
      val host = managerToHost(manager)
      hostToManager -= host
      managerToHost -= manager
    case Status.Failure(t) =>
  }
}

object FrontierManager {
  def props(agentName: String, http: Http): Props =
    Props(new FrontierManager(agentName, Fetcher.props(http)))
}