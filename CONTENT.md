**Brazier is not TeaVM.** If you want TeaVM, use [TeaVM](https://github.com/konsoletyper/teavm).
Brazier is a derivative of its 0.15.0 release, renamed so that neither project can be mistaken for
the other, and it exists to change two things upstream cannot change without breaking its own
compatibility promises.

## Why it exists

**A shared runtime.** TeaVM compiles whole-program: every bundle statically links its own copy of the
Java class library, and nothing in its configuration surface lets one bundle reference another's
classes. Measured across nine bundles of one real project, that cost **6552 KB, of which 4754 KB was
the same class library emitted nine times**. The union of those nine copies is 897 KB, a duplication
factor of 5.3x, and that union barely grew as bundles were added: the library slice is saturated, so
a shared runtime sized for today would serve further bundles almost free.

**A Java 8 floor.** TeaVM 0.15.0's published artifacts declare a minimum JVM version of 11 to 17, so
a project compiling against them cannot enforce `--release 8` on the modules that use them, even
where the code involved touches no post-8 API at all.

Both need compiler and build changes rather than configuration, which is what Brazier is for.

## Status

Early. The rename is complete and the build publishes under Brazier's own coordinates. Neither the
shared runtime nor the Java 8 floor is implemented yet.

## Your Java sources need no changes

Brazier deliberately keeps upstream's `org.teavm.*` package names, so `@JSBody`, `@JSExport` and
`@JSFunctor` keep their imports and a project moving from TeaVM edits only its build coordinates.
Renaming the packages would conflict on every file in every future merge from upstream, and a package
name is not the mark being separated. See [CHANGES.md](CHANGES.md) for that and for everything else
Brazier changes.

The library plugin is `io.github.intisy.brazier.library`.

## Usage

Brazier is not on the Gradle Plugin Portal. It serves itself as a static Maven repository, because a
plugin marker has to be resolvable through `pluginManagement` and no release-asset resolver can do
that. Declare the repository in `settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        maven("https://intisy.github.io/brazier/maven")
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        maven("https://intisy.github.io/brazier/maven")
        mavenCentral()
    }
}
```

Then apply it:

```kotlin
plugins {
    java
    id("io.github.intisy.brazier") version "0.1.0"
}

teavm.js {
    mainClass = "demo.Main"
    targetFileName = "demo.js"
}
```

`./gradlew generateJavaScript` writes the bundle to `build/generated/teavm/js`.

Only tagged releases are published to that repository. For local work on Brazier itself,
`./gradlew publishToMavenLocal` and `mavenLocal()` is the shorter route.

## Building

```
./gradlew publishToMavenLocal
```

Samples build separately, as described in [their readme](samples/README.md).

## Relationship to upstream

Brazier tracks `konsoletyper/teavm` as a git remote so upstream fixes can be merged. Nothing is
pushed back: Brazier is not a contribution route and its changes are not offered upstream.

The baseline is the `upstream-0.15.0` tag in this repository. Every file Brazier changes carries a
modification notice, [CHANGES.md](CHANGES.md) lists them all, and CI fails if the tree and that list
disagree, so the list cannot quietly rot.

**Report Brazier bugs here.** Do not report them to TeaVM unless they reproduce against unmodified
TeaVM.

## License

Apache License 2.0, the same as upstream, with `LICENSE` byte-identical to TeaVM's. Attribution is in
[NOTICE](NOTICE).

TeaVM relies on no OpenJDK or other (L)GPL code, and neither does Brazier. The Java class library
here is TeaVM's own reimplementation, written from scratch or based on non-(L)GPL projects:

* [Apache Harmony](https://harmony.apache.org/) (Apache 2.0)
* [Joda-Time](https://github.com/JodaOrg/joda-time) (Apache 2.0)
* [jzlib](https://github.com/ymnk/jzlib) (BSD style license)

Contributions to the class library must not be based on OpenJDK or other (L)GPL-licensed code.
