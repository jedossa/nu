package nu.authorizer.ops

trait Morph[-A, +B] {
  def to(a: A): B
}

object Morph {
  def apply[A, B](implicit morph: Morph[A, B]): A Morph B = morph
}

sealed trait MorphSyntax {
  implicit final def morphOps[A](a: A): MorphOps[A] =
    new MorphOps(a)
}

final class MorphOps[A](private val a: A) extends AnyVal {
  def to[B: Morph[A, *]]: B = Morph[A, B].to(a)
}

object morphism extends MorphSyntax
