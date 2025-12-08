package io.itsvks.anyhow

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class ResultTest : DescribeSpec({
    describe("and test") {
        it("returns value if ok") {
            Ok(300) and Ok(500) shouldBe Ok(500)
        }

        it("returns error if err") {
            Ok(300) and Err("boom") shouldBe Err("boom")
        }
    }

    describe("andThen test") {
        it("returns transformed value if ok") {
            Ok(5).andThen { Ok(it + 5) } shouldBe Ok(10)
        }

        it("returns error if err") {
            Ok(20)
                .andThen { Ok(it + 43) }
                .andThen { Err("boom") } shouldBe Err("boom")
        }
    }

    describe("runCatching test") {
        it("returns Ok if invocation successful") {
            runCatching { "yo" } shouldBe Ok("yo")
        }

        it("returns Err if invocation fails") {
            val error = IllegalStateException("boom")
            runCatching { throw error } shouldBe Err(error)
        }
    }

    describe("toResultOr test") {
        it("returns ok if non-null") {
            "ok".toResultOr { "err" }.get() shouldBe "ok"
        }

        it("returns err if null") {
            null.toResultOr { "err" } shouldBe Err("err")
        }
    }

    describe("get test") {
        it("returns value if ok") {
            Ok(300).get() shouldBe 300
        }

        it("returns null if err") {
            Err("boom").getOrNull().shouldBeNull()
        }
    }

    describe("getError test") {
        it("returns null if ok") {
            Ok("Vvv").getError().shouldBeNull()
        }

        it("returns error if err") {
            Err("boom").getError() shouldBe "boom"
        }
    }

    describe("getOr test") {
        it("returns value if ok") {
            Ok("hello").getOr("world") shouldBe "hello"
        }

        it("returns default if err") {
            Err("boom").getOr("haha") shouldBe "haha"
        }
    }

    describe("getOrThrow test") {
        it("returns value if ok") {
            Ok("hello").getOrThrow() shouldBe "hello"
        }

        it("throws error if err") {
            shouldThrow<IllegalStateException> {
                Err(IllegalStateException("boom")).getOrThrow()
            }
        }
    }

    describe("getOrThrow with transform") {
        it("returns value if ok") {
            Ok("hello").getOrThrow { IllegalStateException("boom") } shouldBe "hello"
        }

        it("throws transformed error if err") {
            shouldThrowExactly<IllegalStateException> {
                Err("error").getOrThrow(::IllegalStateException)
            }
        }
    }

    describe("getErrorOr test") {
        it("returns default value if ok") {
            Ok("hello").getErrorOr("world") shouldBe "world"
        }

        it("returns error if err") {
            Err("boom").getErrorOr("haha") shouldBe "boom"
        }
    }

    describe("getOrElse test") {
        it("returns value if ok") {
            Ok("hello").getOrElse { "world" } shouldBe "hello"
        }

        it("returns transformed value if err") {
            Err("boom").getOrElse { "haha" } shouldBe "haha"
        }
    }

    describe("getErrorOrElse test") {
        it("returns transformed value if ok") {
            Ok("hello").getErrorOrElse { "world" } shouldBe "world"
        }

        it("returns error if err") {
            Err("boom").getErrorOrElse { "haha" } shouldBe "boom"
        }
    }

    describe("merge") {
        it("returns value if ok") {
            val left: Result<Left, Left> = Ok(Left)
            val right: Result<Right, Left> = Ok(Right)

            val result: Result<Direction, Direction> = left.flatMapEither(
                ok = { left },
                err = { right }
            )

            val direction = result.merge()
            direction shouldBe Left
        }

        it("returns error if err") {
            val left: Result<Left, Left> = Err(Left)
            val right: Result<Right, Right> = Err(Right)

            val result: Result<Direction, Direction> = left.flatMapEither(
                ok = { left },
                err = { right }
            )

            val direction = result.merge()
            direction shouldBe Right
        }
    }

    describe("iterable test") {
        describe("fold test") {
            it("return accumulated value if ok") {
                val result = listOf(20, 30, 40, 50).fold(
                    initial = 10,
                    operation = { a, b -> Ok(a + b) }
                )

                result shouldBe Ok(150)
            }

            it("returns first error if err") {
                val result = listOf(5, 10, 15, 20, 25).fold(
                    initial = 1,
                    operation = { a, b ->
                        when (b) {
                            (5 + 10) -> Err(IterableError.IterableError1)
                            (5 + 10 + 15 + 20) -> Err(IterableError.IterableError2)
                            else -> Ok(a * b)
                        }
                    },
                )

                result shouldBe Err(IterableError.IterableError1)
            }
        }

        describe("foldRight test") {
            it("return accumulated value if ok") {
                val result = listOf(2, 5, 10, 20).foldRight(
                    initial = 100,
                    operation = { a, b -> Ok(b - a) },
                )

                result shouldBe Ok(63)
            }

            it("returns last error if err") {
                val result = listOf(2, 5, 10, 20, 40).foldRight(
                    initial = 38500,
                    operation = { a, b ->
                        when (b) {
                            (((38500 / 40) / 20) / 10) -> Err(IterableError.IterableError1)
                            ((38500 / 40) / 20) -> Err(IterableError.IterableError2)
                            else -> Ok(b / a)
                        }
                    },
                )

                result shouldBe Err(IterableError.IterableError2)
            }
        }

        describe("combine test") {
            it("returns values if all ok") {
                val result = combine(Ok(10), Ok(20), Ok(30))
                result shouldBe Ok(listOf(10, 20, 30))
            }

            it("returns first error if any err") {
                val result = combine(
                    Ok(20),
                    Ok(40),
                    Err(IterableError.IterableError1),
                    Ok(60),
                    Err(IterableError.IterableError2),
                    Ok(80)
                )

                result shouldBe Err(IterableError.IterableError1)
            }
        }

        describe("valuesOf test") {
            it("returns all values") {
                val result = valuesOf(
                    Ok("hello"),
                    Ok("big"),
                    Err(IterableError.IterableError2),
                    Ok("wide"),
                    Err(IterableError.IterableError1),
                    Ok("world")
                )

                result shouldBe listOf("hello", "big", "wide", "world")
            }
        }

        describe("errorsOf test") {
            it("returns all errors") {
                val result = errorsOf(
                    Err(IterableError.IterableError2),
                    Ok("haskell"),
                    Err(IterableError.IterableError2),
                    Ok("f#"),
                    Err(IterableError.IterableError1),
                    Ok("elm"),
                    Err(IterableError.IterableError1),
                    Ok("clojure"),
                    Err(IterableError.IterableError2),
                )

                result shouldBe listOf(
                    IterableError.IterableError2,
                    IterableError.IterableError2,
                    IterableError.IterableError1,
                    IterableError.IterableError1,
                    IterableError.IterableError2,
                )
            }
        }

        describe("partition test") {
            it("returns pair of values and errors") {
                val strings = listOf(
                    "haskell",
                    "f#",
                    "elm",
                    "clojure",
                )

                val errors = listOf(
                    IterableError.IterableError2,
                    IterableError.IterableError2,
                    IterableError.IterableError1,
                    IterableError.IterableError1,
                    IterableError.IterableError2,
                )

                val result = partition(
                    Err(IterableError.IterableError2),
                    Ok("haskell"),
                    Err(IterableError.IterableError2),
                    Ok("f#"),
                    Err(IterableError.IterableError1),
                    Ok("elm"),
                    Err(IterableError.IterableError1),
                    Ok("clojure"),
                    Err(IterableError.IterableError2)
                )

                result shouldBe Pair(strings, errors)
            }
        }
    }

    describe("map test") {
        it("returns transformed value if ok") {
            val value: Result<Int, MapErr> = Ok(10)
            value.map { it + 20 } shouldBe Ok(30)
        }

        it("returns error if err") {
            val value: Result<Int, MapErr> = Err(MapErr.HelloError)
            value.map { "hello $it" } shouldBe Err(MapErr.HelloError)
        }
    }

    describe("mapCatching test") {
        it("returns transformed value if ok") {
            val value: Result<Int, Throwable> = Ok(10)
            value.mapCatching { it + 20 } shouldBe Ok(30)
        }

        it("returns err if transformation throws") {
            val value: Result<Int, Throwable> = Ok(10)
            val mapException = object : Throwable() {}
            value.mapCatching { throw mapException } shouldBe Err(mapException)
        }

        it("returns error if err") {
            val mapException = object : Throwable() {}
            val value: Result<Int, Throwable> = Err(mapException)
            value.mapCatching { "hello $it" } shouldBe Err(mapException)
        }
    }

    describe("transpose test") {
        it("returns null if ok and value is null") {
            val result = Ok(null)
            result.transpose().shouldBeNull()
        }

        it("returns ok if value if not null") {
            val result = Ok("hello")
            result.transpose() shouldBe Ok("hello")
        }

        it("returns error if err") {
            val result = Err("error")
            result.transpose() shouldBe Err("error")
        }
    }

    describe("flatten test") {
        it("returns flattened value if ok") {
            val result = Ok(Ok("hello"))
            result.flatten() shouldBe Ok("hello")
        }

        it("returns flattened err if err") {
            val result = Ok(Err("error"))
            result.flatten() shouldBe Err("error")
        }

        it("returns err if flat err") {
            val result = Err(33)
            Err(33) shouldBe result.flatten()
        }

        it("returns flattened nested result") {
            val result = Ok(Ok(Ok("hello")))
            result.flatten() shouldBe Ok(Ok("hello"))
            result.flatten().flatten() shouldBe Ok("hello")
        }
    }

    describe("mapError test") {
        it("returns value if ok") {
            val value: Result<Int, MapErr> = Ok(70)
            value.mapError { MapErr.WorldError } shouldBe Ok(70)
        }

        it("returns error if err") {
            val value: Result<Int, MapErr> = Err(MapErr.HelloError)
            value.mapError { MapErr.WorldError } shouldBe Err(MapErr.WorldError)
        }
    }

    describe("mapOr test") {
        it("returns transformed value if ok") {
            val value: Result<String, String> = Ok("foo")
            value.mapOr(33, String::length) shouldBe 3
        }

        it("returns default value if err") {
            val value: Result<String, String> = Err("foo")
            value.mapOr(33, String::length) shouldBe 33
        }
    }

    describe("mapOrElse test") {
        val k = 21

        it("returns transformed value if ok") {
            val value: Result<String, String> = Ok("foo")
            value.mapOrElse({ k * 2 }, String::length) shouldBe 3
        }

        it("returns default value if err") {
            val value: Result<String, String> = Err("foo")
            value.mapOrElse({ k * 2 }, String::length) shouldBe 42
        }
    }

    describe("mapBoth test") {
        it("returns transformed value if ok") {
            val value: Result<Int, Long> = Ok(50)

            val result = value.mapBoth(
                ok = { "good $it" },
                err = { "bad $it" },
            )

            result shouldBe "good 50"
        }

        it("returns transformed value if err") {
            val value: Result<Int, Long> = Err(50)

            val result = value.mapBoth(
                ok = { "good $it" },
                err = { "bad $it" },
            )

            result shouldBe "bad 50"
        }
    }

    describe("flatMapBoth test") {
        it("returns transformed value if ok") {
            val value: Result<Int, Long> = Ok(50)

            val result: Result<String, Long> = value.flatMapBoth(
                ok = { Ok("good $it") },
                err = { Err(100L) },
            )

            result shouldBe Ok("good 50")
        }

        it("returns transformed value if err") {
            val result: Result<Int, Long> = Err(25L)

            val value: Result<String, Long> = result.flatMapBoth(
                ok = { Ok("good $it") },
                err = { Err(100L) },
            )

            value shouldBe Err(100L)
        }
    }

    describe("mapEither test") {
        it("returns transformed value if ok") {
            val value: Result<Int, MapErr.HelloError> = Ok(500)

            val result: Result<Long, MapErr.CustomError> = value.mapEither(
                ok = { it + 500L },
                err = { MapErr.CustomError("$it") },
            )

            result shouldBe Ok(1000L)
        }

        it("returns transformed value if err") {
            val value: Result<Int, MapErr.HelloError> = Err(MapErr.HelloError)

            val result: Result<Long, MapErr.CustomError> = value.mapEither(
                ok = { it + 500L },
                err = { MapErr.CustomError("bad") },
            )

            result shouldBe Err(MapErr.CustomError("bad"))
        }
    }

    describe("flatMapEither test") {
        it("returns transformed value if ok") {
            val value: Result<Int, MapErr.HelloError> = Ok(500)

            val result: Result<Long, MapErr.CustomError> = value.flatMapEither(
                ok = { Ok(it + 500L) },
                err = { Err(MapErr.CustomError("$it")) },
            )

            result shouldBe Ok(1000L)
        }

        it("returns transformed value if err") {
            val value: Result<Int, MapErr.HelloError> = Err(MapErr.HelloError)

            val result: Result<Long, MapErr.CustomError> = value.flatMapEither(
                ok = { Ok(it + 500L) },
                err = { Err(MapErr.CustomError("bad")) },
            )

            result shouldBe Err(MapErr.CustomError("bad"))
        }
    }

    describe("toErrorIfNull test") {
        it("returns value if ok") {
            Ok("a").toErrorIfNull { "b" } shouldBe Ok("a")
        }

        it("returns transformed error if null") {
            Ok(null).toErrorIfNull { "b" } shouldBe Err("b")
        }

        it("returns error if err") {
            Err("a").toErrorIfNull { "b" } shouldBe Err("a")
        }
    }

    describe("toErrorUnlessNull test") {
        it("returns transformed error if not null") {
            Ok("a").toErrorUnlessNull { "b" } shouldBe Err("b")
        }

        it("returns value if null") {
            Ok(null).toErrorUnlessNull { "b" } shouldBe Ok(null)
        }

        it("returns error if err") {
            Err("a").toErrorUnlessNull { "b" } shouldBe Err("b")
        }
    }

    describe("onSuccess test") {
        it("invokes action if ok") {
            val counter = Counter(50)
            Ok(counter).onSuccess { it.count += 50 }
            counter.count shouldBe 100
        }

        it("does not invoke action if err") {
            val counter = Counter(100)
            Err(CounterError).onSuccess { counter.count -= 50 }
            counter.count shouldBe 100
        }
    }

    describe("onFailure test") {
        it("invokes action if err") {
            val counter = Counter(333)
            Err(CounterError).onFailure { counter.count += 100 }
            counter.count shouldBe 433
        }

        it("does not invoke action if ok") {
            val counter = Counter(333)
            Ok(counter).onFailure { counter.count -= 100 }
            counter.count shouldBe 333
        }
    }

    describe("or test") {
        it("returns value if ok") {
            Ok(500) or Ok(600) shouldBe Ok(500)
        }

        it("returns default value if err") {
            Ok(500) or Err("boom") shouldBe Ok(500)
        }
    }

    describe("orElse test") {
        it("returns value if ok") {
            Ok(500).orElse { Ok(600) } shouldBe Ok(500)
        }

        it("returns transformed value if err") {
            Err(5050).orElse { Ok(4040) } shouldBe Ok(4040)
        }
    }

    describe("orElseThrow test") {
        it("returns value if ok") {
            Ok(4040).orElseThrow() shouldBe Ok(4040)
        }

        it("throws error if err") {
            val result = Err(RuntimeException("or else throw"))
            shouldThrow<RuntimeException>(result::orElseThrow)
        }
    }

    describe("throwIf test") {
        it("returns value if ok") {
            Ok(500).throwIf { true } shouldBe Ok(500)
        }

        it("returns error if predicate does not match") {
            val result = Err(RuntimeException("throw if"))
            result.throwIf { false } shouldBe result
        }

        it("throws error if predicate matches") {
            val result = Err(RuntimeException("throw if"))
            shouldThrow<RuntimeException> {
                result.throwIf { true }
            }
        }
    }

    describe("throwUnless test") {
        it("returns value if ok") {
            Ok(500).throwUnless { false } shouldBe Ok(500)
        }

        it("returns error if predicate matches") {
            val result = Err(RuntimeException("throw unless"))
            result.throwUnless { true } shouldBe result
        }

        it("throws error if predicate does not match") {
            val result = Err(RuntimeException("throw unless"))
            shouldThrow<RuntimeException> {
                result.throwUnless { false }
            }
        }
    }

    describe("recover test") {
        it("returns value if ok") {
            Ok(30303).recover { 4004 } shouldBe Ok(30303)
        }

        it("returns transformed value if err") {
            Err(5033).recover { 5005 } shouldBe Ok(5005)
        }
    }

    describe("recoverCatching test") {
        it("returns value if ok") {
            Ok(30303).recoverCatching { 4004 } shouldBe Ok(30303)
        }

        it("returns transformed value if err") {
            Err(5033).recoverCatching { 5005 } shouldBe Ok(5005)
        }

        it("returns error if transformation throws") {
            val exception = IllegalArgumentException("throw me")
            Err(5033).recoverCatching { throw exception } shouldBe Err(exception)
        }
    }

    describe("recoverIf test") {
        it("returns value if ok") {
            fun predicate(int: Int): Boolean {
                return int == 4000
            }

            Ok(2020).recoverIf(::predicate) { 3000 } shouldBe Ok(2020)
        }

        it("returns transformed error as Ok if Err and predicate matches") {
            fun predicate(int: Int): Boolean {
                return int == 4000
            }

            Err(4000).recoverIf(::predicate) { 2000 } shouldBe Ok(2000)
        }

        it("does not return transformed error as Ok if Err and predicate does not match") {
            fun predicate(int: Int): Boolean {
                return int == 3000
            }

            Err(4000).recoverIf(::predicate) { 2000 } shouldBe Err(4000)
        }

        it("returns error if err and predicate does not match") {
            fun predicate(int: Int): Boolean {
                return int == 3000
            }

            Err(4000).recoverIf(::predicate) { 2000 } shouldBe Err(4000)
        }
    }

    describe("recoverUnless test") {
        it("returns value if ok") {
            fun predicate(int: Int): Boolean {
                return int == 4000
            }

            Ok(2020).recoverUnless(::predicate) { 2000 } shouldBe Ok(2020)
        }

        it("returns transformed error as Ok if Err and predicate does not match") {
            fun predicate(int: Int): Boolean {
                return int == 3000
            }

            Err(4000).recoverUnless(::predicate) { 2000 } shouldBe Ok(2000)
        }

        it("does not return transformed error as Ok if Err and predicate matches") {
            fun predicate(int: Int): Boolean {
                return int == 4000
            }

            Err(4000).recoverUnless(::predicate) { 2000 } shouldBe Err(4000)
        }

        it("returns error if err and predicate does not match") {
            fun predicate(int: Int): Boolean {
                return int == 4000
            }

            Err(4000).recoverUnless(::predicate) { 2000 } shouldBe Err(4000)
        }
    }

    describe("andThenRecover test") {
        it("returns value if ok") {
            Ok(200).andThenRecover { Ok(300) } shouldBe Ok(200)
        }

        it("returns transformed value if err") {
            Err(500).andThenRecover { Ok(600) } shouldBe Ok(600)
        }
    }

    describe("andThenRecoverIf test") {
        it("returns value if ok") {
            fun predicate(int: Int): Boolean {
                return int == 4000
            }

            Ok(3000).andThenRecoverIf(::predicate) { Ok(2000) } shouldBe Ok(3000)
        }

        it("returns transformed error as Ok if Err and predicate match") {
            fun predicate(int: Int): Boolean {
                return int == 4000
            }

            Err(4000).andThenRecoverIf(::predicate) { Ok(2000) } shouldBe Ok(2000)
        }

        it("returns transformed error as Err if Err and predicate match") {
            fun predicate(int: Int): Boolean {
                return int == 4000
            }

            Err(4000).andThenRecoverIf(::predicate) { Err(2000) } shouldBe Err(2000)
        }

        it("does not return transformation result if Err and predicate does not match") {
            fun predicate(int: Int): Boolean {
                return int == 3000
            }

            Err(4000).andThenRecoverIf(::predicate) { Ok(2000) } shouldBe Err(4000)
        }
    }

    describe("andThenRecoverUnless test") {
        it("returns value if ok") {
            fun predicate(int: Int): Boolean {
                return int == 4000
            }

            Ok(3000).andThenRecoverUnless(::predicate) { Ok(2000) } shouldBe Ok(3000)
        }

        it("returns transformed error as Ok if Err and predicate does not match") {
            fun predicate(int: Int): Boolean {
                return int == 3000
            }

            Err(4000).andThenRecoverUnless(::predicate) { Ok(2000) } shouldBe Ok(2000)
        }

        it("returns transformed error as Err if Err and predicate does not match") {
            fun predicate(int: Int): Boolean {
                return int == 3000
            }

            Err(4000).andThenRecoverUnless(::predicate) { Err(2000) } shouldBe Err(2000)
        }

        it("does not return transformation result if Err and predicate matches") {
            fun predicate(int: Int): Boolean {
                return int == 4000
            }

            Err(4000).andThenRecoverUnless(::predicate) { Ok(2000) } shouldBe Err(4000)
        }
    }

    describe("ResultIterator test") {
        describe("hasNext") {
            it("returns true if unyielded and Ok") {
                val iterator = Ok("hello").iterator()
                iterator.hasNext().shouldBeTrue()
            }

            it("returns false if Err") {
                val iterator = Err("hello").iterator()
                iterator.hasNext().shouldBeFalse()
            }

            it("returns false if yielded") {
                val iterator = Ok("hello").iterator()
                iterator.next()
                iterator.hasNext().shouldBeFalse()
            }
        }

        describe("next") {
            it("returns value if unyielded and Ok") {
                val iterator = Ok("hello").iterator()
                iterator.next() shouldBe "hello"
            }

            it("throws exception if unyielded and Err") {
                val iterator = Err("hello").iterator()
                shouldThrow<NoSuchElementException> {
                    iterator.next()
                }
            }

            it("throws exception if yielded and Ok") {
                val iterator = Ok("hello").iterator()
                iterator.next()
                shouldThrow<NoSuchElementException> {
                    iterator.next()
                }
            }
        }

        describe("remove") {
            it("makes hasNext return false") {
                val iterator = Ok("hello").mutableIterator()
                iterator.remove()
                iterator.hasNext().shouldBeFalse()
            }

            it("makes next throw exception") {
                val iterator = Ok("hello").mutableIterator()
                iterator.remove()

                shouldThrow<NoSuchElementException> {
                    iterator.next()
                }
            }
        }
    }

    describe("unwrap test") {
        it("returns value if ok") {
            Ok(333).unwrap() shouldBe 333
        }

        it("throws exception if err") {
            shouldThrow<UnwrapException> {
                Err(555).unwrap()
            }
        }
    }

    describe("expect test") {
        it("returns value if ok") {
            Ok(1994).expect { "the year should be" } shouldBe 1994
        }

        it("throws exception if err") {
            shouldThrow<UnwrapException> {
                Err(1994).expect { "the year should be" }
            }
        }
    }

    describe("unwrapError test") {
        it("throws exception if ok") {
            shouldThrow<UnwrapException> {
                Ok(333).unwrapError()
            }
        }

        it("returns error if err") {
            Err("boom").unwrapError() shouldBe "boom"
        }
    }

    describe("expectError test") {
        it("throws exception if ok") {
            shouldThrow<UnwrapException> {
                Ok(2020).expectError { "the year should be" }
            }
        }

        it("returns error if err") {
            Err(2020).expectError { "the year should be" } shouldBe 2020
        }
    }

    describe("zip test") {
        describe("zip") {
            it("returns transformed value if both ok") {
                val result = zip({ Ok(10) }, { Ok(20) }, Int::plus)
                result shouldBe Ok(30)
            }

            it("returns err if one of two err") {
                val result = zip({ Ok(10) }, { produce(10, "boom") }, Int::plus)
                result shouldBe Err("boom")
            }

            it("returns first err if both err") {
                val result = zip(
                    { produce(10, "foo") },
                    { produce(20, "bar") },
                    Int::plus,
                )

                result shouldBe Err("foo")
            }

            it("returns transformed value if three ok") {
                val result = zip(
                    { Ok("hello") },
                    { Ok(2) },
                    { Ok(false) },
                    ::ZipData3,
                )
                result shouldBe Ok(ZipData3("hello", 2, false))
            }

            it("returns err if one of three err") {
                val result = zip(
                    { Ok("foo") },
                    { Ok(1).and(Err("boom")) },
                    { Ok(false) },
                    ::ZipData3,
                )
                result shouldBe Err("boom")
            }

            it("returns first err if two of three err") {
                val result = zip(
                    { Ok("foo") },
                    { Ok(1).and(Err("bar")) },
                    { Ok(false).and(Err("baz")) },
                    ::ZipData3,
                )

                result shouldBe Err("bar")
            }

            it("returns first err if all three err") {
                val result = zip(
                    { Ok("foo").and(Err(1)) },
                    { Ok(1).and(Err(2)) },
                    { Ok(false).and(Err(3)) },
                    ::ZipData3,
                )
                result shouldBe Err(1)
            }

            it("returns transformed value if four ok") {
                val result = zip(
                    { Ok("hello") },
                    { Ok(2) },
                    { Ok(false) },
                    { Ok(1.5) },
                    ::ZipData4,
                )
                result shouldBe Ok(ZipData4("hello", 2, false, 1.5))
            }

            it("returns err if some of four err") {
                val result = zip(
                    { Ok("hello") },
                    { Ok(2).and(Err(1)) },
                    { Ok(false) },
                    { Ok(1.5).and(Err(2)) },
                    ::ZipData4,
                )
                result shouldBe Err(1)
            }

            it("returns transformed value if five ok") {
                val result = zip(
                    { Ok("hello") },
                    { Ok(2) },
                    { Ok(false) },
                    { Ok(1.5) },
                    { Ok('a') },
                    ::ZipData5,
                )

                result shouldBe Ok(ZipData5("hello", 2, false, 1.5, 'a'))
            }

            it("returns first err if some of five err") {
                val result = zip(
                    { Ok("hello").and(Err(1)) },
                    { Ok(2) },
                    { Ok(false) },
                    { Ok(1.5) },
                    { Ok('a').and(Err(2)) },
                    ::ZipData5,
                )
                result shouldBe Err(1)
            }
        }

        describe("zipOrAccumulate") {
            it("returns transformed value if all of two ok") {
                val result = zipOrAccumulate(
                    { Ok(10) },
                    { Ok(20) },
                    Int::plus,
                )

                result shouldBe Ok(30)
            }

            it("returns one err if one of two err") {
                val result = zipOrAccumulate(
                    { Ok(10) },
                    { produce(20, "hello") },
                    Int::plus,
                )

                result shouldBe Err(listOf("hello"))
            }

            it("returns errs of all of two err") {
                val result = zipOrAccumulate(
                    { produce(10, "foo") },
                    { produce(20, "bar") },
                    Int::plus,
                )

                val expectedErrors = listOf("foo", "bar")

                result shouldBe Err(expectedErrors)
            }

            it("returns transformed value of all ok") {
                val result = zipOrAccumulate(
                    { Ok(10) },
                    { Ok(20) },
                    { Ok(30) },
                    { Ok(40) },
                    { Ok(50) },
                ) { a, b, c, d, e ->
                    a + b + c + d + e
                }

                result shouldBe Ok(150)
            }

            it("returns all errs if all err") {
                val result = zipOrAccumulate(
                    { produce(10, "error one") },
                    { produce(20, "error two") },
                    { produce(30, "error three") },
                    { produce(40, "error four") },
                    { produce(50, "error five") },
                ) { a, b, c, d, e ->
                    a + b + c + d + e
                }

                val errors = listOf(
                    "error one",
                    "error two",
                    "error three",
                    "error four",
                    "error five",
                )

                result shouldBe Err(errors)
            }

            it("returns one err if one of err") {
                val result = zipOrAccumulate(
                    { Ok(10) },
                    { produce(20, "only error") },
                    { Ok(30) },
                    { Ok(40) },
                    { Ok(50) },
                ) { a, b, c, d, e ->
                    a + b + c + d + e
                }

                result shouldBe Err(listOf("only error"))
            }
        }
    }
}) {
    private sealed interface Direction
    private data object Left : Direction
    private data object Right : Direction

    private sealed interface IterableError {
        data object IterableError1 : IterableError
        data object IterableError2 : IterableError
    }

    private sealed interface MapErr {
        val reason: String

        data object HelloError : MapErr {
            override val reason = "hello"
        }

        data object WorldError : MapErr {
            override val reason = "world"
        }

        data class CustomError(override val reason: String) : MapErr
    }

    private object CounterError
    private class Counter(var count: Int)

    private data class ZipData3(val a: String, val b: Int, val c: Boolean)
    private data class ZipData4(val a: String, val b: Int, val c: Boolean, val d: Double)
    private data class ZipData5(val a: String, val b: Int, val c: Boolean, val d: Double, val e: Char)
}

private fun produce(number: Int, error: String): Result<Int, String> {
    return Ok(number).and(Err(error))
}
