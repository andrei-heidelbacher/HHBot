package hhbot.crawler

import akka.actor._

import java.net.URL

/**
 * @author andrei
 */
abstract class Requester extends Actor {
  import context._
  import Requester._
  import Crawler._

  private val crawler = actorOf(Crawler.props(configuration, self), "Crawler")

  watch(crawler)
  seedURLs.foreach(url => crawler ! CrawlRequest(url))

  protected def configuration: Configuration

  protected def seedURLs: Seq[URL]

  protected def processResult(url: URL, content: Array[Byte]): Unit

  protected def processFailure(url: URL, error: Throwable): Unit

  def receive: Receive = {
    case Result(url, content) => processResult(url, content)
    case Failed(url, error) => processFailure(url, error)
    case Terminated(_) => self ! PoisonPill
  }
}

object Requester {
  case class Result(url: URL, content: Array[Byte])
  case class Failed(url: URL, error: Throwable)
}
