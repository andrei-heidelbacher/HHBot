package hhbot.fetcher

import akka.actor._
import akka.actor.SupervisorStrategy._
import akka.pattern.{ask, pipe}
import akka.util.Timeout

import dispatch.Http

import java.net.URL

import scala.concurrent.duration._

import hhbot.frontier._

/**
 * @author andrei
 */
class FetcherManager(http: Http) extends Actor {
  import context._
  import FetcherManager._
  import Fetcher._

  override val supervisorStrategy = OneForOneStrategy() {
    case _ => Restart
  }

  createFetchers(64)
  system.scheduler.schedule(1.seconds, 1.seconds) {
    notifyFetchers()
  }

  private def createFetchers(fetcherCount: Int): Unit = {
    require(fetcherCount >= 0)
    if (fetcherCount > 0) {
      watch(actorOf(Fetcher.props(http), "Fetcher-" + fetcherCount))
      createFetchers(fetcherCount - 1)
    }
  }

  private def notifyFetchers(): Unit = {
    children.foreach(fetcher => fetcher ! RequestsAvailable)
  }

  def receive: Receive = {
    case DemandRequest => parent.forward(DemandRequest)
    case result: FetchResult => parent ! result
  }
}

object FetcherManager {
  case object RequestsAvailable

  def props(http: Http): Props = Props(new FetcherManager(http))
}