# What Brazier changes, relative to TeaVM 0.15.0

Brazier is a modified derivative of [TeaVM](https://github.com/konsoletyper/teavm). This file is the
complete, authoritative list of files it changes, and CI fails the build if the working tree and this
list disagree. Every file named here carries a modification notice in its own header, as the Apache
License, Version 2.0 requires.

The upstream baseline is the tag `upstream-0.15.0` in this repository, which is
`konsoletyper/teavm` at its own `0.15.0` tag. Diffing against a tag in this repository rather than
against a remote keeps the gate reproducible offline.

## Deliberately unchanged

Two things a reader may expect to have been renamed, and the reasons they were not. Neither is an
oversight, and neither should be "finished" by a later change.

- **Java package names stay `org.teavm.*`.** Renaming them across 2282 files would conflict on every
  file in every future merge from upstream, and a package name is not the mark being separated. It
  also means a consumer's own Java sources need no edit at all: `@JSBody`, `@JSExport` and
  `@JSFunctor` keep their imports.
- **The internal build-logic convention plugin ids stay `teavm-publish` and `teavm-release`.** They
  are Gradle convention plugins private to this build and no consumer ever sees them. Renaming them
  would add roughly thirty files to this list for no external benefit.

## 1. Coordinates

The Maven group, artifact names, project name and Gradle plugin ids are renamed so that nothing
Brazier publishes can collide with, or be mistaken for, TeaVM.

| from | to |
| --- | --- |
| group `org.teavm` | `io.github.intisy.brazier` |
| artifacts `teavm-*` | `brazier-*` |
| root project `teavm` | `brazier` |
| plugin id `org.teavm` | `io.github.intisy.brazier` |
| plugin id `org.teavm.library` | `io.github.intisy.brazier.library` |
| property `teavm.project.version` | `brazier.project.version` |
| version `0.15.0-SNAPSHOT` | `1.0.0-SNAPSHOT` |

Brazier versions on its own line rather than tracking TeaVM's, because it is its own project. Which
upstream release it derives from is recorded by the `upstream-0.15.0` tag and by this file, not by
the version number.

**The group is declared in two places, and both must change.** `build.gradle.kts` sets
`project.group`, but publications do not use it: `PublishTeaVMPlugin` calls `setGroupId` with a
literal. Changing only the build script publishes `brazier-*` artifacts under `org.teavm`, which was
caught by publishing one module locally and looking at where the file landed. Upstream carries the
same value twice; Brazier changes the same two literals rather than restructuring.

**Published pom metadata points at Brazier, and authorship still credits upstream.** `scm` and `url`
in `settings.gradle.kts` named upstream's repository and site, which would have sent anyone following
them to TeaVM, contradicting the NOTICE that asks for Brazier bugs to be filed here. Alexey Andreev
stays listed as a developer, with Brazier's maintainer added beside him.

**Files (31):**

- `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`
- `build-logic/src/main/java/org/teavm/buildutil/PublishTeaVMPlugin.java`, the publication groupId
- `tools/gradle/build.gradle.kts`, which also carries the plugin ids, display names, website and
  vcsUrl
- the 26 remaining module build scripts that declare an `artifactId`: `classlib`, `core`,
  `extension/apis`, `extension/processor`, `extension/spi`, `extension/spi-util`, `extras-slf4j`,
  `interop/core`, `jso/apis`, `jso/core`, `jso/impl`, `metaprogramming/api`, `metaprogramming/impl`,
  `platform`, `tools/browser-runner`, `tools/c-incremental`, `tools/chrome-rdp`, `tools/core`,
  `tools/deobfuscator-wasm-gc`, `tools/devserver/client`, `tools/devserver/core`,
  `tools/devserver/runner`, `tools/junit`, `tools/maven/plugin`, `tools/maven/webapp`,
  `tools/maven/webapp-wasm-gc`

The Gradle plugin derives the coordinates it injects into consumer builds from the publishing
configuration at build time, through `findArtifactCoordinates`, so the artifact renames propagate on
their own and no coordinate is written twice.

## 2. Samples

The samples are a separate composite build that applies the plugin by id and pins its version, so
they would not resolve after the rename. They are kept working because they are the cheapest
end-to-end check that the plugin id, the version and the published coordinates all agree.

**Files (14):** `samples/build.gradle.kts` and the thirteen sample modules under it.

## 3. Project identity

`NOTICE` gains Brazier's attribution above upstream's, which is retained in full as section 4 of the
licence requires. `LICENSE` is byte-identical to upstream's and must stay so.

**Upstream's `README.md` is deleted from this branch, not edited.** The house convention across this
organisation is that a README is generated onto the default branch and never hand-written, so the
development branch carries the source instead: `CONTENT.md` for the body and
`.github/docs-config.yml` for the title, badges, release table and plugin-usage block. A thin
`.github/workflows/readme.yml` calls the shared generator, which renders and commits `README.md` on
`main`.

That is why a reader diffing this branch against upstream sees a deleted README rather than a
modified one, and why editing `README.md` directly would be reverted by the next generator run.

**Files (6):** `NOTICE` (modified), `README.md` (deleted), and four added: `CHANGES.md`,
`CONTENT.md`, `.github/docs-config.yml`, `.github/workflows/readme.yml`.
