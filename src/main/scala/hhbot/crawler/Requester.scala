package hhbot.crawler

import akka.actor._

import java.net.URI

/**
 * @author Andrei Heidelbacher
 */
abstract class Requester extends Actor {
  import context._
  import Requester._
  import Crawler._

  private val crawler = actorOf(Crawler.props(configuration, self), "Crawler")

  watch(crawler)
  seedURIs.foreach(uri => crawler ! CrawlRequest(uri))

  protected def configuration: Configuration

  protected def seedURIs: Seq[URI]

  protected def processResult(uri: URI, content: Array[Byte]): Unit

  protected def processFailure(uri: URI, error: Throwable): Unit

  def receive: Receive = {
    case Result(uri, content) => processResult(uri, content)
    case Failed(uri, error) => processFailure(uri, error)
    case Terminated(_) => self ! PoisonPill
  }
}

object Requester {
  case class Result(uri: URI, content: Array[Byte])
  case class Failed(uri: URI, error: Throwable)
}
