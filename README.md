# AnyhowKt

> Rust-style error handling for Kotlin.

`AnyhowKt` is a Kotlin Multiplatform (KMP) library inspired by Rust's
[`anyhow`](https://docs.rs/anyhow/) crate. It provides a simple, powerful,
and expressive way to handle errors with:

- Context-aware errors
- Short-circuiting with `bind()` (Rust `?`-like behavior)
- Typed and untyped error sources
- `bail`, `ensure`, and structured error propagation
- Works across **JVM, Android, iOS, JS, Native**

## Features

- Rust-like `anyhow { }` result builder
- `bind()` for early-return error propagation (Rust `?`)
- `.context()` and `withContext()` for rich error messages
- `bail()` for immediate failure
- `ensure()` / `ensureNotNull()` helpers
- Convert **Kotlin `Result` to AnyhowResult**
- Typed + string + throwable errors

## Installation

### Gradle (Kotlin DSL) [Multiplatform]

```kotlin
sourceSets {
    commonMain.dependencies {
        implementation("io.github.itsvks19:anyhowkt:1.0.0")
    }
}
```

### Gradle (Kotlin DSL) [Android/Jvm]

```kotlin
dependencies {
    implementation("io.github.itsvks19:anyhowkt:1.0.0")
}
```

## Basic Usage

```kotlin
val result = anyhow {
    val a = parseInt("10").bind()
    val b = parseInt("20").bind()
    a + b
}
```

### Unwrapping

```kotlin
val value: Int = result.unwrap()
```

## Rust `?` Equivalent in Kotlin

Rust:

```rust
let value = compute()?;
```

Kotlin (`AnyhowKt`):

```kotlin
val value = compute().bind()
```

This works inside `anyhow { }` blocks.

## Contextual Errors

```kotlin
val result = anyhow {
    val file = readFile("config.json")
        .anyhowResult { "file not found" }
        .bind() // Early return on readFile failure

    parseConfig(file)
        .anyhow()
        .bind()
}.context("Failed to load application config")
```

Error output becomes:

```
Failed to load application config
Caused by: file not found
...
```

## `bail` – Early Exit

```kotlin
val result = anyhow {
    if (user == null) {
        bail("User not found")
    }
    user.name
}
```

## `ensure` Helpers

```kotlin
anyhow {
    ensure(x > 0) { "x must be positive" }
    ensureNotNull(user) { "user is required" }
    ensureEquals(a, b) { "values must match" }
}
```

## Kotlin Result -> AnyhowResult

```kotlin
val kResult: Result<Int> = runCatching { riskyCall() }

val result: AnyhowResult<Int> = kResult.anyhow()
```

## Plug-in for IntelliJ-based IDEs

If you are using an [IntelliJ IDEA](https://www.jetbrains.com/idea/) or any other IDE from JetBrains, we strongly recommend installing the [Arrow plug-in](https://plugins.jetbrains.com/plugin/24550-arrow). The plug-in helps fix common problems, especially in the realm of typed errors and suggests more idiomatic alternatives when available.

## Rust vs Kotlin Example

| Rust (`anyhow`)     | Kotlin (`AnyhowKt`) |
|---------------------|---------------------|
| `anyhow::Result<T>` | `AnyhowResult<T>`   |
| `?`                 | `.bind()`           |
| `bail!("msg")`      | `bail("msg")`       |
| `.context("msg")`   | `.context("msg")`   |
| `ensure!(cond)`     | `ensure(cond)`      |

## License

MIT License.

## Contributing

PRs and feature requests are welcome!
If you'd like to help add:

* richer formatting
* better JS/iOS backtraces
* or compiler plugin support

feel free to contribute ✨
