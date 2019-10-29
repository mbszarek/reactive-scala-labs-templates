package EShop.lab2

import EShop.lab2.CartFSM.Status
import akka.actor.{LoggingFSM, Props}

import scala.concurrent.duration._
import scala.language.postfixOps

object CartFSM {

  object Status extends Enumeration {
    type Status = Value
    val Empty, NonEmpty, InCheckout = Value
  }

  def props() = Props(new CartFSM())
}

class CartFSM extends LoggingFSM[Status.Value, Cart] {
  import EShop.lab2.CartFSM.Status._
  import CartActor._

  // useful for debugging, see: https://doc.akka.io/docs/akka/current/fsm.html#rolling-event-log
  override def logDepth = 12

  val cartTimerDuration: FiniteDuration = 1 seconds

  startWith(Empty, Cart.empty)

  when(Empty) {
    case Event(AddItem(item), _) =>
      goto(NonEmpty) using Cart.empty.addItem(item)
  }

  when(NonEmpty, stateTimeout = cartTimerDuration) {
    case Event(RemoveItem(item), cart) =>
      val newCart = cart.removeItem(item)
      if (newCart.size > 0)
        goto(NonEmpty) using newCart
      else
        goto(Empty) using Cart.empty

    case Event(AddItem(item), cart) =>
      goto(NonEmpty) using cart.addItem(item)

    case Event(StartCheckout, cart) =>
      goto(InCheckout) using cart

    case Event(ExpireCart | StateTimeout, _) =>
      goto(Empty) using Cart.empty
  }

  when(InCheckout) {
    case Event(CancelCheckout, cart) =>
      goto(NonEmpty) using cart

    case Event(CloseCheckout, _) =>
      goto(Empty) using Cart.empty
  }

}
