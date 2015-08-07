package hhbot.runner

import akka.actor._

import java.net.URI

import scala.concurrent.duration._

import hhbot.crawler._

/**
 * @author Andrei Heidelbacher
 */
abstract class Runner {
  def configuration: Configuration

  def seedURIs: Seq[URI]

  def processResult(uri: URI, content: Array[Byte]): Unit

  def processFailure(uri: URI, error: Throwable): Unit

  final def main(args: Array[String]): Unit = {
    val system = ActorSystem(configuration.agentName)
    val requester = system.actorOf(Props(new Requester {
      def configuration = this.configuration
      def seedURIs = this.seedURIs
      def processResult(uri: URI, content: Array[Byte]) =
        this.processResult(uri, content)
      def processFailure(uri: URI, error: Throwable) =
        this.processFailure(uri, error)
    }), "Requester")
    import system.dispatcher
    system.scheduler.scheduleOnce(configuration.crawlDurationInMs.millis) {
      requester ! PoisonPill
      system.shutdown()
    }
  }
}
