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

package hhbot.fetcher

import akka.actor._
import akka.pattern.pipe

import dispatch.{as, Http, url}

import java.net.URI

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

object Fetcher {
  sealed trait Message
  case object DemandRequest extends Message
  case class FetchRequest(uri: URI) extends Message
  case class FetchResult(uri: URI, content: Try[Array[Byte]]) extends Message

  def props(http: Http): Props = Props(new Fetcher(http))
}

class Fetcher private (http: Http) extends Actor {
  import context._
  import Fetcher._
  import FetcherManager._

  def receive: Receive = {
    case RequestsAvailable => sender() ! DemandRequest
    case FetchRequest(uri) =>
      val query = Future(url(uri.toString))
      val result = query.flatMap(q => http(q OK as.Bytes))
        .map(content => Success(content))
        .recover({ case e: Throwable => Failure(e) })
        .map(content => FetchResult(uri, content))
      result.pipeTo(sender())(self)
      parent ! DemandRequest
    case Status.Failure(t) => parent ! DemandRequest
  }
}