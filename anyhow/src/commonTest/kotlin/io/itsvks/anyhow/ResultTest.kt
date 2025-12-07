package io.itsvks.anyhow

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class ResultTest : FunSpec({
    test("Ok should return the correct value") {
        val result = Ok(42)
        result.shouldBeInstanceOf<Result<Int, Nothing>>()
        result.value shouldBe 42
    }
})
