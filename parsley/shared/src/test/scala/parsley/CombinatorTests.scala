/*
 * Copyright 2020 Parsley Contributors <https://github.com/j-mie6/Parsley/graphs/contributors>
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package parsley

import Predef.{ArrowAssoc => _, _}

import parsley.combinator.{exactly => repeat, _}
import parsley.Parsley._
import parsley.registers.{forYieldP, forYieldP_, Reg}
import parsley.implicits.character.{charLift, stringLift}

class CombinatorTests extends ParsleyTest {
    "choice" should "fail if given the empty list" in {
        choice().parse("") shouldBe a [Failure[_]]
    }
    it should "behave like p for List(p)" in {
        choice('a').parse("") should equal ('a'.parse(""))
        choice('a').parse("a") should equal ('a'.parse("a"))
    }
    it should "parse in order" in {
        choice("a", "b", "bc", "bcd").parse("bcd") should be (Success("b"))
    }
    it should "fail if none of the parsers succeed" in {
        choice("a", "b", "bc", "bcd").parse("c") shouldBe a [Failure[_]]
    }

    "attemptChoice" should "correctly ensure the subparsers backtrack" in {
        attemptChoice("ac", "aba", "abc").parse("abc") should be (Success("abc"))
    }

    "exactly" should "be pure(Nil) for n <= 0" in {
        repeat(0, 'a').parse("a") should be (Success(Nil))
        repeat(-1, 'a').parse("a") should be (Success(Nil))
    }
    it should "parse n times for n > 0" in {
        for (n <- 0 to 100) repeat(n, 'a').parse("a"*n) should be (Success(("a" * n).toList))
    }
    it must "fail if n inputs are not present" in {
        repeat(2, 'a').parse("a") shouldBe a [Failure[_]]
    }

    "option" should "succeed with Some if p succeeds" in {
        option('a').parse("a") should be (Success(Some('a')))
    }
    it should "succeed with None if p fails without consumption" in {
        option('a').parse("") should be (Success(None))
    }
    it should "fail if p fails with consumption" in {
        option("ab").parse("a") shouldBe a [Failure[_]]
    }

    "decide" must "succeed for Some" in {
        decide('a'.map(Option(_))).parse("a") should be (Success('a'))
    }
    it must "fail for None" in {
        decide(pure(None)).parse("") shouldBe a [Failure[_]]
    }
    it must "succeed for None with an alternative" in {
        decide(pure(None), pure(7)).parse("") shouldBe Success(7)
    }
    it must "compose with option to become identity" in {
        decide(option(pure(7))).parse("") should be (pure(7).parse(""))
        decide(option('a')).parse("") shouldBe a [Failure[_]]
        'a'.parse("") shouldBe a [Failure[_]]
        decide(option("ab")).parse("a") shouldBe a [Failure[_]]
        "ab".parse("a") shouldBe a [Failure[_]]
    }

    "optional" must "succeed if p succeeds" in {
        optional('a').parse("a") should be (Success(()))
    }
    it must "also succeed if p fails without consumption" in {
        optional('a').parse("b") should be (Success(()))
    }
    it must "fail if p failed with consumption" in {
        optional("ab").parse("a") shouldBe a [Failure[_]]
    }

    "manyN" must "ensure that n are parsed" in {
        for (n <- 0 to 10) manyN(n, 'a').parse("a"*n) should be (Success(("a"*n).toList))
        for (n <- 0 to 10) manyN(n+1, 'a').parse("a"*n) shouldBe a [Failure[_]]
    }
    it should "not care if more are present" in {
        for (n <- 0 to 10) manyN(n/2, 'a').parse("a"*n) should be (Success(("a"*n).toList))
    }

    "skipManyN" must "ensure that n are parsed" in {
        for (n <- 0 to 10) skipManyN(n, 'a').parse("a"*n) should be (Success(()))
        for (n <- 0 to 10) skipManyN(n+1, 'a').parse("a"*n) shouldBe a [Failure[_]]
    }
    it should "not care if more are present" in {
        for (n <- 0 to 10) skipManyN(n/2, 'a').parse("a"*n) should be (Success(()))
    }

    "sepBy" must "accept empty input" in cases(sepBy('a', 'b')) (
        "" -> Some(Nil),
    )
    it must "not allow sep at the end of chain" in cases(sepBy('a', 'b')) (
        "ab" -> None,
    )
    it should "be able to parse 2 or more p" in cases(sepBy('a', 'b')) (
        "aba" -> Some(List('a', 'a')),
        "ababa" -> Some(List('a', 'a', 'a')),
        "abababa" -> Some(List('a', 'a', 'a', 'a')),
    )

    "sepBy1" must "require a p" in cases(sepBy1('a', 'b')) (
        "a" -> Some(List('a')),
        "" -> None,
    )

    "sepEndBy" must "accept empty input" in cases(sepEndBy('a', 'b')) (
        "" -> Some(Nil),
    )
    it should "not require sep at the end of chain" in cases(sepEndBy('a', 'b')) (
        "a" -> Some(List('a'))
    )
    it should "be able to parse 2 or more p" in cases(sepEndBy('a', 'b'))(
        "aba" -> Some(List('a', 'a')),
        "ababa" -> Some(List('a', 'a', 'a')),
    )
    it should "be able to parse a final sep" in cases(sepEndBy('a', 'b'))(
        "ab" -> Some(List('a')),
        "abab" -> Some(List('a', 'a')),
        "ababab" -> Some(List('a', 'a', 'a')),
    )
    it should "fail if p fails after consuming input" in cases(sepEndBy("aa", 'b')) (
        "ab" -> None,
    )
    it should "fail if sep fails after consuming input" in cases(sepEndBy('a', "bb")) (
        "ab" -> None,
    )
    it must "not corrupt the stack on sep hard-fail" in {
        ('c' <::> attempt(sepEndBy('a', "bb")).getOrElse(List('d'))).parse("cab") should be (Success(List('c', 'd')))
    }

    "sepEndBy1" must "require a p" in {
        sepEndBy1('a', 'b').parse("a") should not be a [Failure[_]]
        sepEndBy1('a', 'b').parse(input = "") shouldBe a [Failure[_]]
    }

    "endBy" must "accept empty input" in {
        endBy('a', 'b').parse("") should be (Success(Nil))
    }
    it must "require sep at end of chain" in {
        endBy('a', 'b').parse("a") shouldBe a [Failure[_]]
        endBy('a', 'b').parse("ab") should be (Success(List('a')))
    }
    it should "be able to parse 2 or more p" in {
        endBy('a', 'b').parse("abab") should be (Success(List('a', 'a')))
        endBy('a', 'b').parse("ababab") should be (Success(List('a', 'a', 'a')))
    }

    "endBy1" must "require a p" in {
        endBy1('a', 'b').parse("ab") should not be a [Failure[_]]
        endBy1('a', 'b').parse(input = "") shouldBe a [Failure[_]]
    }

    "eof" must "succeed at the end of input" in {
        eof.parse("") should not be a [Failure[_]]
    }
    it must "fail if input remains" in {
        eof.parse("a") shouldBe a [Failure[_]]
    }

    "manyUntil" must "require an end" in {
        manyUntil('a', 'b').parse("aa") shouldBe a [Failure[_]]
        manyUntil('a', 'b').parse("ab") should be (Success(List('a')))
    }
    it should "parse the end without result" in {
        manyUntil('a', 'b').parse("b") should be (Success(Nil))
    }
    it should "parse p until that end is found" in {
        manyUntil('a', 'b').parse("aaaaaaaaaaaab") should not be a [Failure[_]]
        manyUntil("aa", 'b').parse("ab") shouldBe a [Failure[_]]
    }

    "someUntil" must "parse at least 1 p" in {
        someUntil('a', 'b').parse("ab") should be (Success(List('a')))
        someUntil('a', 'b').parse("b") shouldBe a [Failure[_]]
    }

    "forYieldP" should "be able to parse context-sensitive grammars" in {
        val r1 = Reg.make[Int]
        def matching[A](p: Parsley[A]) = forYieldP[Int, A](r1.get, pure(_ != 0), pure(_ - 1)) {
            p
        }
        val abc = r1.put(0) *>
                  many('a' *> r1.modify(_ + 1)) *>
                  matching('b') *>
                  matching('c')
        abc.parse("aaabbbccc") should be (Success(List('c', 'c', 'c')))
        abc.parse("aaaabc") shouldBe a [Failure[_]]
    }

    "forYieldP_" should "be able to parse context-sensitive grammars" in {
        val r1 = Reg.make[Int]
        def matching[A](p: Parsley[A]) = forYieldP_[Int, A](r1.get, pure(_ != 0), pure(_ - 1)) { _ =>
            p
        }
        val abc = r1.put(0) *>
                  many('a' *> r1.modify(_ + 1)) *>
                  matching('b') *>
                  matching('c')
        abc.parse("aaabbbccc") should be (Success(List('c', 'c', 'c')))
        abc.parse("aaaabc") shouldBe a [Failure[_]]
    }
}
