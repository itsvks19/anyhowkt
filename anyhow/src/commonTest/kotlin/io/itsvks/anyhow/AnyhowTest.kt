package io.itsvks.anyhow

import arrow.core.None
import arrow.core.Option
import arrow.core.Some
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf

class AnyhowTest : FunSpec({
    context("AnyhowResult creation") {
        test("anyhow { } returns Ok for successful computation") {
            val result = anyhow { 42 }
            result.isOk.shouldBeTrue()
            result.getOrNull() shouldBe 42
        }

        test("anyhow.ok creates Ok result") {
            val result = anyhow.ok(100)
            result.getOrNull() shouldBe 100
        }

        test("anyhow.err with string creates Err result") {
            val result = anyhow.err("error message")
            result.isErr.shouldBeTrue()
            result.errorOrNull()?.message shouldContain "error message"
        }

        test("anyhow.err with Throwable creates Err result") {
            val exception = RuntimeException("test exception")
            val result = anyhow.err(exception)
            result.isErr.shouldBeTrue()
            result.errorOrNull().shouldBeInstanceOf<AnyhowError>()
            result.errorOrNull()?.cause shouldBe exception
        }

        test("anyhow.err with AnyhowError creates Err result") {
            val error = AnyhowError("custom error")
            val result = anyhow.err(error)
            result.isErr.shouldBeTrue()
            result.errorOrNull().shouldBeInstanceOf<AnyhowError>()
        }
    }

    context("AnyhowError creation") {
        test("AnyhowError from string message") {
            val error = AnyhowError("test message")
            error.cause.shouldBeInstanceOf<AnyhowException>()
            error.cause.message shouldBe "test message"
        }

        test("AnyhowError from Throwable") {
            val cause = IllegalStateException("illegal state")
            val error = AnyhowError(cause)
            error.cause shouldBe cause
        }

        test("AnyhowError from null creates error") {
            val error = AnyhowError(null as String?)
            error.cause.shouldBeInstanceOf<AnyhowException>()
            error.cause.message shouldBe "null"
        }

        test("AnyhowError from arbitrary object uses toString") {
            val error = AnyhowError(12345)
            error.toString() shouldContain "12345"
        }

        test("AnyhowError identity") {
            val original = AnyhowError("original")
            val copied = AnyhowError(original)
            copied shouldBe original
        }
    }

    context("AnyhowError context stacking") {
        test("context adds message to error stack") {
            val error = AnyhowError("base error")
            val withContext = error.context("additional context")
            withContext.toString() shouldContain "additional context"
            withContext.toString() shouldContain "base error"
        }

        test("multiple context calls stack messages") {
            val error = AnyhowError("base")
                .context("context 1")
                .context("context 2")
            val errorString = error.toString()
            errorString shouldContain "context 1"
            errorString shouldContain "context 2"
            errorString shouldContain "base"
        }
    }

    context("AnyhowScope.bind operations") {
        test("bind on Ok returns value") {
            val result = anyhow {
                val value = Ok(42).bind()
                value * 2
            }
            result.getOrNull() shouldBe 84
        }

        test("bind on Err short-circuits") {
            val result = anyhow {
                val value = Err(AnyhowError("error")).bind<Int>()
                value * 2 // This should not execute
            }
            result.isErr.shouldBeTrue()
            result.errorOrNull().shouldBeInstanceOf<AnyhowError>()
        }

        test("bind on nullable returns value when non-null") {
            val result = anyhow {
                val value: Int? = "42".toIntOrNull()
                value.bind()
            }
            result.getOrNull() shouldBe 42
        }

        test("bind on nullable raises error when null") {
            val result = anyhow {
                val value: Int? = "hello".toIntOrNull()
                value.bind()
            }
            result.isErr.shouldBeTrue()
            result.errorOrNull().shouldBeInstanceOf<AnyhowError>()
            result.errorOrNull()?.toString() shouldContain "null value"
        }

        test("bind with custom error on nullable") {
            val result = anyhow {
                val value: Int? = null
                value.bind { "custom null error" }
            }
            result.isErr.shouldBeTrue()
            result.errorOrNull().shouldBeInstanceOf<AnyhowError>()
            result.errorOrNull()?.toString() shouldContain "custom null error"
        }

        test("bind on Option.Some returns value") {
            val result = anyhow {
                val option: Option<Int> = Some(42)
                option.bind { "no value" }
            }
            result.getOrNull() shouldBe 42
        }

        test("bind on Option.None raises error") {
            val result = anyhow {
                val option: Option<Nothing> = None
                option.bind { "option is empty" }
            }
            result.isErr.shouldBeTrue()
            result.errorOrNull().shouldBeInstanceOf<AnyhowError>()
            result.errorOrNull()?.toString() shouldContain "option is empty"
        }

        test("bindAll on list of results") {
            val result = anyhow {
                val results = listOf(Ok(1), Ok(2), Ok(3))
                results.bindAll()
            }
            result.getOrNull() shouldBe listOf(1, 2, 3)
        }

        test("bindAll on list with error short-circuits") {
            val result = anyhow {
                val results = listOf(Ok(1), Err(AnyhowError("error")), Ok(3))
                results.bindAll()
            }
            result.isErr.shouldBeTrue()
            result.errorOrNull().shouldBeInstanceOf<AnyhowError>()
        }

        test("bindAll on map of results") {
            val result = anyhow {
                val results = mapOf(
                    "a" to Ok(1),
                    "b" to Ok(2),
                    "c" to Ok(3)
                )
                results.bindAll()
            }
            result.getOrNull() shouldBe mapOf("a" to 1, "b" to 2, "c" to 3)
        }
    }

    context("AnyhowScope.raise operations") {
        test("raise with string short-circuits") {
            val result = anyhow {
                raise("error occurred")
                @Suppress("KotlinUnreachableCode")
                42 // Should not execute
            }
            result.isErr.shouldBeTrue()
            result.errorOrNull().shouldBeInstanceOf<AnyhowError>()
            result.errorOrNull()?.toString() shouldContain "error occurred"
        }

        test("raise with Throwable") {
            val exception = IllegalArgumentException("bad argument")
            val result = anyhow {
                raise(exception)
                42
            }
            result.isErr.shouldBeTrue()
            result.errorOrNull().shouldBeInstanceOf<AnyhowError>()
            result.errorOrNull()?.cause shouldBe exception
        }

        test("bail with string") {
            val result = anyhow {
                bail("bailing out")
                42
            }
            result.isErr.shouldBeTrue()
            result.errorOrNull().shouldBeInstanceOf<AnyhowError>()
            result.errorOrNull()?.toString() shouldContain "bailing out"
        }

        test("bail with Throwable") {
            val exception = RuntimeException("runtime error")
            val result = anyhow {
                bail(exception)
                42
            }
            result.isErr.shouldBeTrue()
            result.errorOrNull().shouldBeInstanceOf<AnyhowError>()
            result.errorOrNull()?.cause shouldBe exception
        }
    }

    context("AnyhowScope.ensure operations") {
        test("ensure with true condition continues") {
            val result = anyhow {
                ensure(true) { "should not fail" }
                42
            }
            result.getOrNull() shouldBe 42
        }

        test("ensure with false condition raises error") {
            val result = anyhow {
                ensure(false) { "condition not met" }
                42
            }
            result.isErr.shouldBeTrue()
            result.errorOrNull().shouldBeInstanceOf<AnyhowError>()
            result.errorOrNull()?.toString() shouldContain "condition not met"
        }

        test("ensureNotNull with non-null value continues") {
            val result = anyhow {
                val value: Int? = "42".toIntOrNull()
                val checked = ensureNotNull(value) { "should not be null" }
                checked * 2
            }
            result.getOrNull() shouldBe 84
        }

        test("ensureNotNull with null value raises error") {
            val result = anyhow {
                val value: Int? = null
                ensureNotNull(value) { "value is null" }
            }
            result.isErr.shouldBeTrue()
            result.errorOrNull().shouldBeInstanceOf<AnyhowError>()
            result.errorOrNull()?.toString() shouldContain "value is null"
        }
    }

    context("AnyhowScope.withContext") {
        test("withContext adds context on success") {
            val result = anyhow {
                withContext("outer context") {
                    42
                }
            }
            result.getOrNull() shouldBe 42
        }

        test("withContext adds context on failure") {
            val result = anyhow {
                withContext("operation context") {
                    raise("inner error")
                }
            }
            result.isErr.shouldBeTrue()
            result.errorOrNull().shouldBeInstanceOf<AnyhowError>()
            val errorString = result.errorOrNull()?.toString() ?: ""
            errorString shouldContain "operation context"
            errorString shouldContain "inner error"
        }

        test("nested withContext stacks contexts") {
            val result = anyhow {
                withContext("outer") {
                    withContext("middle") {
                        withContext("inner") {
                            raise("base error")
                        }
                    }
                }
            }
            result.isErr.shouldBeTrue()
            result.errorOrNull().shouldBeInstanceOf<AnyhowError>()
            val errorString = result.errorOrNull()?.toString() ?: ""
            errorString shouldContain "outer"
            errorString shouldContain "middle"
            errorString shouldContain "inner"
            errorString shouldContain "base error"
        }
    }

    context("AnyhowScope.catching") {
        test("catching handles exceptions") {
            val result = anyhow {
                catching {
                    throw IllegalStateException("exception occurred")
                }
            }
            result.isErr.shouldBeTrue()
            result.errorOrNull().shouldBeInstanceOf<AnyhowError>()
            result.errorOrNull()?.cause.shouldBeInstanceOf<IllegalStateException>()
        }

        test("catching allows normal execution") {
            val result = anyhow {
                catching {
                    42
                }
            }
            result.getOrNull() shouldBe 42
        }
    }

    context("AnyhowScope nested anyhow blocks") {
        test("nested anyhow returns result") {
            val result = anyhow {
                val nested = anyhow {
                    42
                }
                nested.bind() * 2
            }
            result.getOrNull() shouldBe 84
        }

        test("nested anyhow error can be handled") {
            val result = anyhow {
                val nested = anyhow {
                    raise("nested error")
                    42
                }
                nested.fold(::identity) { 0 }
            }
            result.getOrNull() shouldBe 0
        }
    }

    context("result extension methods") {
        test("context adds message to error") {
            val result: AnyhowResult<Int> = Err(AnyhowError("base error"))
            val withContext = result.context("additional info")
            result.isErr.shouldBeTrue()
            result.errorOrNull().shouldBeInstanceOf<AnyhowError>()
            val errorString = withContext.errorOrNull()?.toString() ?: ""
            errorString shouldContain "additional info"
            errorString shouldContain "base error"
        }

        test("context on Ok returns Ok unchanged") {
            val result: AnyhowResult<Int> = Ok(42)
            val withContext = result.context("should not affect")
            withContext.getOrNull() shouldBe 42
        }
    }

    context("Kotlin Result conversion") {
        test("kotlin.Result.anyhow converts success") {
            val kotlinResult = kotlin.Result.success(42)
            val anyhowResult = kotlinResult.anyhow()
            anyhowResult.getOrNull() shouldBe 42
        }

        test("kotlin.Result.anyhow converts failure") {
            val exception = RuntimeException("test error")
            val kotlinResult = kotlin.Result.failure<Int>(exception)
            val anyhowResult = kotlinResult.anyhow()
            anyhowResult.isErr.shouldBeTrue()
            anyhowResult.errorOrNull().shouldBeInstanceOf<AnyhowError>()
            anyhowResult.errorOrNull()?.cause shouldBe exception
        }

        test("toKotlinResult converts Ok") {
            val anyhowResult: AnyhowResult<Int> = Ok(42)
            val kotlinResult = anyhowResult.toKotlinResult()
            kotlinResult.isSuccess shouldBe true
            kotlinResult.getOrNull() shouldBe 42
        }

        test("toKotlinResult converts Err") {
            val error = AnyhowError("test error")
            val anyhowResult: AnyhowResult<Int> = Err(error)
            val kotlinResult = anyhowResult.toKotlinResult()
            kotlinResult.isFailure shouldBe true
        }
    }

    context("complex scenarios") {
        test("combining multiple operations") {
            val result = anyhow {
                val a = Ok(10).bind()
                val b = Some(5).bind { "no b" }
                val c: Int? = 2
                val d = c.bind()
                ensure(a > 0) { "a must be positive" }
                a + b + d
            }
            result.getOrNull() shouldBe 17
        }

        test("early short-circuit stops execution") {
            var executed = false
            val result = anyhow {
                raise("early error")

                @Suppress("KotlinUnreachableCode")
                executed = true
                42
            }
            result.isErr.shouldBeTrue()
            result.errorOrNull().shouldBeInstanceOf<AnyhowError>()
            executed shouldBe false
        }

        test("error propagation through multiple bind calls") {
            val result = anyhow {
                val first = anyhow { Ok(10).bind() }
                val second = anyhow { raise("error in second") }
                val third = anyhow { Ok(30).bind() }

                first.bind() + second.bind<Int>() + third.bind()
            }
            result.isErr.shouldBeTrue()
            result.errorOrNull().shouldBeInstanceOf<AnyhowError>()
            result.errorOrNull()?.toString() shouldContain "error in second"
        }
    }
})
