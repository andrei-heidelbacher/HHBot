package hhbot.fetcher

import akka.actor._
import akka.testkit.{ImplicitSender, TestKit, TestProbe}

import dispatch.Http

import org.scalatest.{BeforeAndAfterAll, GivenWhenThen, WordSpecLike}

import java.net.URI

import scala.concurrent.duration._

class FetcherSpec(_system: ActorSystem) extends TestKit(_system)
  with ImplicitSender
  with WordSpecLike
  with BeforeAndAfterAll
  with GivenWhenThen {
  import Fetcher._

  def this() = this(ActorSystem("FetcherSpec"))

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  private val http = Http.configure { _
    .setUserAgent("HHBot-Test")
    .setConnectionTimeoutInMs(2500)
    .setRequestTimeoutInMs(5000)
    .setFollowRedirects(true)
    .setMaximumNumberOfRedirects(3)
  }

  private def makeFetcher(): ActorRef = system.actorOf(Fetcher.props(http))

  private def makeSupervisedFetcher(): (TestProbe, ActorRef) = {
    val proxy = TestProbe()
    val parent = system.actorOf(Props(new Actor {
      val fetcher = context.actorOf(Fetcher.props(http))

      def receive = {
        case msg if sender() == fetcher => proxy.ref.forward(msg)
        case msg => fetcher.forward(msg)
      }
    }))
    (proxy, parent)
  }

  "A Fetcher actor" should {
    "send back a DemandRequest message given a RequestsAvailable message" in {
      import FetcherManager._

      val fetcher = makeFetcher()
      fetcher ! RequestsAvailable
      expectMsg(DemandRequest)
    }

    "resond to request and demand work from supervisor" in {
      Given("a supervised fetcher")
      val (supervisor, fetcher) = makeSupervisedFetcher()

      When("a FetchRequest message from a requester is received")
      fetcher ! FetchRequest(new URI("http://www.google.com"))

      Then("a DemandRequest message should be sent to supervisor")
      supervisor.expectMsg(DemandRequest)

      And("a FetchResult message should be sent to the requester")
      expectMsgPF(8.seconds) { case FetchResult(uri, content) => }
    }

    "demand work from supervisor" in {
      Given("a supervised fetcher")
      val (supervisor, fetcher) = makeSupervisedFetcher()

      When("a Failure message is sent from a requester")
      fetcher ! Status.Failure(new Throwable("Timeout exception!"))

      Then("a DemandRequest message should be sent to supervisor")
      supervisor.expectMsg(DemandRequest)
    }
  }
}
