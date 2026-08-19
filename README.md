# yt-analysis-and-summary (Java / Spring Boot)

A faithful **Java + Spring Boot** port of the Python CLI project in
[`project-code-python/`](project-code-python/) (which is left untouched as the reference).

```text
YouTube URL → yt-dlp (audio) → ffmpeg (MP3) → [optional split <10 min]
            → OpenRouter transcription → <stem>_transcript.txt
            → OpenRouter summarisation  → <stem>_summary.txt
```

The whole pipeline runs from the **command line** *and* over a simple **REST API** — both
entry points share the exact same orchestration service.

> This repository is written as an **educational Spring Boot project**. Every class and
> method carries tutorial-style comments explaining *what* it does, *why* it exists, and
> *how it maps* back to the original Python code. Read it top to bottom and you will learn
> Spring (dependency injection, beans, `@ConfigurationProperties`, `RestClient`, AOP/`@Retryable`,
> `CommandLineRunner`, `@RestControllerAdvice`) by porting a real, working program.

---

## Requirements

- **JDK 21** (LTS). The Maven Wrapper (`mvnw` / `mvnw.cmd`) is included, so **no separate
  Maven install is needed**.
- Runtime tools on `PATH` (same as the Python original):
  - [`yt-dlp`](https://github.com/yt-dlp/yt-dlp)
  - `ffmpeg` and `ffprobe` (any recent build)
- An **OpenRouter API key** (environment variable `OPENROUTER_API_KEY`).

The API key is read lazily — the app starts and even prints `--help` without one; it is only
required when the pipeline actually calls OpenRouter.

### Setting the API key

Spring Boot does **not** read `.env` files itself; set a real environment variable.

PowerShell:
```powershell
$env:OPENROUTER_API_KEY = "sk-or-v1-..."
```
(cmd: `set OPENROUTER_API_KEY=sk-or-v1-...`, bash: `export OPENROUTER_API_KEY=...`)

---

## Quick start

### 1) Build

```bash
./mvnw package          # Windows: .\mvnw.cmd package
```
This produces an executable (fat) jar at `target/yt-analysis-0.0.1-SNAPSHOT.jar`.

### 2) Run from the command line (CLI mode)

```bash
java -jar target/yt-analysis-0.0.1-SNAPSHOT.jar --url "https://youtu.be/dQw4w9WgXcQ" --out-dir output
```

Options (identical semantics to the Python original):

| Flag | Meaning |
|------|---------|
| `--url <url>` | a single YouTube URL (with `--batch-file`, mutually exclusive) |
| `--batch-file <file>` | a text file with one URL per line (`#` lines are ignored) |
| `--out-dir <dir>` | output directory (default `output`) |
| `--no-summary` | skip summarisation (transcript only) |
| `--force` | re-download even if a matching audio file already exists |
| `--split` | split long audio into chunks < 10 minutes before transcribing |
| `--verbose` / `--quiet` | control log verbosity |
| `--help` | show usage |

If no argument is given, the app starts as a **REST server** instead.

**Resume behaviour:** running the same URL again finds the existing audio and skips the
download; `--force` forces a fresh download.

### 3) Run as a REST server (web mode)

```bash
java -jar target/yt-analysis-0.0.1-SNAPSHOT.jar
# or during development:
./mvnw spring-boot:run
```

| Method & path | Purpose |
|---------------|---------|
| `GET /api/health` | cheap, offline health probe |
| `POST /api/analyse` | run the pipeline and return the result as JSON |

Example:

```bash
curl -X POST http://localhost:8080/api/analyse \
  -H "Content-Type: application/json" \
  -d '{"url":"https://youtu.be/dQw4w9WgXcQ","split":true}'
```

```json
{
  "success": true,
  "videoId": "dQw4w9WgXcQ",
  "title": "Rick Astley - Never Gonna Give You Up",
  "transcript": "...",
  "summary": "...",
  "audioPath": "output/20260819-never-gonna-give-you-up-dQw4w9WgXcQ.mp3",
  "transcriptPath": "output/..._transcript.txt",
  "summaryPath": "output/..._summary.txt"
}
```

Optional request fields (all default sensibly): `split`, `noSummary`, `force`, `save`
(write files; default `true`), `outDir`.

> **Synchronous note:** `POST /api/analyse` blocks for the whole run (minutes). That is fine
> for a tutorial. Production would use `@Async` + a job endpoint (`GET /api/jobs/{id}`);
> a sketched path is commented in `AnalysisController`.

---

## Tests

```bash
./mvnw test
```

No network and no subprocesses are required — every external boundary (`CommandRunner`,
`RestClient`) is mocked. The suite covers filename rules, URL validation, the CLI parser,
the retry policy, the web layer, and a full-context wiring test.

---

## Architecture

```
YtAnalysisApplication  (entry point; chooses CLI vs web mode)
│
├── cli.CliRunner          CommandLineRunner — parses args, drives the pipeline
├── web.AnalysisController RestController — POST /api/analyse + GET /api/health
│
└── service.PipelineOrchestrator   ← the ONE pipeline both entry points call
        │
        ├── AudioDownloadService   yt-dlp download / resume, ffmpeg → MP3
        ├── TranscriptionService   optional split + transcribe chunks, cleanup
        ├── SummarisationService   summarise + save <stem>_summary.txt
        └── ProgressReporter       log banners
                │
                └── client.OpenRouterClient   RestClient + @Retryable (transcribe/summarise)
```

### Key Spring concepts on display

| Concept | Where |
|---------|-------|
| Component scan + `@SpringBootApplication` | `YtAnalysisApplication` |
| Constructor injection everywhere | every `@Service` / `@RestController` |
| `@ConfigurationProperties` (relaxed binding) | `config/OpenRouterProperties`, `config/PipelineProperties` |
| `@Bean` + `RestClient` | `config/RestClientConfig` |
| AOP `@EnableRetry` + `@Retryable` | `config/RetryConfig`, `client/OpenRouterClient` |
| Marker interface for retry classification | `client/exception/RetryableApiException` + transient exceptions |
| `CommandLineRunner` + `ExitCodeGenerator` | `cli/CliRunner` |
| `@RestControllerAdvice` + `ProblemDetail` | `web/GlobalExceptionMapper` |
| Jakarta Bean Validation | `web/AnalyseRequest` (`@NotBlank`) |
| Records as DTOs / data holders | `model/*`, `web/*` |

---

## Python → Spring migration map

| Python (project-code-python) | Java / Spring |
|------------------------------|---------------|
| `cli.py::main` | `YtAnalysisApplication.main` |
| `cli.py::process_single_url` | `service/PipelineOrchestrator.analyse` |
| `yt/downloader.py` | `service/AudioDownloadService` |
| `audio/splitter.py` | `service/AudioSplitter`, `service/AudioProbeService` |
| `transcription/client.py` (`_post`, `@retry`) | `client/OpenRouterClient` (`postJson`, `@Retryable`) |
| `transcribe_split` | `service/TranscriptionService` |
| `summariser.py` | `service/SummarisationService` |
| `utils/retry.py` (tenacity) | `@Retryable` + `RetryConfig` (and `RetryTemplate`) |
| `config.py` | `config/OpenRouterProperties`, `config/PipelineProperties` |
| `httpx.post` | `RestClient` (synchronous, Java 6.1+) |
| `shutil.which` | `util/ExecutableFinder` |
| `subprocess.run(check=True)` | `util/CommandRunner` (wraps `ProcessBuilder`) |
| `argparse` | `cli/CliArgumentParser` (hand-rolled for teaching) |
| `validate_youtube_url` + regex | `util/YoutubeUrlValidator` |
| `slugify` / `clean_title` | `util/SlugUtil` |

---

## Retry policy

Mirrors the Python `tenacity` decorator (`attempts=3`, exponential backoff). **Only the two
leaf API methods** (`transcribe`, `summarise`) are `@Retryable` — orchestration is not, so a
late failure never re-splits or re-transcribes chunks that already succeeded.

- **Transient** failures (HTTP 408/429/500/502/503/504, network errors) → retried up to
  3 times with backoff 1s → 2s → 4s (capped at 10s).
- **Permanent** failures (bad request, bad key, content policy, …) → failed immediately,
  never retried.

Classification is done via the `RetryableApiException` marker interface: transient exceptions
implement it, permanent ones deliberately do not.

---

## Project layout

```
src/main/java/com/example/ytanalysis/
  YtAnalysisApplication.java     entry point / CLI-vs-web switch
  config/   properties, RestClient, retry wiring
  model/    records (VideoInfo, AnalysisResult, PipelineOptions, ...)
  util/     SlugUtil, YoutubeUrlValidator, FileNameUtil, CommandRunner, ExecutableFinder
  client/   OpenRouterClient + exception hierarchy + JSON records
  service/  download, split, transcribe, summarise, orchestration
  cli/      argument parser + CLI runner
  web/      controller, request/response records, exception mapper
src/test/java/...               JUnit 5 unit + web-slice + integration tests
```

The original Python codebase lives in [`project-code-python/`](project-code-python/) as the
line-by-line reference for the port.
