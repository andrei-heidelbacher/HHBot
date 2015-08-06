package hhbot.runner

import akka.actor._

import java.net.URL

import hhbot.crawler._

/**
 * @author andrei
 */
object Runner {
  def main (args: Array[String]): Unit = {
    val system = ActorSystem("HHBot")
    val requester = system.actorOf(Props(new Requester {
      def configuration = Configuration(
          agentName = "HHBot",
          userAgentString = "HHBot",
          connectionTimeoutInMs = 5000,
          requestTimeoutInMs = 15000,
          followRedirects = true,
          maximumNumberOfRedirects = 3,
          filterURL = url => {
            !url.toString.endsWith(".xml") ||
              !url.toString.endsWith(".jpg") ||
              !url.toString.endsWith(".png") ||
              !url.toString.endsWith(".mp3") ||
              !url.toString.endsWith(".rss")
          })

      def seedURLs = Seq(
          new URL("http://www.google.com"),
          new URL("http://www.wikipedia.org"),
          new URL("http://www.reddit.com"),
          new URL("http://www.gsp.ro"))

      def processResult(url: URL, content: Array[Byte]) = {
        println("Retrieved " + url.toString)
      }

      def processFailure(url: URL, error: Throwable) = {
        println("Failed " + url.toString + " because " + error.getMessage)
      }
    }), "Requester")
  }
}
