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

import akka.actor._

import com.typesafe.config.ConfigFactory

import java.io.File
import java.net.URI

import scala.concurrent.duration._

import hhbot.crawler._

abstract class Runner {
  def configuration: Configuration

  def seedURIs: Seq[URI]

  def processResult(uri: URI, content: Array[Byte]): Unit

  def processFailure(uri: URI, error: Throwable): Unit

  private def prepareFolders(): Unit = {
    new File("hhbot/logs/robotstxt").mkdirs()
  }

  final def main(args: Array[String]): Unit = {
    prepareFolders()
    val runner = this
    val conf = ConfigFactory.load()
    val system = ActorSystem(configuration.agentName, conf)
    val requester = system.actorOf(Props(new Requester {
      def configuration = runner.configuration
      def seedURIs = runner.seedURIs
      def processResult(uri: URI, content: Array[Byte]) =
        runner.processResult(uri, content)
      def processFailure(uri: URI, error: Throwable) =
        runner.processFailure(uri, error)
    }), "Requester")
    import system.dispatcher
    system.scheduler.scheduleOnce(configuration.crawlDurationInMs.millis) {
      requester ! PoisonPill
      system.shutdown()
      System.exit(0)
    }
  }
}
