package hhbot.fetcher

import akka.actor._
import akka.pattern.pipe

import dispatch.{as, Http}
import dispatch.Defaults.executor

import java.net.URL

import scala.util.{Failure, Success, Try}

/**
 * @author andrei
 */
class Fetcher(http: Http) extends Actor {
  import Fetcher._
  import FetcherManager._

  def receive: Receive = {
    case RequestsAvailable => sender() ! DemandRequest
    case FetchRequest(url) =>
      val query = dispatch.url(url.toString)
      val result = http(query OK as.Bytes)
        .map(content => Success(content))
        .recover({ case e: Throwable => Failure(e) })
        .map(content => FetchResult(url, content))
      pipe(result).to(sender())
      context.parent ! DemandRequest
    case Status.Failure(t) =>
  }
}

object Fetcher {
  case object DemandRequest
  case class FetchRequest(url: URL)
  case class FetchResult(url: URL, content: Try[Array[Byte]])

  def props(http: Http): Props = Props(new Fetcher(http))
}