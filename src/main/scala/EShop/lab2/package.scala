package EShop

import akka.actor.Cancellable

package object lab2 {
  implicit def cancellableOps(cancellable: Cancellable): CancellableOps = new CancellableOps(cancellable)

  final class CancellableOps(private val cancellable: Cancellable) extends AnyVal {
    def cancelAndExecute[T](action: => T): T = {
      cancellable.cancel()
      action
    }
  }
}
