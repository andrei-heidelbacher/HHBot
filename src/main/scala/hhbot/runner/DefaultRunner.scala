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

package hhbot.runner

import java.io.PrintWriter
import java.net.URI

import hhbot.crawler.Configuration

object DefaultRunner extends Runner {
  val writer = new PrintWriter("hhbot/logs/history.log")

  def configuration = Configuration(
    agentName = "HHBot",
    userAgentString = "HHBot",
    connectionTimeoutInMs = 2500,
    requestTimeoutInMs = 15000,
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