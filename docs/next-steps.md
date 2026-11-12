# Next steps: extending, Kubernetes, scaling out, authentication

What is ready but not switched on, and how to take each step. The decisions behind them are in the
[README](../README.md#decisions).

## Extending

**A new endpoint** (for example METAR) is new code next to the old, never a change to it: a domain record, a port
method, a wire record and mapper in the provider's package, one more method on its HTTP interface, a response type and
controller with its `@PreAuthorize` authority (`AuthorizationIT` fails without one), and its cache. Its settings go in
`application.properties`, and `ProviderGuard` gives it its own circuit breaker (`awc-metar`) with no further code.
Weather data needs short fresh times (the provider sends `max-age=60` for METARs) and little or no stale serving: an
old observation shown as current is a safety issue, so it must carry `Age` and a short stale limit.

**A new provider** is one more adapter package with its own `ProviderGuard`, limit and HTTP service group.

## Deploying to Kubernetes

Not built: there are no manifests yet, because none could be tested without a cluster. The service is ready for it:
probes on the API port, a non-root image with a numeric user (10001), and every setting overridable by an environment
variable. A deployment needs:

- A Deployment whose liveness and readiness probes use `/livez` and `/readyz` on 8080, with `runAsNonRoot`, a
  read-only file system (and a writable `/tmp`), and Prometheus scrape annotations for 8081
  (`/actuator/prometheus`).
- A Service that publishes 8080 only.
- A NetworkPolicy that lets only Prometheus reach 8081: it serves health details, metrics, and log levels that can be
  changed at runtime.
- The provider's limit divided by the replicas (`PROVIDER_AWC_RATE_LIMIT_CALLS=50` for 2), because each replica keeps
  its own budget; change both together, or share the budget (next section).

## Scaling out

The cache, the circuit breakers and the request budget live in each instance's memory. That is correct for one
instance and safe for a few (with the limit divided as above). Beyond that, share them in Redis; `compose.yaml` names
the Redis container, commented out, as the start:

- **Request budget:** no new rate-limit library is needed. Our limiter already counts in fixed blocks of the
  provider's period, so a shared budget is one Redis counter per block: `INCR awc:budget:<block number>`, with
  `EXPIRE` of one period, and a call proceeds while the count is at most half the limit. Resilience4j's limiter has
  no shared backend; Bucket4j with Redis is the ready-made alternative.
- **Cache:** a shared cache (Redis) in front of the per-instance Caffeine, so one instance's provider answer serves
  all of them.
- **Circuit breakers** can stay per instance: each learns of an outage from its own first failed call.

## Adding authentication

Ready, not switched on: every API endpoint already names its authority, and the names match OAuth2 scopes.

**OAuth2 bearer tokens (JWT)**, from any OpenID Connect provider (Keycloak, Okta, Entra ID):

1. Add `spring-boot-starter-oauth2-resource-server` to the pom.
2. Set `spring.security.oauth2.resourceserver.jwt.issuer-uri` to the provider's issuer URL.
3. Set `spring.security.oauth2.resourceserver.jwt.audiences` to this API's audience, so a token the same issuer
   made for another API is refused.
4. In `SecurityConfig`, add `.oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))`.
5. Set `api.security.anonymous-authorities` to an authority no endpoint asks for (for example `ROLE_ANONYMOUS`), so
   only a token with the scope `airports:read` can read.

With a login, a caller without a token should get 401 with `WWW-Authenticate: Bearer`, not 403:
`AirportApiExceptionHandler` answers `AccessDeniedException` with 403, so let it rethrow when the caller is anonymous.

**Mutual TLS** (service-to-service): usually at the gateway or service mesh, which checks the client certificate and
forwards the call; nothing changes here. To end TLS in the service itself instead: an SSL bundle with a trust store
(`spring.ssl.bundle.jks.<name>.*`), `server.ssl.bundle=<name>`, `server.ssl.client-auth=need`, and in `SecurityConfig`
`.x509(Customizer.withDefaults())` with a mapping from certificate subject to authorities.
