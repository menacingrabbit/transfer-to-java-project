# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A **Java 21 / Spring Boot 3.5** port of the Python project in `project-code-python/`
(kept untouched as the reference). One pipeline service is reached from two entry points:
a CLI (`CommandLineRunner`) and a REST API. The code is deliberately **heavily commented**
for teaching — comments may rival code in volume. Preserve that style when editing.

## Build / test / run commands

All commands run from the repo root. The Maven Wrapper is included; **set `JAVA_HOME` first**
(the machine may otherwise default to an old JRE).

```bash
export JAVA_HOME=/c/Users/Guybrush/.jdks/jdk-21.0.12+8   # Windows Git Bash example
./mvnw -q package          # compile + test + fat jar at target/yt-analysis-0.0.1-SNAPSHOT.jar
./mvnw -q test             # run all unit/web/integration tests (no network needed)
./mvnw -q test -Dtest=WebLayerTest -DtrimStackTrace=false   # single test class
java -jar target/yt-analysis-0.0.1-SNAPSHOT.jar --help                      # CLI mode
java -jar target/yt-analysis-0.0.1-SNAPSHOT.jar                             # REST mode (:8080)
```

The `artifact` group / package root is `com.example.ytanalysis`.

## Architecture (the big picture)

`YtAnalysisApplication.main` chooses the mode by whether CLI args are present:
- **args present** → `WebApplicationType.NONE` (no Tomcat), then `SpringApplication.exit`
  + `System.exit(code)` so `$LASTEXITCODE` is correct.
- **no args** → SERVLET web mode; `main` **must NOT call `System.exit`** or the server dies
  immediately after starting.

Both modes converge on **`service.PipelineOrchestrator.analyse(url, outDir, options)`** —
the only place the pipeline logic lives:

```
download (yt-dlp + ffmpeg MP3, 2-pass with resume) → optional split → transcribe
→ save transcript → summarise → save summary
```

Everything is constructor-injected; there is no manual wiring. Records are used for all data
holders and DTOs.

## Non-obvious things to know (gotchas that cost real time)

1. **Lazy API key.** `OpenRouterProperties.apiKey()` reads `System.getenv` **only when called**
   (first actual API call), so `--help` and startup work with no key. Binding is `@ConfigurationProperties`
   (relaxed binding: `OPENROUTER_API_KEY` → `openrouter.api-key`). Never make it eager.
2. **`@Retryable` needs concrete types, not the marker interface.** `@Retryable(retryFor=...)`
   and `SimpleRetryPolicy` are typed `Class<? extends Throwable>`, which an **interface**
   reference does not satisfy. `RetryableApiException` is a marker *interface*; so `retryFor`
   lists the two concrete transient classes instead:
   `retryFor = {TransientTranscriptionException.class, TransientSummarisationException.class}`.
   Matching is subclass-based, so permanent exceptions (which don't extend them) never retry.
3. **`@Retryable` is AOP — it only works when the call crosses a bean boundary.**
   `TranscriptionService` is deliberately a **separate bean** (not `@Retryable` itself) so its
   calls into `OpenRouterClient` go through the retry proxy. A self-invocation inside the same
   bean would bypass the proxy and never retry. The integration test verifies
   `AopUtils.isAopProxy(client)`.
4. **`PipelineIntegrationTest` is the canary for wiring.** Things like "X is not a bean / not
   a `@Component`" will only surface when the full context boots (`@SpringBootTest`), not in
   unit tests. `CliArgumentParser` had to be made a `@Component` for this reason.
5. **Fat jar needs the plugin declared.** Without `<plugin>spring-boot-maven-plugin</plugin>`
   in `<build>`, `mvn package` emits a thin jar and `java -jar` fails with "no main manifest
   attribute".
6. **`Path` serialises as a `file:///` URI in JSON.** The REST DTOs use `String` path fields;
   the controller converts via `Path.toString()` (relative, same form the CLI logs).
   On Windows the separator is `\`.
7. **Text blocks need a line terminator** right after the opening `"""` — an inline
   `"""{"url":"x"}"""` does not compile.
8. **Filename rules live in `util/FileNameUtil` + `util/SlugUtil`** (port of `yt/utils.py`),
   and **must stay byte-for-byte faithful** to the Python (they drive resume matching).
   `SlugUtilTest` pins them. Resume: glob `*-<videoId>.mp3`, else legacy exact-title match,
   newest mtime wins; `--force` redownloads.
9. **External binaries are subprocesses.** Every `ProcessBuilder` call goes through
   `util/CommandRunner` (a final class wrapping `ProcessBuilder`), so tests mock it.
   Subprocess failures throw; `yt-split-` temp dirs are guarded/cleaned up.

## Porting notes (Python `project-code-python/` ↔ Java)

See the migration table in `README.md`. Each Java class's javadoc states which Python module /
function it ports and what changed (e.g. Python's multiple-inheritance `RetryableError` mixin
becomes Java's `RetryableApiException` marker interface; Python's `httpx.post` becomes
synchronous `RestClient`).

## Conventions to preserve

- Heavy English educational comments on every class/method + a *what/why* and *Python↔Java*
  mapping note.
- Records for DTOs/data holders; constructor injection; `@Service`/`@Component`/`@RestController`.
- No `spring-boot-starter-webflux`/`WebClient` — the app is intentionally synchronous
  (explained in `pom.xml`).
- Tests: JUnit 5 + AssertJ; mock the leaves (`CommandRunner`, `RestClient`); no real network.
