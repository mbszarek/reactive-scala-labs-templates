package EShop.lab2

import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable, Props, Scheduler}
import akka.event.{Logging, LoggingReceive}

import scala.concurrent.duration._
import scala.language.postfixOps

object CartActor {

  sealed trait Command
  case class AddItem(item: Any)    extends Command
  case class RemoveItem(item: Any) extends Command
  case object ExpireCart           extends Command
  case object StartCheckout        extends Command
  case object CancelCheckout       extends Command
  case object CloseCheckout        extends Command

  sealed trait Event
  case class CheckoutStarted(checkoutRef: ActorRef) extends Event

  def props = Props(new CartActor())
}

class CartActor extends Actor with ActorLogging {
  import CartActor._
  import context.dispatcher

  override val log      = Logging(context.system, this)
  val cartTimerDuration = 5 seconds

  private def akkaScheduler: Scheduler = context.system.scheduler

  private def scheduleTimer: Cancellable = akkaScheduler.scheduleOnce(cartTimerDuration, self, ExpireCart)

  def receive: Receive = empty

  def empty: Receive = LoggingReceive.withLabel("[State: empty]") {
    case AddItem(item) =>
      context.become(nonEmpty(Cart.empty.addItem(item), scheduleTimer))
  }

  def nonEmpty(cart: Cart, timer: Cancellable): Receive = LoggingReceive.withLabel("[State: nonEmpty]") {
    case AddItem(item) =>
      timer.cancelAndExecute {
        context.become(nonEmpty(cart.addItem(item), scheduleTimer))
      }

    case RemoveItem(item) if cart.contains(item) && cart.size == 1 =>
      timer.cancelAndExecute {
        context.become(empty)
      }

    case RemoveItem(item) =>
      timer.cancelAndExecute {
        context.become(nonEmpty(cart.removeItem(item), scheduleTimer))
      }

    case StartCheckout =>
      timer.cancelAndExecute {
        context.become(inCheckout(cart))
      }

    case ExpireCart =>
      timer.cancelAndExecute {
        context.become(empty)
      }
  }

  def inCheckout(cart: Cart): Receive = LoggingReceive.withLabel("[State: inCheckout]") {
    case CancelCheckout =>
      context.become(nonEmpty(cart, scheduleTimer))

    case CloseCheckout =>
      context.become(empty)
  }

}
