package hhbot.frontier

import akka.actor._
import akka.testkit.{ImplicitSender, TestKit, TestProbe}

import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

import scala.concurrent.{Await, Promise}
import scala.concurrent.duration._
import scala.util.Try

import hhbot.fetcher._

class HostResolverSpec(_system: ActorSystem) extends TestKit(_system)
  with ImplicitSender
  with WordSpecLike
  with BeforeAndAfterAll {
  import HostResolver._
  import Fetcher._

  def this() = this(ActorSystem("HostResolverSpec"))

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  class TestFetcher(testProbe: ActorRef) extends Actor {
    private def wait(duration: Duration): Unit = {
      try {
        Await.ready(Promise[Unit]().future, duration)
      }
      catch {
        case e: Throwable =>
      }
    }

    private def fetchRobots =
      "User-agent: *\nDisallow: /\nSitemap: http://www.google.com/sitemap.txt"
        .getBytes("UTF-8")

    private def fetchSitemap =
      "http://www.google.com/text\nhttp://www.google.com/news\n"
        .getBytes("UTF-8")

    def receive = {
      case FetchRequest(uri) =>
        testProbe.forward(FetchRequest(uri))
        wait(1.seconds)
        val page =
          if (uri.getPath == "/robots.txt") fetchRobots else fetchSitemap
        sender() ! FetchResult(uri, Try(page))
    }
  }

  private def makeResolver(testProbe: ActorRef): ActorRef =
    system.actorOf(HostResolver.props(Props(new TestFetcher(testProbe))))

  "A HostResolver actor" should {
    "send a FetchRequest message" in {
      val probe = TestProbe()
      val resolver = makeResolver(probe.ref)
      probe.send(resolver, ResolveHost("http://www.google.com"))
      probe.expectMsgPF() { case FetchRequest(_) => }
    }

    "send a PushRobotstxt message" in {
      val probe = TestProbe()
      val resolver = makeResolver(probe.ref)
      probe.ignoreMsg { case FetchRequest(_) => true }
      probe.send(resolver, ResolveHost("http://www.google.com"))
      probe.expectMsgPF() { case PushRobotstxt(_) => }
    }

    "send a Push message" in {
      import HostManager._

      val probe = TestProbe()
      val resolver = makeResolver(probe.ref)
      probe.ignoreMsg {
        case FetchRequest(_) => true
        case PushRobotstxt(_) => true
      }
      probe.send(resolver, ResolveHost("http://www.google.com"))
      probe.expectMsgPF() { case Push(_) => }
      probe.expectMsgPF() { case Push(_) => }
    }

    "send PoisonPill to self after 10 seconds" in {
      import HostManager._

      val probe = TestProbe()
      val resolver = makeResolver(probe.ref)
      probe.ignoreMsg {
        case FetchRequest(_) => true
        case PushRobotstxt(_) => true
        case Push(_) => true
      }
      probe.watch(resolver)
      probe.send(resolver, ResolveHost("http://www.google.com"))
      probe.expectNoMsg(9.seconds)
      probe.expectMsgPF(11.seconds) { case Terminated(_) => }
    }
  }
}
