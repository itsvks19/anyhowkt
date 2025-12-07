import com.android.build.api.dsl.androidLibrary
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.vanniktech.mavenPublish)
    alias(libs.plugins.kotest)
    alias(libs.plugins.ksp)
}

group = "io.github.itsvks19"
version = "1.0.0"

kotlin {
    compilerOptions {
        optIn.addAll(
            "kotlin.contracts.ExperimentalContracts",
            "kotlin.experimental.ExperimentalTypeInference",
            "io.itsvks.anyhow.UnsafeResultApi"
        )
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    jvm()

    @Suppress("UnstableApiUsage")
    androidLibrary {
        namespace = "io.itsvks.anyhow"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        withJava() // enable java compilation support
        withHostTestBuilder {}.configure {}
        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }

        compilations.configureEach {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_11)
                }
            }
        }
    }

    macosArm64()
    iosSimulatorArm64()
    iosArm64()

    linuxX64()
    linuxArm64()
    macosX64()
    iosX64()
    tvosSimulatorArm64()
    tvosX64()
    tvosArm64()
    mingwX64()

    js {
        browser()
        nodejs()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            testTask { useKarma { useChromeHeadless() } }
            val rootDirPath = project.rootDir.path
            val projectDirPath = project.projectDir.path
            commonWebpackConfig {
                devServer = (devServer ?: KotlinWebpackConfig.DevServer()).apply {
                    // Serve sources to debug inside browser
                    static(rootDirPath)
                    static(projectDirPath)
                }
            }
        }
        nodejs()
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.arrow.core)
            implementation(libs.arrow.fx.coroutines)
        }

        commonTest {
            dependencies {
                implementation(libs.kotest.framework.engine)
                implementation(libs.kotest.assertions.core)
            }
        }

        jvmTest {
            dependencies {
                implementation(libs.kotest.runner.junit5)
            }
        }
    }
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    coordinates(group.toString(), "anyhowkt", version.toString())

    pom {
        name = "AnyhowKt"
        description = """
            A Kotlin Multiplatform error-handling library inspired by Rust's `anyhow` crate, 
            providing ergonomic error propagation, context, and lightweight dynamic error types.
        """.trimIndent()

        inceptionYear = "2025"
        url = "https://github.com/itsvks19/anyhowkt/"

        licenses {
            license {
                name = "MIT License"
                url = "https://opensource.org/licenses/MIT"
                distribution = "https://opensource.org/licenses/MIT"
            }
        }

        developers {
            developer {
                id = "itsvks19"
                name = "Vivek"
                email = "itsvks19@gmail.com"
            }
        }

        scm {
            connection = "scm:git:git://github.com/itsvks19/anyhowkt.git"
            developerConnection = "scm:git:ssh://github.com/itsvks19/anyhowkt.git"
            url = "https://github.com/itsvks19/anyhowkt"
        }
    }
}

tasks.named<Test>("jvmTest") {
    useJUnitPlatform()
    filter {
        isFailOnNoMatchingTests = false
    }
}
