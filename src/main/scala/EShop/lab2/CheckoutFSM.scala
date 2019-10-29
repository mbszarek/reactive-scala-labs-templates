package EShop
package lab2

import EShop.lab2.Checkout.Data
import EShop.lab2.CheckoutFSM.Status
import akka.actor.{ActorRef, Cancellable, LoggingFSM, Props}

import scala.concurrent.duration._
import scala.language.postfixOps

object CheckoutFSM {

  object Status extends Enumeration {
    type Status = Value
    val NotStarted, SelectingDelivery, SelectingPaymentMethod, Cancelled, ProcessingPayment, Closed = Value
  }

  def props(cartActor: ActorRef) = Props(new CheckoutFSM)
}

class CheckoutFSM extends LoggingFSM[Status.Value, Data] {
  import EShop.lab2.CheckoutFSM.Status._
  import Checkout._
  import context.dispatcher

  // useful for debugging, see: https://doc.akka.io/docs/akka/current/fsm.html#rolling-event-log
  override def logDepth = 12

  val checkoutTimerDuration: FiniteDuration = 1 seconds
  val paymentTimerDuration: FiniteDuration  = 1 seconds

  private val scheduler = context.system.scheduler

  private def checkoutTimer: Cancellable = scheduler.scheduleOnce(checkoutTimerDuration, self, ExpireCheckout)
  private def paymentTimer: Cancellable  = scheduler.scheduleOnce(paymentTimerDuration, self, ExpirePayment)

  startWith(NotStarted, Uninitialized)

  when(NotStarted) {
    case Event(StartCheckout, _) =>
      goto(SelectingDelivery) using SelectingDeliveryStarted(checkoutTimer)
  }

  when(SelectingDelivery) {
    case Event(SelectDeliveryMethod(_), _) =>
      goto(SelectingPaymentMethod)

    case Event(CancelCheckout | ExpireCheckout, SelectingDeliveryStarted(timer)) =>
      timer.cancelAndExecute {
        goto(Cancelled)
      }
  }

  when(SelectingPaymentMethod) {
    case Event(SelectPayment(_), SelectingDeliveryStarted(timer)) =>
      timer.cancelAndExecute {
        goto(ProcessingPayment) using ProcessingPaymentStarted(paymentTimer)
      }

    case Event(CancelCheckout | ExpireCheckout, SelectingDeliveryStarted(timer)) =>
      timer.cancelAndExecute {
        goto(Cancelled)
      }
  }

  when(ProcessingPayment) {
    case Event(ReceivePayment, ProcessingPaymentStarted(timer)) =>
      timer.cancelAndExecute {
        goto(Closed)
      }

    case Event(CancelCheckout | ExpirePayment | ExpireCheckout, ProcessingPaymentStarted(timer)) =>
      timer.cancelAndExecute {
        goto(Cancelled)
      }
  }

  when(Cancelled) {
    case _ => stay
  }

  when(Closed) {
    case _ => stay
  }

}
