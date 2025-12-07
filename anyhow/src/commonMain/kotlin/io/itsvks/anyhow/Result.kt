@file:Suppress("FunctionName")

package io.itsvks.anyhow

import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.jvm.JvmInline

/**
 * Marks declarations that are part of the internal or unsafe API of the [Result] class.
 *
 * Properties or functions annotated with this are considered unsafe because they may access
 * the underlying data of a [Result] without verifying its state (Success vs Failure).
 * Direct usage might lead to [ClassCastException] or unhandled runtime errors if the [Result]
 * is not in the expected state.
 *
 * This annotation requires explicit opt-in.
 */
@RequiresOptIn("This API is unsafe because it bypasses success/failure checks. Ensure the Result state is verified before accessing.")
@MustBeDocumented
@Retention(AnnotationRetention.BINARY)
public annotation class UnsafeResultApi

/**
 * Calls the specified function [block] and returns its encapsulated result if invocation was
 * successful, catching any [Throwable] exception that was thrown from the [block] function
 * execution and encapsulating it as a failure.
 */
public inline fun <V> runCatching(block: () -> V): Result<V, Throwable> {
    contract {
        callsInPlace(block, InvocationKind.AT_MOST_ONCE)
    }

    return try {
        Ok(block())
    } catch (e: Throwable) {
        Err(e)
    }
}

/**
 * Calls the specified function [block] with [this] value as its receiver and returns its
 * encapsulated result if invocation was successful, catching any [Throwable] exception that was
 * thrown from the [block] function execution and encapsulating it as a failure.
 */
public inline infix fun <T, V> T.runCatching(block: T.() -> V): Result<V, Throwable> {
    contract {
        callsInPlace(block, InvocationKind.AT_MOST_ONCE)
    }

    return try {
        Ok(block())
    } catch (e: Throwable) {
        Err(e)
    }
}

/**
 * Converts a nullable of type [V] to a [Result]. Returns [Ok] if the value is
 * non-null, otherwise the supplied [error].
 */
public inline infix fun <V, E> V?.toResultOr(error: () -> E): Result<V, E> {
    contract {
        callsInPlace(error, InvocationKind.AT_MOST_ONCE)
    }

    return when (this) {
        null -> Err(error())
        else -> Ok(this)
    }
}

/**
 * A discriminated union that represents either success ([Ok]) or failure ([Err]).
 *
 * This `Result` type is conceptually similar to Rust's [Result](https://doc.rust-lang.org/std/result/enum.Result.html)
 * or Kotlin's standard [kotlin.Result], but allows for typed errors ([E]) instead of strictly [Throwable].
 *
 * @param Value The type of the value returned in a success state.
 * @param Error The type of the error returned in a failure state.
 *
 * @property value The success value. accessing this property is unsafe; use [isOk] to check state first.
 * @property error The error value. accessing this property is unsafe; use [isErr] to check state first.
 * @property isOk Returns `true` if the result represents a success.
 * @property isErr Returns `true` if the result represents a failure.
 */
@JvmInline
public value class Result<out Value, out Error> internal constructor(
    private val data: Any?
) {
    @Suppress("UNCHECKED_CAST")
    @UnsafeResultApi
    public val value get() = data as Value

    @Suppress("UNCHECKED_CAST")
    @UnsafeResultApi
    public val error get() = (data as Failure<Error>).error

    public val isOk get() = data !is Failure<*>
    public val isErr get() = data is Failure<*>

    public operator fun component1() = when {
        isOk -> value
        else -> null
    }

    public operator fun component2() = when {
        isErr -> error
        else -> null
    }

    override fun toString() = when {
        isOk -> "Ok($value)"
        else -> "Err($error)"
    }
}

public interface ResultBindingScope<E> {
    public fun <V> Result<V, E>.bind(): V
}

/**
 * Creates a [Result] representing a successful operation containing the given [value].
 *
 * @param value The value to wrap.
 * @return A [Result] containing the [value] as a success.
 */
public fun <V> Ok(value: V) = Result<V, Nothing>(value)

/**
 * Creates a [Result] representing a failed operation containing the given [error].
 *
 * @param error The error to wrap.
 * @return A [Result] containing the [error] as a failure.
 */
public fun <E> Err(error: E) = Result<Nothing, E>(createFailure(error))

/**
 * Unsafely casts this [Result<V, E>][Result] to [Result<U, Nothing>][Result], to be used inside
 * an explicit [isOk][Result.isOk] or [isErr][Result.isErr] guard.
 */
@Suppress("UNCHECKED_CAST", "NOTHING_TO_INLINE")
@UnsafeResultApi
public inline fun <V, E, U> Result<V, E>.asOk(): Result<U, Nothing> {
    return this as Result<U, Nothing>
}

/**
 * Unsafely casts this [Result<V, E>][Result] to [Result<Nothing, F>][Result], to be used inside
 * an explicit [isOk][Result.isOk] or [isErr][Result.isErr] guard.
 */
@Suppress("UNCHECKED_CAST", "NOTHING_TO_INLINE")
@UnsafeResultApi
public inline fun <V, E, F> Result<V, E>.asErr(): Result<Nothing, F> {
    return this as Result<Nothing, F>
}

private fun <E> createFailure(error: E) = Failure(error)
private data class Failure<out E>(val error: E)

/**
 * Maps this [Result<V, E>][Result] to [Result<U, E>][Result] by either applying the [transform]
 * function to the [value][Result.value] if this result [is ok][Result.isOk], or returning [this].
 *
 * - Rust: [Result.map](https://doc.rust-lang.org/std/result/enum.Result.html#method.map)
 */
public inline fun <V, E, T> Result<V, E>.map(transform: (V) -> T): Result<T, E> {
    contract {
        callsInPlace(transform, InvocationKind.AT_MOST_ONCE)
    }

    return when {
        isOk -> Ok(transform(value))
        else -> asErr()
    }
}

/**
 * Maps this [Result<V, Throwable>][Result] to [Result<U, Throwable>][Result] by either applying
 * the [transform] function to the [value][Result.value] if this result [is ok][Result.isOk], or
 * returning [this].
 *
 * This function catches any [Throwable] exception thrown by [transform] function and encapsulates
 * it as an [Err].
 *
 * - Rust: [Result.map](https://doc.rust-lang.org/std/result/enum.Result.html#method.map)
 */
public inline fun <V, T> Result<V, Throwable>.mapCatching(transform: (V) -> T): Result<T, Throwable> {
    contract {
        callsInPlace(transform, InvocationKind.AT_MOST_ONCE)
    }

    return when {
        isOk -> runCatching { transform(value) }
        else -> asErr()
    }
}

/**
 * Transposes this [Result<V?, E>][Result] to [Result<V, E>][Result].
 *
 * Returns null if this [Result] is [Ok] and the [value][Result.value] is `null`, otherwise this [Result].
 *
 * - Rust: [Result.transpose](https://doc.rust-lang.org/std/result/enum.Result.html#method.transpose)
 */
@Suppress("NOTHING_TO_INLINE")
public inline fun <V, E> Result<V?, E>.transpose(): Result<V, E>? {
    return when {
        isOk && value == null -> null
        isOk && value != null -> this.asOk()
        else -> this.asErr()
    }
}

/**
 * Maps this [Result<Result<V, E>, E>][Result] to [Result<V, E>][Result].
 *
 * - Rust: [Result.flatten](https://doc.rust-lang.org/std/result/enum.Result.html#method.flatten)
 */
public fun <V, E> Result<Result<V, E>, E>.flatten(): Result<V, E> {
    return when {
        isOk -> value
        else -> this.asErr()
    }
}

/**
 * Returns [result] if this result [is ok][Result.isOk], otherwise [this].
 *
 * - Rust: [Result.and](https://doc.rust-lang.org/std/result/enum.Result.html#method.and)
 */
public fun <V, E, U> Result<V, E>.and(result: Result<U, E>): Result<U, E> {
    return when {
        isOk -> result
        else -> this.asErr()
    }
}

/**
 * Maps this [Result<V, E>][Result] to [Result<U, E>][Result] by either applying the [transform]
 * function if this result [is ok][Result.isOk], or returning [this].
 *
 * - Rust: [Result.and_then](https://doc.rust-lang.org/std/result/enum.Result.html#method.and_then)
 */
public inline fun <V, E, U> Result<V, E>.andThen(transform: (V) -> Result<U, E>): Result<U, E> {
    contract {
        callsInPlace(transform, InvocationKind.AT_MOST_ONCE)
    }

    return when {
        isOk -> transform(value)
        else -> this.asErr()
    }
}

/**
 * Maps this [Result<V, E>][Result] to [Result<U, E>][Result] by either applying the [transform]
 * function if this result [is ok][Result.isOk], or returning [this].
 *
 * This is functionally equivalent to [andThen].
 */
public inline infix fun <V, E, U> Result<V, E>.flatMap(transform: (V) -> Result<U, E>): Result<U, E> {
    contract {
        callsInPlace(transform, InvocationKind.AT_MOST_ONCE)
    }

    return andThen(transform)
}

/**
 * Maps this [Result<V, E>][Result] to [U] by applying either the [ok] function if this
 * result [is ok][Result.isOk], or the [err] function if this result
 * [is an error][Result.isErr].
 *
 * Unlike [mapEither], [ok] and [err] must both return [U].
 */
public inline fun <V, E, U> Result<V, E>.mapBoth(
    ok: (V) -> U,
    err: (E) -> U,
): U {
    contract {
        callsInPlace(ok, InvocationKind.AT_MOST_ONCE)
        callsInPlace(err, InvocationKind.AT_MOST_ONCE)
    }

    return when {
        isOk -> ok(value)
        else -> err(error)
    }
}

/**
 * Maps this [Result<V, E>][Result] to [U] by applying either the [ok] function if this
 * result [is ok][Result.isOk], or the [err] function if this result
 * [is an error][Result.isErr].
 *
 * Unlike [mapEither], [ok] and [err] must both return [U].
 *
 * This is functionally equivalent to [mapBoth].
 */
public inline fun <V, E, U> Result<V, E>.fold(
    ok: (V) -> U,
    err: (E) -> U,
): U {
    contract {
        callsInPlace(ok, InvocationKind.AT_MOST_ONCE)
        callsInPlace(err, InvocationKind.AT_MOST_ONCE)
    }

    return mapBoth(ok, err)
}

/**
 * Maps this [Result<V, E>][Result] to [Result<U, E>][Result] by applying either the [ok]
 * function if this result [is ok][Result.isOk], or the [err] function if this result
 * [is an error][Result.isErr].
 *
 * Unlike [mapEither], [ok] and [err] must both return [U].
 */
public inline fun <V, E, U> Result<V, E>.flatMapBoth(
    ok: (V) -> Result<U, E>,
    err: (E) -> Result<U, E>,
): Result<U, E> {
    contract {
        callsInPlace(ok, InvocationKind.AT_MOST_ONCE)
        callsInPlace(err, InvocationKind.AT_MOST_ONCE)
    }

    return when {
        isOk -> ok(value)
        else -> err(error)
    }
}

/**
 * Maps this [Result<V, E>][Result] to [Result<U, F>][Result] by applying either the [ok]
 * function if this result [is ok][Result.isOk], or the [err] function if this result
 * [is an error][Result.isErr].
 *
 * Unlike [mapBoth], [ok] and [err] may either return [U] or [F] respectively.
 */
public inline fun <V, E, U, F> Result<V, E>.mapEither(
    ok: (V) -> U,
    err: (E) -> F,
): Result<U, F> {
    contract {
        callsInPlace(ok, InvocationKind.AT_MOST_ONCE)
        callsInPlace(err, InvocationKind.AT_MOST_ONCE)
    }

    return when {
        isOk -> Ok(ok(value))
        else -> Err(err(error))
    }
}

/**
 * Maps this [Result<V, E>][Result] to [Result<U, F>][Result] by applying either the [ok]
 * function if this result [is ok][Result.isOk], or the [err] function if this result
 * [is an error][Result.isErr].
 *
 * Unlike [mapBoth], [ok] and [err] may either return [U] or [F] respectively.
 */
public inline fun <V, E, U, F> Result<V, E>.flatMapEither(
    ok: (V) -> Result<U, F>,
    err: (E) -> Result<U, F>,
): Result<U, F> {
    contract {
        callsInPlace(ok, InvocationKind.AT_MOST_ONCE)
        callsInPlace(err, InvocationKind.AT_MOST_ONCE)
    }

    return when {
        isOk -> ok(value)
        else -> err(error)
    }
}

/**
 * Maps this [Result<V, E>][Result] to [Result<V, F>][Result] by either applying the [transform]
 * function to the [error][Result.error] if this result [is an error][Result.isErr], or returning
 * [this].
 *
 * - Rust: [Result.map_err](https://doc.rust-lang.org/std/result/enum.Result.html#method.map_err)
 */
public inline fun <V, E, F> Result<V, E>.mapError(transform: (E) -> F): Result<V, F> {
    contract {
        callsInPlace(transform, InvocationKind.AT_MOST_ONCE)
    }

    return when {
        isErr -> Err(transform(error))
        else -> this.asOk()
    }
}

/**
 * Maps this [Result<V, E>][Result] to [U] by either applying the [transform] function to the
 * [value][Result.value] if this result [is ok][Result.isOk], or returning the [default] if this
 * result [is an error][Result.isErr].
 *
 * - Rust: [Result.map_or](https://doc.rust-lang.org/std/result/enum.Result.html#method.map_or)
 */
public inline fun <V, E, U> Result<V, E>.mapOr(
    default: U,
    transform: (V) -> U,
): U {
    contract {
        callsInPlace(transform, InvocationKind.AT_MOST_ONCE)
    }

    return when {
        isOk -> transform(value)
        else -> default
    }
}

/**
 * Maps this [Result<V, E>][Result] to [U] by applying either the [transform] function if this
 * result [is ok][Result.isOk], or the [default] function if this result
 * [is an error][Result.isErr]. Both of these functions must return the same type ([U]).
 *
 * - Rust: [Result.map_or_else](https://doc.rust-lang.org/std/result/enum.Result.html#method.map_or_else)
 */
public inline fun <V, E, U> Result<V, E>.mapOrElse(
    default: (E) -> U,
    transform: (V) -> U,
): U {
    contract {
        callsInPlace(default, InvocationKind.AT_MOST_ONCE)
        callsInPlace(transform, InvocationKind.AT_MOST_ONCE)
    }

    return when {
        isOk -> transform(value)
        else -> default(error)
    }
}

/**
 * Returns a [Result<List<U>, E>][Result] containing the results of applying the given [transform]
 * function to each element in the original collection, returning early with the first [Err] if a
 * transformation fails.
 */
public inline fun <V, E, U> Result<Iterable<V>, E>.mapAll(transform: (V) -> Result<U, E>): Result<List<U>, E> {
    contract { callsInPlace(transform, InvocationKind.UNKNOWN) }

    return map { iterable ->
        iterable.map { element ->
            val transformed = transform(element)

            when {
                transformed.isOk -> transformed.value
                else -> return transformed.asErr()
            }
        }
    }
}

/**
 * Returns the [transformation][transform] of the [value][Result.value] if this result
 * [is ok][Result.isOk] and satisfies the given [predicate], otherwise [this].
 *
 * @see [takeIf]
 */
public inline fun <V, E> Result<V, E>.toErrorIf(
    predicate: (V) -> Boolean,
    transform: (V) -> E,
): Result<V, E> {
    contract {
        callsInPlace(predicate, InvocationKind.AT_MOST_ONCE)
        callsInPlace(transform, InvocationKind.AT_MOST_ONCE)
    }

    return when {
        isOk && predicate(value) -> Err(transform(value))
        else -> this
    }
}

/**
 * Returns the supplied [error] if this result [is ok][Result.isOk] and the [value][Result.value]
 * is `null`, otherwise [this].
 *
 * @see [toErrorIf]
 */
public inline fun <V, E> Result<V?, E>.toErrorIfNull(error: () -> E): Result<V, E> {
    contract {
        callsInPlace(error, InvocationKind.AT_MOST_ONCE)
    }

    return when {
        isOk && value == null -> Err(error())
        isOk && value != null -> this.asOk()
        else -> this.asErr()
    }
}

/**
 * Returns the [transformation][transform] of the [value][Result.value] if this result
 * [is ok][Result.isOk] and _does not_ satisfy the given [predicate], otherwise [this].
 *
 * @see [takeUnless]
 */
public inline fun <V, E> Result<V, E>.toErrorUnless(
    predicate: (V) -> Boolean,
    transform: (V) -> E,
): Result<V, E> {
    contract {
        callsInPlace(predicate, InvocationKind.AT_MOST_ONCE)
        callsInPlace(transform, InvocationKind.AT_MOST_ONCE)
    }

    return when {
        isOk && !predicate(value) -> Err(transform(value))
        else -> this
    }
}

/**
 * Returns the supplied [error] unless this result [is ok][Result.isOk] and the
 * [value][Result.value] is `null`, otherwise [this].
 *
 * @see [toErrorUnless]
 */
public inline fun <V, E> Result<V, E>.toErrorUnlessNull(error: () -> E): Result<V, E> {
    contract {
        callsInPlace(error, InvocationKind.AT_MOST_ONCE)
    }

    return when {
        isOk && value == null -> this
        else -> Err(error())
    }
}

/**
 * Returns the [value][Result.value] if this result [is ok][Result.isOk], otherwise `null`.
 *
 * - Rust: [Result.ok](https://doc.rust-lang.org/std/result/enum.Result.html#method.ok)
 */
public fun <V, E> Result<V, E>.get(): V? {
    return when {
        isOk -> value
        else -> null
    }
}

/**
 * Alias for [get]
 */
@Suppress("NOTHING_TO_INLINE")
public inline fun <V, E> Result<V, E>.ok() = get()

/**
 * Returns the [error][Result.error] if this result [is an error][Result.isErr], otherwise `null`.
 *
 * - Rust: [Result.err](https://doc.rust-lang.org/std/result/enum.Result.html#method.err)
 */
public fun <V, E> Result<V, E>.getError(): E? {
    return when {
        isErr -> error
        else -> null
    }
}

/**
 * Alias for [getError]
 */
@Suppress("NOTHING_TO_INLINE")
public inline fun <V, E> Result<V, E>.err() = getError()

/**
 * Returns the [value][Result.value] if this result [is ok][Result.isOk], otherwise [default].
 *
 * - Rust: [Result.unwrap_or](https://doc.rust-lang.org/std/result/enum.Result.html#method.unwrap_or)
 *
 * @param default The value to return if [Err].
 * @return The [value][Result.value] if [Ok], otherwise [default].
 */
public infix fun <V, E> Result<V, E>.getOr(default: V): V {
    return when {
        isOk -> value
        else -> default
    }
}

/**
 * Returns the [error][Result.error] if this result [is an error][Result.isErr], otherwise
 * [default].
 *
 * @param default The error to return if [Ok].
 * @return The [error][Result.error] if [Err], otherwise [default].
 */
public infix fun <V, E> Result<V, E>.getErrorOr(default: E): E {
    return when {
        isOk -> default
        else -> error
    }
}

/**
 * Returns the [value][Result.value] if this result [is ok][Result.isOk], otherwise the
 * [transformation][transform] of the [error][Result.error].
 *
 * - Rust: [Result.unwrap_or_else](https://doc.rust-lang.org/src/core/result.rs.html#735-740)
 */
public inline infix fun <V, E> Result<V, E>.getOrElse(transform: (E) -> V): V {
    contract {
        callsInPlace(transform, InvocationKind.AT_MOST_ONCE)
    }

    return when {
        isOk -> value
        else -> transform(error)
    }
}

/**
 * Returns the [error][Result.error] if this result [is an error][Result.isErr], otherwise the
 * [transformation][transform] of the [value][Result.value].
 */
public inline infix fun <V, E> Result<V, E>.getErrorOrElse(transform: (V) -> E): E {
    contract {
        callsInPlace(transform, InvocationKind.AT_MOST_ONCE)
    }

    return when {
        isErr -> error
        else -> transform(value)
    }
}

/**
 * Returns the [value][Result.value] if this result [is ok][Result.isOk], otherwise throws the
 * [error][Result.error].
 *
 * This is functionally equivalent to [`getOrElse { throw it }`][getOrElse].
 */
public fun <V, E> Result<V, E>.getOrThrow(): V where E : Throwable {
    return when {
        isOk -> value
        else -> throw error
    }
}

/**
 * Returns the [value][Result.value] if this result [is ok][Result.isOk], otherwise throws the
 * [transformation][transform] of the [error][Result.error] to a [Throwable].
 */
public inline fun <V, E> Result<V, E>.getOrThrow(transform: (E) -> Throwable): V {
    contract {
        callsInPlace(transform, InvocationKind.AT_MOST_ONCE)
    }

    return when {
        isOk -> value
        else -> throw transform(error)
    }
}

/**
 * Merges this [Result<V, E>][Result] to [U], returning the [value][Result.value] if this result
 * [is ok][Result.isOk], otherwise the [error][Result.error].
 */
public fun <V, E, U> Result<V, E>.merge(): U where V : U, E : U {
    return when {
        isOk -> value
        else -> error
    }
}

/**
 * Returns a list containing only elements that [are ok][Result.isOk].
 */
public fun <V, E> Iterable<Result<V, E>>.filterValues(): List<V> {
    return filterValuesTo(ArrayList())
}

/**
 * Returns a list containing only elements that [are an error][Result.isErr].
 */
public fun <V, E> Iterable<Result<V, E>>.filterErrors(): List<E> {
    return filterErrorsTo(ArrayList())
}

/**
 * Appends the [values][Result.value] of each element that [is ok][Result.isOk] to the given
 * [destination].
 */
public fun <V, E, C : MutableCollection<in V>> Iterable<Result<V, E>>.filterValuesTo(destination: C): C {
    for (element in this) {
        if (element.isOk) {
            destination.add(element.value)
        }
    }

    return destination
}

/**
 * Appends the [errors][Result.error] of each element that [is an error][Result.isErr] to the given
 * [destination].
 */
public fun <V, E, C : MutableCollection<in E>> Iterable<Result<V, E>>.filterErrorsTo(destination: C): C {
    for (element in this) {
        if (element.isErr) {
            destination.add(element.error)
        }
    }

    return destination
}

/**
 * Returns `true` if each element [is ok][Result.isOk], `false` otherwise.
 */
public fun <V, E> Iterable<Result<V, E>>.allOk(): Boolean {
    return all(Result<V, E>::isOk)
}

/**
 * Returns `true` if each element [is an error][Result.isErr], `false` otherwise.
 */
public fun <V, E> Iterable<Result<V, E>>.allErr(): Boolean {
    return all(Result<V, E>::isErr)
}

/**
 * Returns `true` if at least one element [is ok][Result.isOk], `false` otherwise.
 */
public fun <V, E> Iterable<Result<V, E>>.anyOk(): Boolean {
    return any(Result<V, E>::isOk)
}

/**
 * Returns `true` if at least one element [is an error][Result.isErr], `false` otherwise.
 */
public fun <V, E> Iterable<Result<V, E>>.anyErr(): Boolean {
    return any(Result<V, E>::isErr)
}

/**
 * Returns the number of elements that [are ok][Result.isOk].
 */
public fun <V, E> Iterable<Result<V, E>>.countOk(): Int {
    return count(Result<V, E>::isOk)
}

/**
 * Returns the number of elements that [are an error][Result.isErr].
 */
public fun <V, E> Iterable<Result<V, E>>.countErr(): Int {
    return count(Result<V, E>::isErr)
}

/**
 * Accumulates value starting with [initial] value and applying [operation] from left to right to
 * current accumulator value and each element.
 */
public inline fun <T, R, E> Iterable<T>.fold(
    initial: R,
    operation: (acc: R, T) -> Result<R, E>,
): Result<R, E> {
    var accumulator = initial

    for (element in this) {
        val result = operation(accumulator, element)

        accumulator = when {
            result.isOk -> result.value
            else -> return Err(result.error)
        }
    }

    return Ok(accumulator)
}

/**
 * Accumulates value starting with [initial] value and applying [operation] from right to left to
 * each element and current accumulator value.
 */
public inline fun <T, R, E> List<T>.foldRight(
    initial: R,
    operation: (T, acc: R) -> Result<R, E>,
): Result<R, E> {
    var accumulator = initial

    if (!isEmpty()) {
        val iterator = listIterator(size)

        while (iterator.hasPrevious()) {
            val result = operation(iterator.previous(), accumulator)

            accumulator = when {
                result.isOk -> result.value
                else -> return Err(result.error)
            }
        }
    }

    return Ok(accumulator)
}

/**
 * Combines the specified [results] into a single [Result] (holding a [List]). Elements in the
 * returned list are in the same order as the specified [results].
 */
public fun <V, E, R : Result<V, E>> combine(vararg results: R): Result<List<V>, E> {
    return results.asIterable().combine()
}

/**
 * Combines [this] iterable into a single [Result] (holding a [List]). Elements in the returned
 * list are in the the same order as [this].
 */
public fun <V, E> Iterable<Result<V, E>>.combine(): Result<List<V>, E> {
    val values = map { result ->
        when {
            result.isOk -> result.value
            else -> return result.asErr()
        }
    }

    return Ok(values)
}

/**
 * Returns a [List] containing the [value][Result.value] of each element in the specified [results]
 * that [is ok][Result.isOk]. Elements in the returned list are in the same order as the specified
 * [results].
 */
public fun <V, E, R : Result<V, E>> valuesOf(vararg results: R): List<V> {
    return results.asIterable().filterValues()
}

/**
 * Returns a [List] containing the [error][Result.error] of each element in the specified [results]
 * that [is an error][Result.isErr]. Elements in the returned list are in the same order as the
 * specified [results].
 */
public fun <V, E, R : Result<V, E>> errorsOf(vararg results: R): List<E> {
    return results.asIterable().filterErrors()
}

/**
 * Partitions the specified [results] into a [Pair] of [Lists][List]. An element that
 * [is ok][Result.isOk] will appear in the [first][Pair.first] list, whereas an element that
 * [is an error][Result.isErr] will appear in the [second][Pair.second] list.
 */
public fun <V, E, R : Result<V, E>> partition(vararg results: R): Pair<List<V>, List<E>> {
    return results.asIterable().partition()
}

/**
 * Partitions this into a [Pair] of [Lists][List]. An element that [is ok][Result.isOk] will appear
 * in the [first][Pair.first] list, whereas an element that [is an error][Result.isErr] will appear
 * in the [second][Pair.second] list.
 */
public fun <V, E> Iterable<Result<V, E>>.partition(): Pair<List<V>, List<E>> {
    val values = mutableListOf<V>()
    val errors = mutableListOf<E>()

    for (result in this) {
        if (result.isOk) {
            values += result.value
        } else {
            errors += result.error
        }
    }

    return Pair(values, errors)
}

/**
 * Returns a [Result<List<U>, E>][Result] containing the results of applying the given [transform]
 * function to each element in the original collection, returning early with the first [Err] if a
 * transformation fails. Elements in the returned list are in the same order as [this].
 */
public inline fun <V, E, U> Iterable<V>.mapResult(
    transform: (V) -> Result<U, E>,
): Result<List<U>, E> {
    val values = map { element ->
        val transformed = transform(element)

        when {
            transformed.isOk -> transformed.value
            else -> return transformed.asErr()
        }
    }

    return Ok(values)
}

/**
 * Applies the given [transform] function to each element of the original collection and appends
 * the results to the given [destination], returning early with the first [Err] if a
 * transformation fails. Elements in the returned list are in the same order as [this].
 */
public inline fun <V, E, U, C : MutableCollection<in U>> Iterable<V>.mapResultTo(
    destination: C,
    transform: (V) -> Result<U, E>,
): Result<C, E> {
    val values = mapTo(destination) { element ->
        val transformed = transform(element)

        when {
            transformed.isOk -> transformed.value
            else -> return transformed.asErr()
        }
    }

    return Ok(values)
}

/**
 * Returns a [Result<List<U>, E>][Result] containing only the non-null results of applying the
 * given [transform] function to each element in the original collection, returning early with the
 * first [Err] if a transformation fails. Elements in the returned list are in the same order as
 * [this].
 */
public inline fun <V, E, U : Any> Iterable<V>.mapResultNotNull(
    transform: (V) -> Result<U, E>?,
): Result<List<U>, E> {
    val values = mapNotNull { element ->
        val transformed = transform(element)

        when {
            transformed == null -> null
            transformed.isOk -> transformed.value
            else -> return transformed.asErr()
        }
    }

    return Ok(values)
}

/**
 * Applies the given [transform] function to each element in the original collection and appends
 * only the non-null results to the given [destination], returning early with the first [Err] if a
 * transformation fails.
 */
public inline fun <V, E, U : Any, C : MutableCollection<in U>> Iterable<V>.mapResultNotNullTo(
    destination: C,
    transform: (V) -> Result<U, E>?,
): Result<C, E> {
    val values = mapNotNullTo(destination) { element ->
        val transformed = transform(element)

        when {
            transformed == null -> null
            transformed.isOk -> transformed.value
            else -> return transformed.asErr()
        }
    }

    return Ok(values)
}

/**
 * Returns a [Result<List<U>, E>][Result] containing the results of applying the given [transform]
 * function to each element and its index in the original collection, returning early with the
 * first [Err] if a transformation fails. Elements in the returned list are in same order as
 * [this].
 */
public inline fun <V, E, U> Iterable<V>.mapResultIndexed(
    transform: (index: Int, V) -> Result<U, E>,
): Result<List<U>, E> {
    val values = mapIndexed { index, element ->
        val transformed = transform(index, element)

        when {
            transformed.isOk -> transformed.value
            else -> return transformed.asErr()
        }
    }

    return Ok(values)
}

/**
 * Applies the given [transform] function to each element and its index in the original collection
 * and appends the results to the given [destination], returning early with the first [Err] if a
 * transformation fails.
 */
public inline fun <V, E, U, C : MutableCollection<in U>> Iterable<V>.mapResultIndexedTo(
    destination: C,
    transform: (index: Int, V) -> Result<U, E>,
): Result<C, E> {
    val values = mapIndexedTo(destination) { index, element ->
        val transformed = transform(index, element)

        when {
            transformed.isOk -> transformed.value
            else -> return transformed.asErr()
        }
    }

    return Ok(values)
}

/**
 * Returns a [Result<List<U>, E>][Result] containing only the non-null results of applying the
 * given [transform] function to each element and its index in the original collection, returning
 * early with the first [Err] if a transformation fails. Elements in the returned list are in
 * the same order as [this].
 */
public inline fun <V, E, U : Any> Iterable<V>.mapResultIndexedNotNull(
    transform: (index: Int, V) -> Result<U, E>?,
): Result<List<U>, E> {
    val values = mapIndexedNotNull { index, element ->
        val transformed = transform(index, element)

        when {
            transformed == null -> null
            transformed.isOk -> transformed.value
            else -> return transformed.asErr()
        }
    }

    return Ok(values)
}

/**
 * Applies the given [transform] function to each element and its index in the original collection
 * and appends only the non-null results to the given [destination], returning early with the first
 * [Err] if a transformation fails.
 */
public inline fun <V, E, U : Any, C : MutableCollection<in U>> Iterable<V>.mapResultIndexedNotNullTo(
    destination: C,
    transform: (index: Int, V) -> Result<U, E>?,
): Result<C, E> {
    val values = mapIndexedNotNullTo(destination) { index, element ->
        val transformed = transform(index, element)

        when {
            transformed == null -> null
            transformed.isOk -> transformed.value
            else -> return transformed.asErr()
        }
    }

    return Ok(values)
}

/**
 * Invokes an [action] if this result [is ok][Result.isOk].
 *
 * - Rust: [Result.inspect](https://doc.rust-lang.org/std/result/enum.Result.html#method.inspect)
 */
public inline infix fun <V, E> Result<V, E>.onSuccess(action: (V) -> Unit): Result<V, E> {
    contract {
        callsInPlace(action, InvocationKind.AT_MOST_ONCE)
    }

    if (isOk) action(value)
    return this
}

/**
 * Invokes an [action] if this result [is an error][Result.isErr].
 *
 * - Rust [Result.inspect_err](https://doc.rust-lang.org/std/result/enum.Result.html#method.inspect_err)
 */
public inline infix fun <V, E> Result<V, E>.onFailure(action: (E) -> Unit): Result<V, E> {
    contract {
        callsInPlace(action, InvocationKind.AT_MOST_ONCE)
    }

    if (isErr) action(error)
    return this
}

/**
 * Returns [result] if this result [is an error][Result.isErr], otherwise [this].
 *
 * - Rust: [Result.or](https://doc.rust-lang.org/std/result/enum.Result.html#method.or)
 */
public infix fun <V, E, F> Result<V, E>.or(result: Result<V, F>): Result<V, F> {
    return when {
        isOk -> this.asOk()
        else -> result
    }
}

/**
 * Returns the [transformation][transform] of the [error][Result.error] if this result
 * [is an error][Result.isErr], otherwise [this].
 *
 * - Rust: [Result.or_else](https://doc.rust-lang.org/std/result/enum.Result.html#method.or_else)
 */
public inline fun <V, E, F> Result<V, E>.orElse(transform: (E) -> Result<V, F>): Result<V, F> {
    contract {
        callsInPlace(transform, InvocationKind.AT_MOST_ONCE)
    }

    return when {
        isOk -> this.asOk()
        else -> transform(error)
    }
}

/**
 * Throws the [error][Result.error] if this result [is an error][Result.isErr], otherwise returns
 * [this].
 */
public fun <V, E : Throwable> Result<V, E>.orElseThrow(): Result<V, Nothing> {
    return when {
        isOk -> this.asOk()
        else -> throw error
    }
}

/**
 * Throws the [error][Result.error] if this result [is an error][Result.isErr] and satisfies the
 * given [predicate], otherwise returns [this].
 *
 * @see [takeIf]
 */
public inline fun <V, E : Throwable> Result<V, E>.throwIf(predicate: (E) -> Boolean): Result<V, E> {
    contract {
        callsInPlace(predicate, InvocationKind.AT_MOST_ONCE)
    }

    return when {
        isErr && predicate(error) -> throw error
        else -> this
    }
}

/**
 * Throws the [error][Result.error] if this result [is an error][Result.isErr] and _does not_
 * satisfy the given [predicate], otherwise returns [this].
 *
 * @see [takeUnless]
 */
public inline fun <V, E : Throwable> Result<V, E>.throwUnless(predicate: (E) -> Boolean): Result<V, E> {
    contract {
        callsInPlace(predicate, InvocationKind.AT_MOST_ONCE)
    }

    return when {
        isErr && !predicate(error) -> throw error
        else -> this
    }
}

/**
 * Returns the [transformation][transform] of the [error][Result.error] if this result
 * [is an error][Result.isErr], otherwise [this].
 */
public inline fun <V, E> Result<V, E>.recover(transform: (E) -> V): Result<V, Nothing> {
    contract {
        callsInPlace(transform, InvocationKind.AT_MOST_ONCE)
    }

    return when {
        isOk -> this.asOk()
        else -> Ok(transform(error))
    }
}

/**
 * Returns the [transformation][transform] of the [error][Result.error] if this result
 * [is an error][Result.isErr], catching and encapsulating any thrown exception as an [Err],
 * otherwise [this].
 */
public inline fun <V, E> Result<V, E>.recoverCatching(transform: (E) -> V): Result<V, Throwable> {
    contract {
        callsInPlace(transform, InvocationKind.AT_MOST_ONCE)
    }

    return when {
        isOk -> this.asOk()
        else -> runCatching { transform(error) }
    }
}

/**
 * Returns the [transformation][transform] of the [error][Result.error] if this result
 * [is an error][Result.isErr] and satisfies the given [predicate], otherwise [this].
 */
public inline fun <V, E> Result<V, E>.recoverIf(
    predicate: (E) -> Boolean,
    transform: (E) -> V,
): Result<V, E> {
    contract {
        callsInPlace(predicate, InvocationKind.AT_MOST_ONCE)
        callsInPlace(transform, InvocationKind.AT_MOST_ONCE)
    }

    return when {
        isErr && predicate(error) -> Ok(transform(error))
        else -> this
    }
}

/**
 * Returns the [transformation][transform] of the [error][Result.error] if this result
 * [is an error][Result.isErr] and _does not_ satisfy the given [predicate], otherwise [this].
 */
public inline fun <V, E> Result<V, E>.recoverUnless(predicate: (E) -> Boolean, transform: (E) -> V): Result<V, E> {
    contract {
        callsInPlace(predicate, InvocationKind.AT_MOST_ONCE)
        callsInPlace(transform, InvocationKind.AT_MOST_ONCE)
    }

    return when {
        isErr && !predicate(error) -> Ok(transform(error))
        else -> this
    }
}

/**
 * Returns the [transformation][transform] of the [error][Result.error] if this result
 * [is an error][Result.isErr], otherwise [this].
 */
public inline fun <V, E> Result<V, E>.andThenRecover(transform: (E) -> Result<V, E>): Result<V, E> {
    contract {
        callsInPlace(transform, InvocationKind.AT_MOST_ONCE)
    }

    return when {
        isOk -> this
        else -> transform(error)
    }
}

/**
 * Returns the [transformation][transform] of the [error][Result.error] if this result
 * [is an error][Result.isErr] and satisfies the given [predicate], otherwise [this].
 */
public inline fun <V, E> Result<V, E>.andThenRecoverIf(
    predicate: (E) -> Boolean,
    transform: (E) -> Result<V, E>,
): Result<V, E> {
    contract {
        callsInPlace(predicate, InvocationKind.AT_MOST_ONCE)
        callsInPlace(transform, InvocationKind.AT_MOST_ONCE)
    }

    return when {
        isErr && predicate(error) -> transform(error)
        else -> this
    }
}

/**
 * Returns the [transformation][transform] of the [error][Result.error] if this result
 * [is an error][Result.isErr] and _does not_ satisfy the given [predicate], otherwise [this].
 */
public inline fun <V, E> Result<V, E>.andThenRecoverUnless(
    predicate: (E) -> Boolean,
    transform: (E) -> Result<V, E>,
): Result<V, E> {
    contract {
        callsInPlace(predicate, InvocationKind.AT_MOST_ONCE)
        callsInPlace(transform, InvocationKind.AT_MOST_ONCE)
    }

    return when {
        isErr && !predicate(error) -> transform(error)
        else -> this
    }
}
