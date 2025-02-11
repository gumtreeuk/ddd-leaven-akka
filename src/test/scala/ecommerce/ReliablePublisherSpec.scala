package ecommerce

import test.support.{DummyAggregateRoot, ReliableEventHandler, EventsourcedAggregateRootSpec}
import test.support.TestConfig._
import ddd.support.domain.event.DomainEvent
import org.mockito.Matchers.isA
import org.mockito.BDDMockito.given
import akka.actor.Props
import infrastructure.akka.event.{RedeliveryFailedException, ReliablePublisher}
import scala.concurrent.duration._
import ddd.support.domain.protocol.Acknowledged
import org.mockito.Mockito
import test.support.DummyAggregateRoot.{Created, Create}
import ddd.support.domain.command.CommandMessage
import org.mockito.stubbing.Answer
import org.mockito.invocation.InvocationOnMock

class ReliablePublisherSpec extends EventsourcedAggregateRootSpec[DummyAggregateRoot](testSystem) {

  "Event published from AR" should {
    "be eventually delivered to configured destination" in {
      // given
      def exception(counter: Int) = new RuntimeException(s"Destination handler failed... [$counter].")

      val handler = Mockito.mock(classOf[Function[DomainEvent, Unit]])
      given(handler.apply(isA(classOf[DomainEvent])))
        .willThrow(exception(1))
        .willThrow(exception(2))
        .willThrow(exception(3))
        .willThrow(exception(4))
        .will(new Answer[Unit] {
          override def answer(invocation: InvocationOnMock): Unit = {
            system.eventStream.publish(Created())
          }
        })

      val reliableDestination = system.actorOf(ReliableEventHandler.props(handler))

      val aggregateRoot = system.actorOf(Props(new DummyAggregateRoot with ReliablePublisher {
        override val target = reliableDestination.path
        override val redeliverInterval = 500.millis
        override val redeliverMax = 2
      }))

      // when
      aggregateRoot ! CommandMessage(Create())

      // then
      expectReply(Acknowledged)
      expectExceptionLogged[RedeliveryFailedException]()
      expectEventPublished[Created]
    }
  }

}
