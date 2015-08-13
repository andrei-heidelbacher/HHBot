/*
 * Copyright 2015 Andrei Heidelbacher <andrei.heidelbacher@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

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
    maximumCrawlDelayInMs: Int,
    maximumHistorySize: Int,
    hostBatchSize: Int,
    crawlDurationInMs: Long,
    fetcherCount: Int) {
  require(agentName.nonEmpty)
  require(userAgentString.startsWith(agentName))
  require(connectionTimeoutInMs > 0)
  require(requestTimeoutInMs > 0)
  require(followRedirects == (maximumNumberOfRedirects > 0))
  require(maximumNumberOfRedirects >= 0)
  require(minimumCrawlDelayInMs > 100)
  require(maximumCrawlDelayInMs > minimumCrawlDelayInMs)
  require(maximumHistorySize > 0)
  require(hostBatchSize > 0)
  require(crawlDurationInMs > 0L)
  require(fetcherCount > 0)
}
