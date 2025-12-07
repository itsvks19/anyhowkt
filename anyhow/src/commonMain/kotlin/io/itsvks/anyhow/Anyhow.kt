package io.itsvks.anyhow

import arrow.core.None
import arrow.core.Option
import arrow.core.Some
import arrow.core.raise.Raise
import arrow.core.raise.RaiseDSL
import arrow.core.raise.catch
import arrow.core.raise.fold
import arrow.core.raise.mapOrAccumulate
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.jvm.JvmName

@Suppress("NOTHING_TO_INLINE")
public inline fun <A> identity(a: A): A = a

@DslMarker
@RaiseDSL
public annotation class AnyhowDsl

/**
 * The canonical result type for the Anyhow error model.
 *
 * Represents either:
 * - `Ok(value)` — a successful computation
 * - `Err(error)` — a failed computation containing an [AnyhowError]
 */
public typealias AnyhowResult<V> = Result<V, AnyhowError>

/**
 * Convert Kotlin's [kotlin.Result] into an [AnyhowResult],
 * wrapping failures into an [AnyhowError].
 */
@JvmName("anyhowResult")
public fun <A> kotlin.Result<A>.anyhow(): AnyhowResult<A> =
    fold(::Ok, AnyhowError::wrap)

/**
 * Convert an [AnyhowResult] back into Kotlin's [kotlin.Result].
 */
public fun <A> AnyhowResult<A>.toKotlinResult() =
    fold(kotlin.Result.Companion::success, kotlin.Result.Companion::failure)

/**
 * The exception type used internally by Anyhow error transformations.
 */
public class AnyhowException(
    override val message: String?,
    override val cause: Throwable? = null
) : RuntimeException()

/**
 * A structured error type that supports contextual message stacking.
 *
 * Each operation may call `context(message)` to push additional information
 * onto the error stack. This produces rich debugging messages similar to Rust's `anyhow`.
 */
public class AnyhowError internal constructor(
    override val cause: Throwable?,
    private val ctx: List<String>
) : Throwable() {

    /**
     * Add contextual information to this error.
     *
     * The returned error includes both the previous context and the newly added message.
     */
    public fun context(message: String) = AnyhowError(cause, ctx + message)

    override fun toString(): String {
        return (ctx + cause?.message).joinToString("\nCaused by: ")
    }

    companion object {

        /**
         * Wrap a Throwable into an [AnyhowError] inside an `Err`.
         */
        @PublishedApi
        internal fun wrap(cause: Throwable?) = Err(AnyhowError(cause))
    }
}

internal interface AnyhowResultBindingScope : ResultBindingScope<AnyhowError>

@Suppress("NOTHING_TO_INLINE")
public inline fun AnyhowError(error: AnyhowError) = error

/**
 * Convert any [Throwable] into an [AnyhowError].
 */
public fun AnyhowError(cause: Throwable?) =
    cause as? AnyhowError ?: AnyhowError(cause, emptyList())

/**
 * Create an [AnyhowError] from a string message.
 */
public fun AnyhowError(message: String) =
    AnyhowError(AnyhowException(message), emptyList())

/**
 * Convert arbitrary error types into a structured [AnyhowError].
 *
 * Accepted inputs:
 * - AnyhowError -> returned as-is
 * - Throwable -> wrapped
 * - String -> converted to message error
 * - null -> treated as `"null"` error
 * - else -> converted via `toString()`
 */
public fun <Error> AnyhowError(error: Error) = when (error) {
    is AnyhowError -> error
    is Throwable -> AnyhowError(error)
    is String -> AnyhowError(error)
    null -> AnyhowError(AnyhowException("null"))
    else -> AnyhowError(error.toString())
}

/**
 * Execution scope used inside `anyhow { }` blocks.
 */
public class AnyhowScope(
    private val raise: Raise<AnyhowError>
) : Raise<AnyhowError> by raise, AnyhowResultBindingScope {

    /**
     * Bind a [Result], short-circuiting on error.
     */
    @AnyhowDsl
    override fun <V> Result<V, AnyhowError>.bind(): V = fold(::identity) { raise(it) }

    /**
     * Bind a nullable value, failing with a default null error.
     */
    @AnyhowDsl
    public fun <A> A?.bind(): A {
        contract {
            returns() implies (this@bind != null)
            returnsNotNull() implies (this@bind != null)
        }
        return this ?: raise(AnyhowError("null value"))
    }

    /**
     * Bind a nullable value, raising a custom error when null.
     */
    @AnyhowDsl
    public fun <A, Error> A?.bind(error: () -> Error): A {
        contract {
            callsInPlace(error, InvocationKind.AT_MOST_ONCE)
            returns() implies (this@bind != null)
            returnsNotNull() implies (this@bind != null)
        }
        return this ?: raise(error())
    }

    /**
     * Bind a generic [Result].
     */
    @AnyhowDsl
    @JvmName("bindResult")
    public fun <A, E> Result<A, E>.bind(): A = fold(::identity) { raise(it) }

    /**
     * Bind all results in a map, failing on the first error.
     */
    @JvmName("bindAllResult")
    public fun <K, V> Map<K, AnyhowResult<V>>.bindAll(): Map<K, V> = mapValues { (_, v) -> v.bind() }

    /**
     * Bind every result in an iterable, collecting successful values.
     */
    @AnyhowDsl
    @JvmName("bindAllResult")
    public fun <A> Iterable<AnyhowResult<A>>.bindAll(): List<A> = map { it.bind() }

    /**
     * Bind an Option, raising an error if None.
     */
    @AnyhowDsl
    public fun <A> Option<A>.bind(message: () -> String): A {
        contract {
            callsInPlace(message, InvocationKind.AT_MOST_ONCE)
        }
        return when (this) {
            is Some -> value
            is None -> bail(message())
        }
    }

    /**
     * Raises a _logical failure_ of type [AnyhowError].
     * This function behaves like a _return statement_,
     * immediately short-circuiting and terminating the computation.
     *
     * __Alternatives:__ Common ways to raise errors include: [ensure], [ensureNotNull], and [bind].
     * Consider using them to make your code more concise and expressive.
     *
     * __Handling raised errors:__ Refer to [recover] and [mapOrAccumulate].
     *
     * @param error an error of type [String] that will short-circuit the computation.
     * Behaves similarly to _return_ or _throw_.
     */
    @AnyhowDsl
    @JvmName("raiseAnyhow")
    public fun <Error> raise(error: Error): Nothing = raise(AnyhowError(error))

    /**
     * Immediately short-circuits with the given error.
     */
    @AnyhowDsl
    public fun bail(message: String): Nothing = raise(message)

    /**
     * Immediately short-circuits with the given error.
     */
    @AnyhowDsl
    public fun bail(error: Throwable): Nothing = raise(AnyhowError(error))

    /**
     * Ensures that the [condition] is met;
     * otherwise, [Raise.raise]s a logical failure of type [AnyhowError].
     *
     * In summary, this is a type-safe alternative to [require], using the [Raise] API.
     */
    @AnyhowDsl
    public inline fun <Error> ensure(
        condition: Boolean,
        error: () -> Error
    ) {
        contract {
            callsInPlace(error, InvocationKind.AT_MOST_ONCE)
            returns() implies condition
        }
        if (!condition) raise(error())
    }

    /**
     * Ensures that the [value] is not null;
     * otherwise, [Raise.raise]s a logical failure of type [AnyhowError].
     *
     * In summary, this is a type-safe alternative to [requireNotNull], using the [Raise] API.
     */
    @AnyhowDsl
    public inline fun <T : Any, Error> ensureNotNull(
        value: T?,
        error: () -> Error
    ): T {
        contract {
            callsInPlace(error, InvocationKind.AT_MOST_ONCE)
            returns() implies (value != null)
            returnsNotNull() implies (value != null)
        }
        return value ?: raise(error())
    }

    /**
     * Add contextual information around a nested computation.
     */
    public inline fun <T> withContext(
        message: String,
        block: AnyhowScope.() -> T
    ): T {
        contract {
            callsInPlace(block, InvocationKind.AT_MOST_ONCE)
        }
        return anyhow(block).fold(::identity) { error -> raise(error.context(message)) }
    }

    /**
     * Starts a new nested [AnyhowScope] context.
     * Useful for grouping operations where failures should be caught or transformed differently
     * within a larger [AnyhowScope] block.
     */
    public inline fun <A> anyhow(block: AnyhowScope.() -> A): AnyhowResult<A> {
        contract {
            callsInPlace(block, InvocationKind.AT_MOST_ONCE)
        }
        return io.itsvks.anyhow.anyhow(block)
    }
}

/**
 * Execute a block inside an [AnyhowScope], capturing errors as [AnyhowResult].
 */
@AnyhowDsl
public inline fun <A> anyhow(block: AnyhowScope.() -> A): AnyhowResult<A> {
    contract { callsInPlace(block, InvocationKind.AT_MOST_ONCE) }
    return fold({ block(AnyhowScope(this)) }, AnyhowError::wrap, ::Err, ::Ok)
}

/**
 * Raise an error inside an [AnyhowScope].
 */
@AnyhowDsl
public fun <E> AnyhowScope.anyhow(error: E): Nothing = raise(error)

/**
 * Convenience entry point for raising an immediate error.
 */
@AnyhowDsl
public fun <E> anyhow(error: E) = anyhow { raise(error) }

@Suppress("ClassName")
@AnyhowDsl
public object anyhow {
    public fun <A> ok(value: A) = anyhow { value }
    public fun err(error: AnyhowError) = anyhow { raise(error) }
    public fun err(error: Throwable) = anyhow { raise(error) }
    public fun err(error: String) = anyhow { raise(error) }
    public fun <Error> err(error: Error) = anyhow { raise(error) }
}

/**
 * Catch exceptions inside an [AnyhowScope] and convert them into failures.
 */
@AnyhowDsl
public inline fun <T> AnyhowScope.catching(block: () -> T): T = catch(block) { raise(it) }

/**
 * Add structural context to a result error.
 */
public fun <T> AnyhowResult<T>.context(msg: String): AnyhowResult<T> = mapError { it.context(msg) }
