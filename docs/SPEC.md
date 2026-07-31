# Convert HmrcLogger to an HMRC MDTP-standard Scala/Play service at C:\code\hmrclog

## Context

HmrcLogger (`C:\Users\mark\RiderProjects\HmrcLogger`) simulates HMRC's side of the API platform: it ingests log lines in the hmrc/vat-api logback format (extended with `appId=[…]`/`clientId=[…]` MDC fields) and shows a Developer-Hub-style dashboard of MTD ITSA endpoint usage per application/client/month, for developers and internal staff. It is currently .NET 10 Blazor Server + SQLite + ASP.NET Identity + QuestPDF, with pinned scraped GOV.UK CSS.

Real HMRC MDTP services are Scala + Play + Twirl. This task rewrites the app as a genuine MDTP-style frontend microservice — **hmrc-log-dashboard-frontend** — in a new folder `C:\code\hmrclog`, modelled on `hmrc/hmrc-frontend-scaffold.g8` and real services like `hmrc/tai-frontend`, using `play-frontend-hmrc` Twirl components instead of scraped CSS. Feature parity with the .NET app, including PDF export (ported last). Local-only; no Azure deployment. Execute all 7 phases in one run, verifying each before the next.

**User decisions:** local MongoDB install ✔; PDF ported in final phase ✔; all phases in one run ✔; local-only ✔.

## Stack

- Scala **3.3.6**, Play **3.0** (`org.playframework % sbt-plugin % 3.0.9`), sbt 1.10.x, Temurin **21** (Java 26 is installed but unvalidated for this stack — install and pin 21)
- `bootstrap-frontend-play-30 10.8.0`, `play-frontend-hmrc-play-30 13.9.0`, `hmrc-mongo-play-30 2.7.0` (verify latest at build time), test: `bootstrap-test-play-30`, `hmrc-mongo-test-play-30`
- MongoDB Community Server as local Windows service (winget), DB `hmrc-log-dashboard-frontend`
- Default port **9250**

## Source of truth (behaviour to port, in the old repo)

- `HmrcLogger\Services\LogLineParser.cs` — 6 regexes, 4 timestamp formats, never-throws, kind classification order Success→Failure→Start→Other
- `HmrcLogger\Services\DashboardQueryService.cs` — **the core spec**: one request = one correlationId; outcome = max(status) else (any Failure-kind → 500 else 200); tags: Fix = any 5xx or >25% failures, Review = any failure, Correct = all 2xx; area tag = max endpoint tag; sort areas by tag desc then volume, endpoints by requests desc; report caps 1000 failed rows / 500 warnings, strict clientId warning matching
- `HmrcLogger\Domain\ItsaEndpointCatalog.cs` — ~94 endpoint definitions (~27 ITSA API areas + 4 VAT) to transcribe; lookups by (controller,endpoint), by path+method regex, by key
- `HmrcLogger\Services\SampleLogGenerator.cs` + `DatabaseSeeder.cs` — deterministic seeder (seed 20260717): 3 fixed apps (Tax Optimiser prod `8dab5c01-…` 0.60/12 clients, Sandbox 0.12/4, BooksFlow 0.28/8), ~5000 requests → ~10k lines over 6 months, business-hours skew, 30 weighted endpoints, deliberate unhealthy endpoints (submitFinalDeclaration 500s, amendFinancialDetails >25% fail, retrieveCalculation 404s, triggerBsas mixed)
- `HmrcLogger\Services\LogIngestionService.cs` — 500/batch, auto-register unknown appIds as "Application {8 chars}"/Unknown, result = linesRead/parsed/failed + first 5 failed samples
- `HmrcLogger.Tests\LogLineParserTests.cs` — 8 tests to port as Phase 2 acceptance

## Key design decisions

| Decision | Choice |
|---|---|
| Storage | MongoDB + hmrc-mongo; collections `applications` (UUID-string `_id`) and `log-entries`; 4 indexes: `(applicationId, timestampUtc)`, `(applicationId, endpointKey, timestampUtc)`, `(correlationId)`, `(applicationId, clientId)`; timestamps via `MongoJavatimeFormats` |
| Aggregation | In-memory grouping in `DashboardService` over a projected month slice (faithful port; ~10k docs is trivial) |
| bootstrap-frontend | Use it, with platform integrations disabled: `auditing.enabled=false`, metrics/graphite off, **no** `AuthModule`, allowlist filter off, `tracking-consent gtm.container="transparent"`. Keep bootstrap coupling to ErrorHandler + conf so fallback to plain Play is cheap |
| Auth | Scaffold-style `IdentifierAction` trait + `SessionIdentifierAction` impl; GOV.UK login page checking config-held creds (`dev@hmrclogger.local` / `LetMeIn-2026!`); swapping in real auth-client later = one Guice binding in `config.Module` |
| Reseed | MDTP idiom: `conf/testOnlyDoNotUseInAppConf.routes` with `POST /test-only/seed?force=true` (run via `-Dapplication.router=testOnlyDoNotUseInAppConf.Routes`) + eager-singleton seed-if-empty on startup (`seed-on-startup=true`) |
| Filters | GET form: three `govukSelect` (month / application / client) + explicit "Apply filters" `govukButton` — no-JS baseline, bookmarkable |
| Raw-lines expander | Server-rendered `govukDetails` per correlation ID on endpoint detail (no JS) |
| PDF | Phase 7: openhtmltopdf-pdfbox rendering a strict-XHTML print Twirl view |
| Seeder fidelity | `java.util.Random` ≠ .NET Random: assert distribution shape (volume shares, client counts, unhealthy endpoints, all 3 tag colours), not exact lines |

## Build definition essentials

- `project/build.properties`: `sbt.version=1.10.11`
- `project/plugins.sbt`: HMRC open-artefacts resolvers (maven2 + ivy2 at `open.artefacts.tax.service.gov.uk`) + `addSbtPlugin("org.playframework" % "sbt-plugin" % "3.0.9")`. Omit HMRC's sbt-auto-build/sbt-distributables (platform-only).
- `project/AppDependencies.scala` with the coordinates above; `build.sbt`: `Project("hmrc-log-dashboard-frontend", file("."))`, `PlayKeys.playDefaultPort := 9250`, TwirlKeys.templateImports for govuk/hmrc components + CSPNonce, `routesImport += "java.util.UUID"`
- `conf/application.conf`: `include "frontend.conf"`, `play.http.errorHandler = handlers.ErrorHandler`, `PlayMongoModule` + `config.Module`, `mongodb.uri`, the off-platform disable keys above, `login.username/password`, `seed-on-startup`, `upload.max-size-bytes = 20971520`. Exact disable-key names reconciled against bootstrap 10.8.0's shipped `frontend.conf` — Phase 1's exit gate is a clean boot log.

## Project layout (scaffold-conformant)

```
app/config/       AppConfig, Module
app/controllers/  HomeController, AuthController, DashboardController, EndpointDetailController,
                  ImportController, PdfController(P7); actions/ IdentifierAction+SessionIdentifierAction,
                  IdentifierRequest; testonly/ SeedController
app/handlers/     ErrorHandler (extends bootstrap FrontendErrorHandler)
app/models/       LogEntry, HmrcApplication, ordered Scala 3 enums (LogSeverity, LogEventKind, TaxRegime,
                  HealthTag: Correct<Review<Fix), EndpointDefinition, ItsaEndpointCatalog, viewmodels/, forms/
app/repositories/ ApplicationRepository, LogEntryRepository
app/services/     LogLineParser, LogIngestionService, DashboardService, SampleLogGenerator,
                  SeederService, PdfReportService(P7)
app/views/        Layout (wraps hmrcStandardPage), LandingPage, LoginPage, DashboardPage,
                  EndpointDetailPage, ImportPage, ErrorTemplate, PageNotFound, printable/(P7)
conf/             application.conf, routes, messages.en, testOnlyDoNotUseInAppConf.routes
test/             mirrors app/ (LogLineParserSpec, DashboardServiceSpec, repository integration specs
                  via DefaultPlayMongoRepositorySupport, controller specs)
docs/SPEC.md      this phased spec, copied into the repo
```

## Phases (each verified before the next)

### Phase 1 — Toolchain + bootable skeleton
Install: `winget install EclipseAdoptium.Temurin.21.JDK sbt.sbt MongoDB.Server MongoDB.Shell`; pin `JAVA_HOME` to Temurin 21 (repo `.sbtopts`/README, never machine-default Java 26). Create `C:\code\hmrclog` with build files, application.conf survival kit, routes (home + hmrc-frontend webjar assets), ErrorHandler + GDS error pages, Layout wrapping `hmrcStandardPage`, landing page (heading, capability bullets, green start button), messages.en, `git init`, copy this spec to `docs/SPEC.md`.
**Verify:** `sbt update` resolves (proves resolvers/version alignment — first gate before any code); `sbt run` → landing page on :9250 with GOV.UK header/footer/fonts from webjars; boot log clean (Mongo connected, zero audit/metrics/auth errors); `/nope` → GDS 404.

### Phase 2 — Pure domain: enums, catalog, parser
Ordered enums; `ItsaEndpointCatalog` (~94 entries transcribed from the C#, `tryMatch`, `tryMatchPath` compiling `{param}`→`[^/]+` regex, `findByKey`); `LogEntry`; `LogLineParser` porting all regexes (thread/rid/user/appId/clientId optional), 4 timestamp formats (comma/dot millis, space/T), never-throws (unparseable → parsed=false, rawLine kept), exact classification order and failure rule `status>=400 || "Error response received" || (level>=Warn && errorCode)`.
**Verify:** `sbt test` — all 8 ported LogLineParser tests green.

### Phase 3 — Persistence, ingestion, seeder, test-only reseed
Repositories with 4 managed indexes; `LogIngestionService` port (500/batch `insertMany(ordered=false)`, auto-registration, IngestResult); `SampleLogGenerator` port (distribution-equivalent); `SeederService` (force → wipe+regen, else skip if non-empty); `SeedController` + test-only routes; eager `StartupSeeder` behind `seed-on-startup`.
**Verify:** repository integration specs against local mongod; seed via test-only route; mongosh shows ~10k `log-entries` across 3 apps + 4 indexes.

### Phase 4 — Auth seam + login
`IdentifierAction` trait + `SessionIdentifierAction` (session key, redirect to `/login` with continue); `AuthController` GET/POST with `govukErrorSummary` on bad creds; logout; Guice binding in `config.Module`; `/dashboard` placeholder behind it.
**Verify:** controller specs (redirect unauthenticated / error summary + anchor on bad creds / success redirects to continue); manual login round-trip.

### Phase 5 — Dashboard aggregation + page
`DashboardService.summary` — faithful port of the aggregation rules above; option queries (apps, clients, months newest-first with "Month so far"/"Historic month" labels); `lastApiRequestUtc` ignores month/client filter. `DashboardController.onPageLoad(appId: Option[UUID], month: Option[String], client: Option[String])` with defaults; GET filter form; view: metadata rows, aria-live "Showing N requests", per-area `govukDetails` accordions each holding a `govukTable` (Method | Endpoint link+path | Requests | Success % | Failures | Last called) with red/yellow/green `govukTag`.
**Verify:** `DashboardServiceSpec` — worst-status, 500/200 defaults, 25% boundary, tag ordering; manual: all three tag colours on seeded data, unhealthy endpoints show as designed.

### Phase 6 — Endpoint detail + import
`DashboardService.endpointDetail` (breakdown by (status,errorCode) with Fix/Review labels + %, recent 100 desc); `EndpointDetailController.onPageLoad(appId, endpointKey, month, client)`; view with Issues table + Recent-requests table, each correlation ID a server-rendered `govukDetails` containing that request's raw lines. `ImportController`: GET form (monospace textarea + `govukFileUpload`), POST paste + POST multipart (20MB cap, .log/.txt), GDS error summary, success `govukNotificationBanner` with counts + ≤5 unparsed samples.
**Verify:** service spec for percentages; controller spec uploading fixture .log; manual: paste → banner counts; dashboard → endpoint → expand correlation → raw lines match.

### Phase 7 — PDF export + polish
Add openhtmltopdf-pdfbox; `DashboardService.reportData` port (issues by endpoint, 1000/500 caps with true totals, strict clientId warning matching); `printable/ReportView` strict-XHTML print view; `PdfController` GET `/dashboard/export/pdf?appId&month&client` (400 bad month, 404 unknown app) + export button on dashboard when requests > 0; WCAG 2.2 AA sweep (unique titles, error-summary focus/anchors, table captions+scope, skip link — largely free via hmrcStandardPage); complete messages.en; README (toolchain, run, seed, deviations from MDTP).
**Verify:** full `sbt test`; export PDF for seeded Tax Optimiser prod month, spot-check tables; keyboard-only pass over all pages.

## Top risks

1. **bootstrap-frontend off-platform** — wrong disable keys → boot failures/noisy retries. Mitigation: Phase 1 exit gate is a clean boot log; reconcile keys against bootstrap 10.8.0's `frontend.conf`; contained fallback to plain Play + play-frontend-hmrc.
2. **Java 26** — Scala 3.3/sbt/Pekko unvalidated on it. Mitigation: Temurin 21 pinned via JAVA_HOME/.sbtopts, documented.
3. **HMRC artefact resolution/version alignment** — artefacts live at open.artefacts.tax.service.gov.uk, not Maven Central. Mitigation: resolvers in both build files; cross-check versions against live scaffold; `sbt update` first.
