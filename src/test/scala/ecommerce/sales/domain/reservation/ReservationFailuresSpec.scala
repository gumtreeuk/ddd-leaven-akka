package ecommerce.sales.domain.reservation

import test.support.EventsourcedAggregateRootSpec
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._
import ecommerce.sales.domain.reservation.Reservation.ReserveProduct
import ddd.support.domain.error.AggregateRootNotInitializedException
import ddd.support.domain.Representative._
import test.support.TestConfig._

class ReservationFailuresSpec extends EventsourcedAggregateRootSpec[Reservation](testSystem) {

  "Reservation of product" must {
    "fail if Reservation does not exist" in {
      val reservationId = "reservation1"
      implicit val timeout = Timeout(5, SECONDS)

      // Use ask (?) to send a command and expect Failure(AggregateRootNotInitializedException) in the response
      expectFailure[AggregateRootNotInitializedException] {
        representative[Reservation] ? ReserveProduct(reservationId, "product1", 1)
      }
    }
  }

}
