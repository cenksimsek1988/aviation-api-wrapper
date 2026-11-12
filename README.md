# Aviation API Wrapper

A Spring Boot service that returns airport details for an ICAO code, using the
[aviationweather.gov data API](https://aviationweather.gov/data/api/) (Aviation Weather Center, "AWC") as its data
provider.

Java 17 · Spring Boot 4.1 · Resilience4j · Caffeine · Spring Security · Micrometer/Prometheus · springdoc OpenAPI ·
WireMock

## Run

Requirements: JDK 17 or newer, or Docker. The Maven wrapper downloads Maven itself.

```bash
./mvnw spring-boot:run            # or: docker compose up --build
curl -i http://localhost:8080/api/v1/airports/LIPE
```

| What | Where |
|---|---|
| API | `GET http://localhost:8080/api/v1/airports/{icao}` |
| OpenAPI UI and JSON | http://localhost:8080/swagger-ui.html · http://localhost:8080/v3/api-docs |
| Probes (API port) | http://localhost:8080/livez · http://localhost:8080/readyz |
| Health details, Prometheus metrics, log levels (management port) | http://localhost:8081/actuator/health · `/actuator/prometheus` · `/actuator/loggers` |

Settings are in `src/main/resources/application.properties`, only where they differ from Spring Boot's defaults; an
environment variable overrides any of them (`SPRING_HTTP_SERVICECLIENT_AWC_READ_TIMEOUT=3s`). What a Kubernetes
deployment needs is in [docs/next-steps.md](docs/next-steps.md).

## Tests

```bash
./mvnw verify    # unit and integration tests; no network needed
```

Each behaviour is tested once, at the lowest level that can show it.

| Level | Covers |
|---|---|
| Integration: the whole service over HTTP, WireMock as the provider | the exact JSON and caching headers, the cache, 400, 403, 404, 503 without retry and the pause, a slow and an unreachable provider, a missing required key, the OpenAPI document, health and probes, and that every endpoint names its authority |
| Unit | code and position validation, the cache rules over time, the guard (pauses, retry, budget), sorting the provider's answers, judging its values, `Cache-Control` parsing, the rate-limit share, the logging context |

Test doubles are hand-written and shared (package `testing`): a fake provider and a clock the test moves. Mockito is
excluded: no test needs a mock, and a working fake can test state, failures and time.

## API

`GET /api/v1/airports/{icao}`, case-insensitive:

```json
{
  "icao": "LIPE",
  "iata": "BLQ",
  "name": "BOLOGNA/BORGO PANIGALE AIRPORT",
  "region": "BO",
  "country": "IT",
  "location": { "latitude": 44.5308, "longitude": 11.297, "elevation": { "value": 38, "unit": "m" } },
  "runways": [
    { "designator": "12/30", "length": { "value": 9196, "unit": "ft" }, "width": { "value": 148, "unit": "ft" },
      "surface": "H" }
  ]
}
```

The ICAO code, latitude and longitude are always present and valid; other fields are null when the provider gives
none or an unreadable one, and `runways` is never null. Numbers travel with their unit, as the provider sends them.

| Status | When | Headers |
|---|---|---|
| 200 | Airport found | `Cache-Control: max-age=<provider's>, public`, `Age` |
| 400 | Not four letters A-Z | |
| 403 | The caller lacks the endpoint's authority (for now everyone has it) | |
| 404 | The provider knows no such airport (cached like an airport) | `Cache-Control`, `Age` |
| 500 | A failure of this service itself | |
| 503 | The provider gave no usable answer and nothing usable is cached | `Retry-After` |

Errors are [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) problem details with a `requestId` that matches the
`X-Request-Id` header and the request's log lines; the details stay in the log. Caching follows
[RFC 9111](https://www.rfc-editor.org/rfc/rfc9111): `max-age` is the provider's, unchanged, and `Age` is how long ago
we fetched the data.

## Architecture

```
AirportController             HTTP only: parse the code, map the result and the failures to HTTP; @PreAuthorize
  -> AirportService           cache (Caffeine): every answer kept for the provider's max-age
    -> AirportProvider        the port: findByIcao(IcaoCode) -> ProviderAnswer (found / not found / bad data)
      -> AwcAirportProvider   the adapter: sorts every answer, judges the data (AwcAirportMapper)
        -> ProviderGuard      per provider: circuit breaker per endpoint -> retry -> rate limiter
          -> AwcClient        Spring HTTP interface; base URL, timeouts and User-Agent from properties
```

| Package | Holds |
|---|---|
| `airport` | the domain: `Airport`, `Quantity`, `IcaoCode`, the `AirportProvider` port, `AirportService` (cache) |
| `airport.api` | controller, response type, error handler |
| `provider` | what every provider shares: `ProviderAnswer`, the failure classes, `ProviderGuard`, `ProviderLimit`, `MaxAge` |
| `provider.awc` | the aviationweather.gov adapter: HTTP interface, wire model, mapper, configuration |
| `security` | Spring Security configuration and the authorities |
| `requestid` | the filter that puts the request ID and the session hash into every log line |

## Decisions

| Decision | Why | Considered instead |
|---|---|---|
| The domain owns the `AirportProvider` port; the API has its own response type | Neither the provider nor the domain can change the public JSON by accident; a new endpoint or provider is new code, not a change ([next steps](docs/next-steps.md#extending)) | Serialising the domain records: less code, but the contract would follow the domain |
| A provider failure is temporary (503 with `Retry-After`); only our own failure is 500 | A caller can retry what may recover; a case that cannot be told apart counts as temporary | |
| One circuit breaker per endpoint; one failure pauses it for the provider's period (60 s); the next call after the pause is the test | While the provider struggles, our calls may still count against its limit and get us blocked without any 429 | A failure-rate threshold; a scheduled canary lookup |
| A provider-wide failure (429, 401/403, unreachable, 502-504) pauses all its endpoints | The limit and our access are shared | |
| Retry only a call that never reached the provider: 3 attempts, 200 ms apart; timeouts connect 1 s, read 2 s | A retry cannot count twice against the limit; backoff and jitter protect a struggling server, which these calls never reached | Exponential backoff with jitter |
| Outbound budget: half the provider's 100 calls per 60 s, in fixed blocks of that period | Any window of the provider's touches at most two blocks, so we never exceed 100 in it, however it counts; bursts of 50 pass | Small slices: they reject normal bursts |
| Over the budget, or paused: 503 at once, `Retry-After` = the whole period | A waiting request holds a server thread, and enough of them would stall cache hits | Waiting for room; the exact seconds left (about 25 lines more) |
| Provider answers over 100,000 characters are refused (503) | One airport is under 1 KB, so a bigger answer is broken, and reading it would cost memory | A limit per endpoint: the HTTP clients have none per request |
| Every answer cached for the provider's own `max-age` | Fresh times come from the provider, per answer | A fixed time in our settings |
| A stale airport is served at once and refreshed in the background, for up to 7 days while refreshes fail; a refresh with bad data keeps the good copy | An outage never empties the cache | |
| "Not found" (404) and bad data (503) are cached for `max-age` too | Neither costs a provider call per request | |
| A bad required value (code, latitude, longitude) makes a bad entry; a bad optional value becomes null, with a warning | One bad value never hides a usable airport or pauses the provider | Failing the whole answer |
| Units as value plus unit text (the Quantity pattern), as the provider documents them | A new unit needs no new type; nothing is parsed from text; nothing is converted until a client asks | A unit enum |
| No provider name in the answer | An internal detail that would tie the contract to one provider | |
| Method security per endpoint (`SCOPE_airports:read`); anonymous callers get it for now | The annotations are live and tested; an OAuth2 scope maps to the name with no converter; a login is configuration ([next steps](docs/next-steps.md#adding-authentication)) | No security until a login exists |
| No inbound rate limit; a gateway or WAF in front is assumed | Blocking a client needs its identity or a view of all traffic, and one instance has neither | A per-IP limiter in the service |
| Log lines carry a session hash (HMAC-SHA256 of address and port, random key per start) and the request ID, never a client address | Ties a caller's lines together with a pseudonym, not an address; after a restart the key is gone, so no old value can be linked back; behind a proxy the port is the proxy's, so one client may show several values | The plain address |
| Management port 8081 stays internal (compose binds it to this computer); probes also on 8080 | Health details and runtime log levels stay inside; a probe tests the port that serves traffic | |
| Health shows each circuit breaker, never DOWN because of one | A restart would not bring the provider back, and the cache still answers | |
| Spring HTTP interface (`@HttpExchange`) on the JDK `HttpClient`, set explicitly | Built into Spring; no library on the classpath can swap the client (Apache's would repeat a 429) | WebClient (a reactive stack for one call); OpenFeign (its maintainers recommend HTTP interfaces) |
| Java 17 | The team's level; most requests are cache hits | Java 25 for virtual threads (on Java 21 they pin inside a Caffeine load) |
| Versions: long-term line, else newest stable, each without a known vulnerability (OSV, NVD, GitHub, IntelliJ, 11 Nov 2026): Boot 4.1.2 with four fixed versions raised | The overrides go with Boot 4.2, which manages the same versions | |
| WireMock 4.0.0-beta.41, test only | Every 3.x jar bundles its own vulnerable Jackson, Jetty and Handlebars | WireMock 3 |
| JSpecify `@NullMarked` in every package | Nothing is null unless marked; IntelliJ, NullAway, Kotlin and Spring all read it, and the JDK has no standard | Lombok's or JSR 305's `@NonNull` |
| Records for data, with record-style accessor names everywhere | One naming style, no generated code | Lombok |
| `application.properties`, only non-default values | One key per line: easy to compare and script | YAML |

**How a provider answer is sorted:**

| Provider answer | Class | Scope | Retried | Effect |
|---|---|---|---|---|
| Connection refused, DNS failure, connect timeout | `ProviderUnreachableException` | provider | yes, twice | then all endpoints paused |
| 429, 502, 503, 504 | `ProviderUnavailableException` | provider | no | all endpoints paused |
| 401, 403 | `ProviderBrokenException` | provider | no | all endpoints paused, ERROR |
| Read timeout, other I/O failure, 500 and other 5xx | `ProviderUnavailableException` | endpoint | no | endpoint paused |
| 404, other 4xx, other 2xx/3xx, unreadable body, a required JSON key missing | `ProviderBrokenException` | endpoint | no | endpoint paused, ERROR |
| A required value bad, or not the airport asked for | `ProviderAnswer.Bad` | | no | 503 for the provider's max-age; never pauses |
| An optional value unreadable (elevation, runway size...) | | | | that field null, WARN |
| 204 or an empty array | `ProviderAnswer.NotFound` | | | 404 for the provider's max-age |

**Also:** codes are checked before any call (four letters, so the provider's list and wildcard syntax cannot be
injected) and upper-cased with `Locale.ROOT`; a caller's `X-Request-Id` is kept only as 1-64 safe characters; errors
never carry stack traces or provider URLs; CSRF protection is off, as there are no cookies or sessions (it comes back
with a cookie-based login); the container runs as a non-root user. Log levels: ERROR when a person must act (a stack
trace only for our own bugs), WARN once per pause, DEBUG for each retry or turned-away call, so an outage writes one
line per pause. Metrics: `http_server_requests`, `http_client_requests`, `resilience4j_*`, `cache_*{cache="airports"}`.

## Assumptions

- An ICAO airport code is four letters A-Z, as ICAO Doc 7910 defines a location indicator.
- Units follow the provider's documentation: `elev` is metres, a runway `dimension` is feet (LIPE: 38 m and 9,196 ft,
  matching Bologna's published values).
- We ask for one code per call; the provider has no single-airport path and always answers an array. If it sends
  several airports, the one asked for is used, with a warning.
- The provider's OpenAPI document declares latitude, longitude and elevation as strings, but it sends numbers; both
  are read, with the digits as sent.
- Names are returned as the provider spells them, trimmed; "", "-" and blanks mean "none". The runway surface is the
  provider's code (H hard, C concrete, A asphalt).
- A gateway or WAF in front authenticates callers and protects against floods.

## Roadmap

In order of value; each is ready to start, and the first three are written out in [next steps](docs/next-steps.md).

| Next | Why not now |
|---|---|
| Authentication: OAuth2 tokens or mutual TLS | The assignment needs no user management; the authorities are in place |
| Kubernetes deployment | Not built, as it could not be tested without a cluster; the service is ready for it (probes on the API port, non-root image, settings from the environment) |
| Shared budget and cache in Redis, for many instances | One instance, or a few with the limit divided, is safe today |
| The provider's own `Retry-After` on a 429 | Today we pause for its full period instead |
| Tracing (Micrometer Tracing with OpenTelemetry), JSON logs, a CI pipeline | The assignment asks for basic observability |
| A unit option for clients (`?lengthUnit=m`) | No client asks yet; the JSON shape already allows it |
| An audit store of session hashes with client addresses | Needs a stated retention period and access rules |
| Virtual threads on Java 25 | The team's Java level; little gain while most requests hit the cache |
| A config service (Spring Cloud Config, or ConfigMaps managed per environment) | Pays off when several services share settings; every setting is already a property that the environment overrides |

## AI-assisted development

In a first session with Claude (Anthropic) I chose the tech stack, and Claude built a working starting project from
it. I then reviewed that code part by part, as a design review: Claude laid out problems, options and trade-offs; I
made the design decisions (about 45, such as the failure classes and their scopes, one circuit per endpoint, half the
provider's limit, units as value plus unit, caching every answer for the provider's max-age); Claude implemented them,
running the full test suite and IntelliJ's inspections after each batch, and the commit went through a structured
review of code, design, documentation and security. I reviewed and approved the result.
