package hhbot.runner

import java.io.PrintWriter
import java.net.URI

import hhbot.crawler.Configuration

/**
 * @author Andrei Heidelbacher
 */
object DefaultRunner extends Runner {
  val writer = new PrintWriter("history.log")

  def configuration = Configuration(
    agentName = "HHBot",
    userAgentString = "HHBot",
    connectionTimeoutInMs = 2500,
    requestTimeoutInMs = 5000,
    followRedirects = true,
    maximumNumberOfRedirects = 3,
    filterURI = uri => {
      !uri.toString.endsWith(".xml") &&
        !uri.toString.endsWith(".jpg") &&
        !uri.toString.endsWith(".png") &&
        !uri.toString.endsWith(".mp3") &&
        !uri.toString.endsWith(".rss")
    },
    minimumCrawlDelayInMs = 250,
    maximumCrawlDelayInMs = 1000,
    maximumHistorySize = 1000000,
    hostBatchSize = 10,
    crawlDurationInMs = 60000L)

  def seedURIs = Seq(
    new URI("http://www.google.com"),
    new URI("http://www.wikipedia.org"),
    new URI("http://www.reddit.com"),
    new URI("http://www.gsp.ro"))

  def processResult(uri: URI, content: Array[Byte]) = {
    println("Retrieved " + uri.toString)
    writer.println(uri.toString)
    writer.flush()
  }

  def processFailure(uri: URI, error: Throwable) = {
    println("Failed " + uri.toString + " because " + error.getMessage)
  }
}