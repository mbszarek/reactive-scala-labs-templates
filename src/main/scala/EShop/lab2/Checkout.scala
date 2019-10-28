package EShop
package lab2

import EShop.lab2.Checkout.{ExpireCheckout, ExpirePayment, StartCheckout}
import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable, Props, Scheduler}
import akka.event.{Logging, LoggingReceive}

import scala.concurrent.duration._
import scala.language.postfixOps

object Checkout {

  sealed trait Data
  case object Uninitialized                               extends Data
  case class SelectingDeliveryStarted(timer: Cancellable) extends Data
  case class ProcessingPaymentStarted(timer: Cancellable) extends Data

  sealed trait Command
  case object StartCheckout                       extends Command
  case class SelectDeliveryMethod(method: String) extends Command
  case object CancelCheckout                      extends Command
  case object ExpireCheckout                      extends Command
  case class SelectPayment(payment: String)       extends Command
  case object ExpirePayment                       extends Command
  case object ReceivePayment                      extends Command

  sealed trait Event
  case object CheckOutClosed                   extends Event
  case class PaymentStarted(payment: ActorRef) extends Event

  def props(cart: ActorRef) = Props(new Checkout())
}

class Checkout extends Actor with ActorLogging {
  import context.dispatcher
  import Checkout._

  private val scheduler = context.system.scheduler
  override val log      = Logging(context.system, this)

  val checkoutTimerDuration = 1.second
  val paymentTimerDuration  = 1.second

  private def checkoutTimer: Cancellable = scheduler.scheduleOnce(checkoutTimerDuration, self, ExpireCheckout)
  private def paymentTimer: Cancellable  = scheduler.scheduleOnce(paymentTimerDuration, self, ExpirePayment)

  def receive: Receive = LoggingReceive.withLabel("[State: receive]") {
    case StartCheckout =>
      context.become(selectingDelivery(checkoutTimer))
  }

  def selectingDelivery(timer: Cancellable): Receive = LoggingReceive.withLabel("[State: selecting delivery]") {
    case CancelCheckout | ExpireCheckout =>
      timer.cancelAndExecute {
        context.become(cancelled)
      }

    case SelectDeliveryMethod(_) =>
      timer.cancelAndExecute {
        context.become(selectingPaymentMethod(checkoutTimer))
      }
  }

  def selectingPaymentMethod(timer: Cancellable): Receive =
    LoggingReceive.withLabel("[State: selecting payment method]") {
      case CancelCheckout | ExpireCheckout =>
        timer.cancelAndExecute {
          context.become(cancelled)
        }

      case SelectPayment(_) =>
        timer.cancelAndExecute {
          context.become(processingPayment(paymentTimer))
        }
    }

  def processingPayment(timer: Cancellable): Receive = LoggingReceive.withLabel("[State: processing payment]") {
    case CancelCheckout | ExpirePayment =>
      timer.cancelAndExecute {
        context.become(cancelled)
      }

    case ReceivePayment =>
      timer.cancelAndExecute {
        context.become(closed)
      }
  }

  def cancelled: Receive = LoggingReceive.withLabel("[State: cancelled]") {
    case event => log.info("Checkout cancelled")
  }

  def closed: Receive = LoggingReceive.withLabel("[State: closed]") {
    case _ => log.info("Checkout closed")
  }

}
