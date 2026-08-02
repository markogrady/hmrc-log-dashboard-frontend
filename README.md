# hmrc-log-dashboard-frontend

An MDTP-style HMRC frontend microservice (Scala 3 / Play 3.0 / play-frontend-hmrc / hmrc-mongo)
that simulates HMRC's side of the API platform: it ingests log lines in the hmrc/vat-api
logback format (extended with `appId=[…]` and `clientId=[…]` MDC fields) and shows a
Developer-Hub-style dashboard of MTD ITSA endpoint usage per application, client and month.

This is a rewrite of a .NET Blazor app to HMRC standards, modelled on
[hmrc-frontend-scaffold.g8](https://github.com/hmrc/hmrc-frontend-scaffold.g8) and
real MDTP services. The phased specification is in [docs/SPEC.md](docs/SPEC.md).

**It is not an HMRC project** and is not affiliated with or endorsed by HMRC. It
simulates the HMRC side of the API platform for local development and demo
purposes; all data is synthetic and generated on first run. There is no
connection to any real HMRC system or taxpayer data.

## Prerequisites

- Temurin JDK 21 (Java 26 is not validated for Scala 3.3 / sbt / Pekko — pin `JAVA_HOME` to 21)
- sbt 1.10.x
- MongoDB Community Server running locally on `localhost:27017`

```powershell
winget install EclipseAdoptium.Temurin.21.JDK sbt.sbt MongoDB.Server MongoDB.Shell
```

Every sbt session must use JDK 21 — use the wrapper, which pins it:

```powershell
.\sbtw.ps1 run   # http://localhost:9250
```

(Equivalent to setting `JAVA_HOME` to the Temurin 21 install and running `sbt run`.)

## Run

```powershell
.\sbtw.ps1 run   # http://localhost:9250
```

On first boot with an empty database the service seeds ~10,000 deterministic sample
log lines across 3 applications (`seed-on-startup = true` in application.conf).

Sign in: `dev@hmrclogger.local` / `LetMeIn-2026!`

> These are **demo credentials for a local-only simulator** — they are published
> deliberately, guard nothing but synthetic generated data, and are not used
> anywhere else. Override them with the `HMRCLOG_LOGIN_USERNAME` and
> `HMRCLOG_LOGIN_PASSWORD` environment variables for any non-local run.

### Reseed (test-only routes, MDTP idiom)

```powershell
sbt "run -Dapplication.router=testOnlyDoNotUseInAppConf.Routes"
curl -X POST "http://localhost:9250/test-only/seed?force=true"
```

## Test

```powershell
sbt test         # repository integration specs need local mongod
```

## Deviations from MDTP

Documented in docs/SPEC.md — chiefly: runs off-platform (auditing/metrics/allowlist
disabled), session-cookie auth behind the scaffold `IdentifierAction` seam instead of
auth-client, and HMRC sbt plugins omitted.
