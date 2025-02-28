/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley.token.symbol

import parsley.Parsley, Parsley.attempt
import parsley.character.{char, string}
import parsley.token.descriptions.{NameDesc, SymbolDesc}
import parsley.token.errors.ErrorConfig

import parsley.internal.deepembedding.singletons.token

/** This class' implementation has been optimised for performance. If you've stumbled on
  * this file hoping to learn about how it works, this is the wrong place. The original,
  * unoptimised implementation is preserved for testing in the corresponding place in the
  * "test" folder. This is because a requirement of optimisation is that the semantics can
  * still be implemented by plain combinators. Go look there!
  */
private [token] class ConcreteSymbol(nameDesc: NameDesc, symbolDesc: SymbolDesc, err: ErrorConfig) extends Symbol(err) {

    override def apply(name: String): Parsley[Unit] = {
        require(name.nonEmpty, "Symbols may not be empty strings")
        if (symbolDesc.hardKeywords(name))       softKeyword(name)
        else if (symbolDesc.hardOperators(name)) softOperator(name)
        else                                     attempt(string(name)).void
    }

    override def apply(name: Char): Parsley[Unit] = char(name).void

    override def softKeyword(name: String): Parsley[Unit] = {
        require(name.nonEmpty, "Keywords may not be empty strings")
        new Parsley(new token.SoftKeyword(name, nameDesc.identifierLetter, symbolDesc.caseSensitive,
                                          err.labelSymbolKeyword(name), err.labelSymbolEndOfKeyword(name)))
    }

    override def softOperator(name: String): Parsley[Unit] = {
        require(name.nonEmpty, "Operators may not be empty strings")
        new Parsley(new token.SoftOperator(name, nameDesc.operatorLetter, symbolDesc.hardOperatorsTrie,
                                           err.labelSymbolOperator(name), err.labelSymbolEndOfOperator(name)))
    }
}
