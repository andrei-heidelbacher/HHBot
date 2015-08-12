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

import akka.actor._

import java.net.URI

abstract class Requester extends Actor {
  import context._
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