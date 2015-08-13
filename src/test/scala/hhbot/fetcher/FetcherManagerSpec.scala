package hhbot.fetcher

import akka.actor._
import akka.testkit.{ImplicitSender, TestKit, TestProbe}

import org.scalatest.{BeforeAndAfterAll, GivenWhenThen, WordSpecLike}

import java.net.URI

import scala.concurrent.{Await, Promise}
import scala.concurrent.duration._
import scala.util.Try

class FetcherManagerSpec(_system: ActorSystem) extends TestKit(_system)
with ImplicitSender
with WordSpecLike
with BeforeAndAfterAll
with GivenWhenThen {
  import FetcherManager._

  def this() = this(ActorSystem("FetcherSpec"))

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  object TestFetcher {
    case object FetcherCreated

    def props(testProbe: ActorRef): Props = Props(new TestFetcher(testProbe))
  }

  class TestFetcher(testProbe: ActorRef) extends Actor {
    import TestFetcher._
    import Fetcher._

    wait(100.millis)
    testProbe ! FetcherCreated

    private def wait(duration: Duration): Unit = {
      try {
        Await.ready(Promise[Unit]().future, duration)
      }
      catch {
        case e: Throwable =>
      }
    }

    def receive = {
      case RequestsAvailable =>
        testProbe.forward(RequestsAvailable)
        sender() ! DemandRequest
      case FetchRequest(uri) =>
        testProbe.forward(FetchRequest(uri))
        wait(2.seconds)
        sender() ! FetchResult(uri, Try(Array[Byte](55, 120, 23, 44)))
    }
  }

  private def makeSupervisedManager(count: Int): (TestProbe, ActorRef) = {
    val proxy = TestProbe()
    val parent = system.actorOf(Props(new Actor {
      val manager =
        context.actorOf(props(TestFetcher.props(proxy.ref), count), "FM")

      def receive = {
        case msg if sender() == proxy.ref => manager.forward(msg)
        case msg => proxy.ref.forward(msg)
      }
    }))
    (proxy, parent)
  }

  "A FetcherManager" should {
    "create a fixed number of fetchers" in {
      import TestFetcher._

      val fetcherCount = 32
      val (probe, _) = makeSupervisedManager(fetcherCount)
      for (_ <- 1 to fetcherCount) {
        probe.expectMsg(FetcherCreated)
      }
      probe.expectNoMsg(500.millis)
    }

    "redirect DemandRequest" in {
      import TestFetcher._
      import Fetcher._

      val fetcherCount = 1
      val (probe, _) = makeSupervisedManager(fetcherCount)
      probe.expectMsg(FetcherCreated)
      probe.expectMsg(RequestsAvailable)
      probe.expectMsg(DemandRequest)
      probe.expectNoMsg(2.seconds)
    }

    "send RequestsAvailable periodically" in {
      import TestFetcher._
      import Fetcher._

      val fetcherCount = 16
      val (probe, _) = makeSupervisedManager(fetcherCount)
      probe.ignoreMsg { case FetcherCreated => true }
      for (_ <- 1 to 3) {
        for (_ <- 1 to 2 * fetcherCount) {
          probe.expectMsgPF() {
            case RequestsAvailable =>
            case DemandRequest =>
          }
        }
      }
    }

    "redirect FetchRequest" in {
      import TestFetcher._
      import Fetcher._

      val fetcherCount = 16
      val (probe, manager) = makeSupervisedManager(fetcherCount)
      probe.ignoreMsg {
        case FetcherCreated => true
        case RequestsAvailable => true
      }
      for (_ <- 1 to 2 * fetcherCount) {
        probe.expectMsgPF() {
          case DemandRequest =>
            probe.send(manager, FetchRequest(new URI("http://www.google.ro")))
          case FetchRequest(uri) =>
        }
      }
      probe.expectNoMsg(1500.millis)
    }

    "send FetchResult" in {
      import TestFetcher._
      import Fetcher._

      val fetcherCount = 1
      val (probe, _) = makeSupervisedManager(fetcherCount)
      probe.ignoreMsg {
        case FetcherCreated => true
        case RequestsAvailable => true
      }
      probe.expectMsgPF() {
        case DemandRequest => probe.send(
            probe.sender(),
            FetchRequest(new URI("http://www.google.ro")))
      }
      probe.expectMsgPF() { case FetchRequest(_) => }
      probe.expectMsgPF() { case FetchResult(_, _) => }
    }
  }
}
