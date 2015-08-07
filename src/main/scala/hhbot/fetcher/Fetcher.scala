package hhbot.fetcher

import akka.actor._
import akka.pattern.pipe

import dispatch.{as, Http, url}

import java.net.URI

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

/**
 * @author Andrei Heidelbacher
 */
object Fetcher {
  case object DemandRequest
  case class FetchRequest(uri: URI)
  case class FetchResult(uri: URI, content: Try[Array[Byte]])

  def props(http: Http): Props = Props(new Fetcher(http))
}

class Fetcher private (http: Http) extends Actor {
  import context._
  import Fetcher._
  import FetcherManager._

  def receive: Receive = {
    case RequestsAvailable => sender() ! DemandRequest
    case FetchRequest(uri) =>
      val query = Future(url(uri.toString))
      val result = query.flatMap(q => http(q OK as.Bytes))
        .map(content => Success(content))
        .recover({ case e: Throwable => Failure(e) })
        .map(content => FetchResult(uri, content))
      result.pipeTo(sender())(self)
      parent ! DemandRequest
  }
}