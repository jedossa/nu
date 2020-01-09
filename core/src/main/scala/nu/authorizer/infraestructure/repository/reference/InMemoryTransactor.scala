package nu.authorizer.infraestructure.repository.reference

import cats.Applicative
import cats.effect.concurrent.Ref

trait Transactor[F[_], A] {
  val ref: Ref[F, A]
  val storage: A
}

final class InMemoryTransactor[F[_]: Applicative, A] private (val ref: Ref[F, A], val storage: A)
  extends Transactor[F, A]

object InMemoryTransactor {
  def apply[F[_]: Applicative, A](ref: Ref[F, A], storage: A): Transactor[F, A] = new InMemoryTransactor(ref, storage)
}
