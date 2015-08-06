package hhbot.crawler

import java.net.URI

/**
 * @author Andrei Heidelbacher
 */
case class Configuration(
    agentName: String,
    userAgentString: String,
    connectionTimeoutInMs: Int,
    requestTimeoutInMs: Int,
    followRedirects: Boolean,
    maximumNumberOfRedirects: Int,
    filterURI: URI => Boolean,
    minimumCrawlDelayInMs: Int,
    maximumCrawlDelayInMs: Int) {
  require(agentName.nonEmpty)
  require(userAgentString.startsWith(agentName))
  require(connectionTimeoutInMs > 0)
  require(requestTimeoutInMs > 0)
  require(followRedirects == (maximumNumberOfRedirects > 0))
  require(maximumNumberOfRedirects >= 0)
  require(minimumCrawlDelayInMs > 100)
  require(maximumCrawlDelayInMs > minimumCrawlDelayInMs)
}
