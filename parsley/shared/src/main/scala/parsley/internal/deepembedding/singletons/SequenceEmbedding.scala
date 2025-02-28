/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.internal.deepembedding.singletons

import parsley.internal.deepembedding.frontend.LazyParsleyIVisitor
import parsley.internal.machine.instructions

// Core Embedding
private [parsley] final class Pure[A](private [Pure] val x: A) extends Singleton[A] {
    // $COVERAGE-OFF$
    override def pretty: String = s"pure($x)"
    // $COVERAGE-ON$
    override def instr: instructions.Instr = new instructions.Push(x)

    override def visit[T, U[+_]](visitor: LazyParsleyIVisitor[T, U], context: T): U[A] = visitor.visit(this, context)(x)
}

private [parsley] final class Fresh[A](x: =>A) extends Singleton[A] {
    // $COVERAGE-OFF$
    override def pretty: String = s"fresh($x)"
    // $COVERAGE-ON$
    override def instr: instructions.Instr = new instructions.Fresh(x)

    override def visit[T, U[+_]](visitor: LazyParsleyIVisitor[T, U], context: T): U[A] = visitor.visit(this, context)(x)
}

private [deepembedding] object Pure {
    def unapply[A](self: Pure[A]): Some[A] = Some(self.x)
}
