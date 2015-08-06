package hhbot.crawler

import java.net.URL

/**
 * @author andrei
 */
case class Configuration(
    agentName: String,
    userAgentString: String,
    connectionTimeoutInMs : Int,
    requestTimeoutInMs: Int,
    followRedirects: Boolean,
    maximumNumberOfRedirects: Int,
    filterURL: URL => Boolean) {
  require(agentName.nonEmpty)
  require(userAgentString.startsWith(agentName))
  require(connectionTimeoutInMs > 0)
  require(requestTimeoutInMs > 0)
  require(followRedirects == (maximumNumberOfRedirects > 0))
  require(maximumNumberOfRedirects >= 0)
}
