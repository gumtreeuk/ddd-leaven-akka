package ecommerce.sales.domain.reservation

import akka.actor.Props
import akka.testkit.TestProbe
import ddd.support.domain.AggregateRootActorFactory
import ddd.support.domain.Office._
import ddd.support.domain.protocol.Acknowledged
import ecommerce.sales.domain.reservation.Reservation._
import infrastructure.actor.PassivationConfig
import infrastructure.cluster.ReservationShardResolution
import infrastructure.cluster.ShardResolution.ShardResolutionStrategy
import test.support.{ ClusterConfig, ClusterSpec, LocalPublisher }

import scala.concurrent.duration._
import scala.reflect.ClassTag

class ReservationGlobalOfficeSpecMultiJvmNode1 extends ReservationGlobalOfficeSpec
class ReservationGlobalOfficeSpecMultiJvmNode2 extends ReservationGlobalOfficeSpec

class ReservationGlobalOfficeSpec extends ClusterSpec {

  import test.support.ClusterConfig._

  implicit val reservationActorFactory = new ReservationActorFactory

  class ReservationActorFactory extends AggregateRootActorFactory[Reservation] {
    override def props(passivationConfig: PassivationConfig): Props = Props(new Reservation(passivationConfig) with LocalPublisher)
  }

  def registerGlobalReservationOffice() {
    startSharding[Reservation](new ReservationShardResolution {
      //take last char of reservationId as shard id
      override def shardResolutionStrategy: ShardResolutionStrategy =
        aggregateIdResolver => {
          case msg => aggregateIdResolver(msg).last.toString
        }
    })
  }

  "Reservation global office" must {
    "given necessary infrastructure available" in {
      setupSharedJournal()
      joinCluster()
    }
    "given global reservation office available" in {
      registerGlobalReservationOffice()
    }

    enterBarrier("when")

    "distribute work evenly" in {
      val reservationOffice = globalOffice[Reservation]

      on(node1) {
        expectEventPublished[ReservationCreated] {
          reservationOffice ! CreateReservation("reservation-1", "client1")
          reservationOffice ! CreateReservation("reservation-2", "client2")
        }
      }

      on(node2) {
        expectEventPublished[ReservationCreated]
      }
    }

    "handle subsequent commands from anywhere" in {
      val reservationOffice = globalOffice[Reservation]

      on(node2) {
        expectReply(Acknowledged) {
          reservationOffice ! ReserveProduct("reservation-1", "product1", 1)
        }
        expectReply(Acknowledged) {
          reservationOffice ! ReserveProduct("reservation-2", "product1", 1)
        }
      }
    }

  }

  def expectReply[T, R](obj: T)(when: => R): R = {
    val r = when
    expectMsg(20.seconds, obj)
    r
  }

  def expectEventPublished[E](implicit t: ClassTag[E]) {
    expectEventPublished()
  }

  def expectEventPublished[E](when: Unit)(implicit t: ClassTag[E]) {
    val probe = TestProbe()
    system.eventStream.subscribe(probe.ref, t.runtimeClass)
    val r = when
    probe.expectMsgClass(20.seconds, t.runtimeClass)
    r
  }
}
