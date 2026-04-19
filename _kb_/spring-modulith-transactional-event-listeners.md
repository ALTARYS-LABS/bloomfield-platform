# Spring Modulith `@ApplicationModuleListener` — Why You Don't Add `@Transactional` On Top

> Audience: you (Emmanuel), building Bloomfield Terminal.
> Goal: after reading this, you understand exactly what `@ApplicationModuleListener` does under the hood, why stacking `@Transactional` on top is wrong, and why Spring 7 now fails fast instead of silently accepting it.
> Style: plain language first, then meta-annotations, then the failure mode.

---

## Table of contents

1. [The one-line answer](#1-the-one-line-answer)
2. [What `@ApplicationModuleListener` actually is](#2-what-applicationmodulelistener-actually-is)
3. [What `@TransactionalEventListener` means (and why it matters here)](#3-what-transactionaleventlistener-means)
4. [The three propagation modes that show up in this story](#4-the-three-propagation-modes)
5. [Why stacking plain `@Transactional` is a bug, not a style issue](#5-why-stacking-plain-transactional-is-a-bug)
6. [Why Spring 7 throws at startup instead of booting](#6-why-spring-7-throws-at-startup)
7. [The fix pattern in Bloomfield](#7-the-fix-pattern)
8. [The `event_publication` table - where Modulith remembers events](#8-the-event_publication-table)
9. [Publishing custom business events - a checklist for the team](#9-publishing-custom-business-events)
10. [Going further on events in Spring Modulith](#10-going-further-on-events)
11. [When you still legitimately need `@Transactional` on a listener](#11-when-you-still-need-transactional)
12. [Cheat sheet](#12-cheat-sheet)

---

## 1. The one-line answer

`@ApplicationModuleListener` is already a transactional event listener that runs its method inside a brand-new transaction. Adding `@Transactional` on top is redundant at best and, under Spring Framework 7, illegal — the container refuses to start.

That's why `AlertEngine.onQuoteTick` no longer carries `@Transactional`. Nothing about the runtime behavior changed; we just stopped lying to Spring about what transaction we want.

---

## 2. What `@ApplicationModuleListener` actually is

`@ApplicationModuleListener` is not a primitive. It is a **composed annotation** — a single Spring Modulith annotation that bundles three separate ones. Peek at the source and you'll see (simplified):

```java
@Target(METHOD)
@Retention(RUNTIME)
@Async
@TransactionalEventListener(fallbackExecution = true)
@Transactional(propagation = REQUIRES_NEW)
public @interface ApplicationModuleListener { }
```

Three things happen on every invocation, in order:

1. **`@TransactionalEventListener`** — the method only runs after the *publisher's* transaction has committed successfully. If the publisher rolls back, the listener is skipped. This is the core of decoupled intra-modulith communication: when the portfolio module publishes `QuoteTick`, the alerts listener fires only if that publish was durably committed.
2. **`@Transactional(propagation = REQUIRES_NEW)`** — the listener starts its **own**, fresh transaction. It is not joined to the publisher's, because the publisher's is already closed by the time `AFTER_COMMIT` runs.
3. **`@Async`** — the method is dispatched on a task executor, off the publisher's thread.

So by the time `onQuoteTick(QuoteTick tick)` starts executing, you are:

- on a worker thread (not the HTTP thread that triggered the tick)
- inside a brand-new, Spring-managed transaction
- guaranteed that the event you're handling represents committed state

That is exactly what we want for an alert engine: the price publication has to have really happened before we start firing alerts based on it.

---

## 3. What `@TransactionalEventListener` means

Plain old `@EventListener` fires synchronously, on the caller's thread, inside the caller's transaction. If the caller rolls back, the listener already ran — its side effects leak.

`@TransactionalEventListener` is the fix: the method is registered as a listener but deferred to a specific **transaction phase** (default: `AFTER_COMMIT`). Spring intercepts the publish and parks the invocation until the ambient transaction hits that phase.

Key corollary for propagation:

> By the time `AFTER_COMMIT` fires, the publisher's transaction is **already committed and closed**.

So if your listener method is itself `@Transactional` with the default propagation (`REQUIRED`), there is no outer transaction to join. Spring must either (a) start a new one, (b) run without one, or (c) complain. Which brings us to propagation.

---

## 4. The three propagation modes

Only three matter for this conversation:

| Propagation      | Behavior on entry                                                                 |
| ---------------- | --------------------------------------------------------------------------------- |
| `REQUIRED` (default) | Join the caller's TX. If none exists, start one. **This is the problematic one for listeners.** |
| `REQUIRES_NEW`   | Suspend the caller's TX (if any) and **always start a new one**.                  |
| `NOT_SUPPORTED`  | Suspend the caller's TX (if any) and run **without** a transaction.               |

For an `AFTER_COMMIT` listener there is no caller TX to join. So `REQUIRED` degenerates into "start a new one implicitly" — which works, but is sloppy and hides intent. The two **honest** choices are `REQUIRES_NEW` ("yes, I want my own TX") or `NOT_SUPPORTED` ("I don't want any TX at all"). Spring 7 now enforces that honesty (see §6).

`@ApplicationModuleListener` picks `REQUIRES_NEW` for you, because 99% of the time you *do* want a new transaction around the listener body (so its repository writes are durable, and so other listeners on the same event get independent TX boundaries).

---

## 5. Why stacking plain `@Transactional` is a bug

Recall the method as it was on `main` before this PR:

```java
@ApplicationModuleListener        // implies @Transactional(REQUIRES_NEW)
@Transactional                    // REQUIRED (default)  — redundant + conflicting
void onQuoteTick(QuoteTick tick) { ... }
```

Two annotations, two different propagations. Spring has a deterministic merge rule for meta-annotations, so this doesn't *actually* double-wrap the method — but it does force the framework to reconcile contradictory intent on every boot. Three concrete problems:

1. **Intent lies.** A reader sees `@Transactional` (default `REQUIRED`) and assumes the method joins the caller's TX. It doesn't and can't — it's an `AFTER_COMMIT` listener. The annotation is actively misleading.
2. **No behavioral benefit.** Removing `@Transactional` leaves `REQUIRES_NEW` in place via the meta-annotation. The runtime behavior is identical.
3. **Framework-version fragility.** As of Spring 7, this pattern fails startup (see next section). So the "redundant but harmless" style note has become a context-load bug.

---

## 6. Why Spring 7 throws at startup

Spring Framework 7 introduced `RestrictedTransactionalEventListenerFactory`. Its job at startup: look at every `@TransactionalEventListener` method and make sure its transactional annotations are coherent with being an `AFTER_COMMIT` listener. The rule, paraphrased from the exception message:

> A `@TransactionalEventListener` method must not be annotated with `@Transactional` unless it declares `REQUIRES_NEW` or `NOT_SUPPORTED`.

Translation: you can keep `@Transactional` on a listener, but only if you **explicitly** pick a propagation that makes sense with "there is no outer TX". Default `REQUIRED` is now a startup failure.

The failure comes out of the factory as:

```
BeanInitializationException → IllegalStateException
  at RestrictedTransactionalEventListenerFactory.createApplicationListener(line 52)
```

…which bubbles up as a `BeanInitializationException` in `EventListenerMethodProcessor.afterSingletonsInstantiated`. That's what blew up every `@SpringBootTest` integration test in our suite before the fix: the application context couldn't be built at all, so even tests that didn't touch the alerts module failed with "ApplicationContext failure threshold exceeded".

### Why did this regression land on `main`?

Two failures compounded:

1. Spring Boot 4 / Spring 7 upgrade tightened this check. Our code wasn't adjusted.
2. `.github/workflows/ci.yml` only triggered on `main` and `feature/**`. Our branches follow `feat/...` and target `develop`, so **CI literally never ran** on the PRs that introduced the module. The duplicate `V003` migration slipped in the same way. This is the second half of the same fix PR.

---

## 7. The fix pattern

Three equivalent fixes, from most to least idiomatic:

### (a) Remove the redundant annotation (what we did)

```java
@ApplicationModuleListener
void onQuoteTick(QuoteTick tick) { ... }
```

Rely on the meta-annotation's `REQUIRES_NEW`. Cleanest. Zero intent lies.

### (b) Be explicit if you really want to document it

```java
@ApplicationModuleListener
@Transactional(propagation = Propagation.REQUIRES_NEW)
void onQuoteTick(QuoteTick tick) { ... }
```

Legal under Spring 7 (matches the meta-annotation). Redundant but not wrong. Choose this only if you have a team convention that says "always declare propagation at the point of use".

### (c) Opt out of the TX entirely

```java
@ApplicationModuleListener
@Transactional(propagation = Propagation.NOT_SUPPORTED)
void onQuoteTick(QuoteTick tick) { ... }
```

Also legal. Only pick this if the listener body genuinely does no persistence — e.g. it only sends a WebSocket message. Our listener writes to `alert_events` and updates `alert_rules`, so it must stay transactional.

We took option (a) because `@ApplicationModuleListener` already says what we want.

---

## 8. The `event_publication` table

> **Why this section exists**: on 2026-04-19 the staging site went half-dark. Indices and portfolio streams worked, but the quotes table stayed empty and the order form ticker dropdown was `--`. Backend logs showed `ERROR: relation "event_publication" does not exist` repeating every second inside `SimulatedMarketDataProvider.publishQuotes`. Diagnosing it took longer than fixing it. This section is so nobody on the team has to rediscover what happened.

### What the table is for

Spring Modulith does not trust in-memory event delivery. Every time you call `ApplicationEventPublisher.publishEvent(someEvent)` **and** at least one listener is a `@TransactionalEventListener` (which includes all `@ApplicationModuleListener` methods), Modulith persists a row in a table called `event_publication` **as part of the publisher's transaction**. The listener runs later, in a separate transaction, and when it finishes successfully Modulith updates that row with a `completion_date`.

Schema (PostgreSQL, Modulith v2):

```
event_publication(
  id UUID PRIMARY KEY,
  listener_id TEXT,            -- fully-qualified method signature
  event_type TEXT,             -- fully-qualified class of the event
  serialized_event TEXT,       -- JSON payload of the event
  publication_date TIMESTAMPTZ,
  completion_date TIMESTAMPTZ, -- NULL until the listener succeeds
  status TEXT,
  completion_attempts INT,
  last_resubmission_date TIMESTAMPTZ
)
```

### What it guarantees

1. **"At-least-once" listener execution across restarts.** If the app crashes between "publisher commits" and "listener finishes", the event row is still there with `completion_date = NULL`. On the next startup (or a manual resubmit call), Modulith can replay it. This is the whole reason the table exists: without it, events lost on restart are gone forever.
2. **Audit trail of cross-module communication.** You can SQL your way through `event_publication` to see exactly which events were published, by whom, consumed by which listener, and whether they completed. Extremely useful when debugging a module that "didn't react" to something.
3. **The publisher's transaction can still roll back cleanly.** Because the event row is written in the publisher's TX, a rollback deletes the event too. No phantom events ever escape a failed unit of work.

### Why we created V005 manually

Modulith ships a schema auto-initialiser (activated by `spring.modulith.events.jdbc.schema-initialization.enabled=true`) that creates the table on first startup. We deliberately do **not** use it. Instead we own the DDL via Flyway (`V005__spring_modulith_event_publication.sql`). Reasons:

- **Flyway is the single source of truth for schema changes.** Two schema managers racing against one Postgres instance is an easy way to end up with drift between local, staging, and prod.
- **Reproducibility.** A test-container started for an integration test gets the same table via the same migration as staging. No surprises.
- **The staging incident would have been caught at migrate time.** Flyway fails fast on missing migrations. Auto-init failed silently because our staging volume was older than the alerts module and nothing ever retried the init.

### When you will touch this table directly

Almost never. But you should know these three knobs:

| Column | What to check when debugging |
| --- | --- |
| `completion_date IS NULL AND publication_date < now() - interval '1 minute'` | Listeners that are stuck or failing. If this count grows, something in a listener is throwing. |
| `event_type` | Group-by to see which events are flowing. Good smoke test after changes. |
| `completion_attempts > 1` | Listener is being retried - inspect the listener log for exceptions. |

If you find yourself writing migrations to alter this table, stop. The DDL is owned by Spring Modulith; we only ever re-create it from the official jar resource. If a newer Modulith version ships v3 schema, bump Bloomfield's migration with the new file, verbatim.

---

## 9. Publishing custom business events

When a junior dev adds a new cross-module event (portfolio -> alerts, user -> audit, etc.), these are the things that go wrong in production and not in tests. Tests use fresh TestContainers, so they mask every one of these.

### The checklist

1. **Pick the right publisher.** Inject `ApplicationEventPublisher` and call `publishEvent(new MyEvent(...))`. Do not invent your own queue.
2. **Events are records.** No behaviour, no mutable fields. A plain `public record QuoteTick(String ticker, BigDecimal price, Instant at) {}`. Records serialize to JSON cleanly, which is what the `serialized_event` column wants.
3. **Events describe facts that already happened.** Past tense: `QuoteTick`, `PositionOpened`, `AlertTriggered`. Not imperatives (`CreatePosition`) - that is a command, not an event. Commands are method calls; events are records of completed work.
4. **Publish inside a transaction, from inside the module that owns the fact.** The alerts module listens to `QuoteTick` but does not publish it; the market-data module does. Cross this wire and Modulith's compile-time checks (`ApplicationModules.verify()`) will flag the dependency cycle.
5. **Listener lives in the consuming module's `internal` package.** Never expose a listener as part of a module's `api`. The listener is an implementation detail; other modules should not know about it.
6. **Listener methods return `void` and take exactly one argument: the event.** Anything else, Modulith will ignore silently.
7. **If you need to publish an event while handling another, do it from a `@Transactional` method.** Events published outside any transaction skip the `event_publication` table entirely, which means they also skip the at-least-once guarantee. This is a very easy mistake to make from a scheduled method like `publishQuotes` that calls `eventPublisher.publishEvent` on every tick - it works in dev, but a listener failure on startup loses every event already fired.
8. **Never put slow I/O directly in a listener.** Each listener has its own transaction. A listener making an HTTP call ties up a DB connection for the duration. If the external service is slow, you starve the pool. For I/O, either (a) hand off to `@Async` inside the listener, or (b) make the listener just write to a work-queue table and let a separate `@Scheduled` drain it.
9. **Do not publish events inside tight loops without thinking about the table size.** `publishQuotes` currently publishes 45 events every 1-2 seconds, which is already ~2 million rows per day in `event_publication` if listeners never complete. For high-frequency domains (ticks, telemetry), consider batching events (`QuoteTickBatch(List<QuoteTick>)`) or skipping persistence by using plain `@EventListener` for the hot path and keeping `@ApplicationModuleListener` for the low-volume, high-value transitions.
10. **Verify the listener name in the event table.** `listener_id` is the method signature. If you rename a listener method, old unresolved rows will never be picked up (their `listener_id` points at a method that no longer exists). This is a migration-grade change - handle it like a rename of a class on disk: bulk-update `listener_id` in a Flyway migration at the same time you rename the method.

### The three failure modes you will actually hit

- **Listener throws.** Row stays with `completion_date = NULL`. Table grows. You see it in shutdown logs as "Unresolved publications" or at runtime via `EventPublicationRegistry`.
- **Publisher rolls back.** Event row is rolled back too. Listener never runs. This is correct behaviour - the fact did not happen, so there is no event.
- **Listener commits but the resource it called rolled back.** Example: listener sends a STOMP message successfully, then tries to write to the DB and the DB write fails. Listener TX rolls back -> `completion_date` stays NULL -> Modulith retries next startup -> STOMP message sent twice. Protect against this by doing the non-transactional side effect (STOMP send, HTTP call, file write) **last** in the listener, so a TX failure short-circuits before the side effect.

---

## 10. Going further on events

If the above has you curious, these are the five next things worth knowing.

### a) Resubmitting unresolved events on startup

Modulith exposes a bean `IncompleteEventPublications` (or `EventPublicationRepository`). You can wire an `ApplicationListener<ApplicationStartedEvent>` that calls `.resubmitIncompletePublications(...)` to replay everything the app missed while it was down. We do not do this yet in Bloomfield - the prototype accepts at-most-once delivery for quotes. If you build something that must not lose events (order placements, payments), wire this in.

### b) Completion mode `@ApplicationModuleListener(completion = ...)`

The default is `UPDATE` - rows are kept with `completion_date` set as an audit trail. Other values of `spring.modulith.events.completion-mode` are `DELETE` (drop the row on success) and `ARCHIVE` (move it to a separate archive table). For noisy domains you almost certainly want `DELETE` and to scale with live data, not audit logs.

**In Bloomfield we set `DELETE`** (see `application.yml`). Rationale: the `QuoteTick` cadence is roughly one publication per ticker per second. Keeping every completed row would grow `event_publication` by hundreds of MB per day for nothing. With `DELETE`, the table only ever contains rows whose listener has not finished yet - which doubles as a live "failure queue" you can query with `SELECT event_type, listener_id, publication_date FROM event_publication;`. If the table is empty, all listeners are caught up; if it's growing, something is throwing.

The tradeoff we accept: no audit trail of past events in the DB. For order placements, payments or anything with a regulatory requirement, override locally on the listener with `@ApplicationModuleListener(completion = REGISTER_ON_COMPLETION)` - that wins over the global setting per-listener.

### c) Externalising events to a real broker

`@Externalized` on an event record tells Modulith to push it to Kafka / RabbitMQ / AMQP after commit, in addition to calling in-process listeners. This is how you graduate from a modulith to a distributed system without rewriting the publisher side. Worth knowing it exists even if we don't use it today - it means our choice of in-process events is not a dead end.

### d) `ApplicationModules.verify()` as a test

Add one test per module graph:

```java
@Test
void modulesAreCoherent() {
  ApplicationModules.of(BloomfieldApplication.class).verify();
}
```

This fails the build if any module depends on another module's internal package, if there is a cycle, or if a listener is unreachable. Cheaper than a code review.

### e) The documentation generator

`ApplicationModules.of(...).createCanvas().writeDocumentation(...)` generates a directory of AsciiDoc/PlantUML describing every module, its allowed dependencies, and its events. Useful for onboarding a new hire - one command produces an up-to-date architecture diagram from the actual code.

### Further reading

- Spring Modulith reference, section "Working with Application Events": https://docs.spring.io/spring-modulith/reference/events.html
- Oliver Drotbohm's talk "Spring Modulith: From a monolith to a modulith" on YouTube - 40 minutes, the best high-level overview of why this library exists.
- Source of truth for the DDL we vendor: inside `spring-modulith-events-jdbc-2.0.0.jar`, path `org/springframework/modulith/events/jdbc/schemas/v2/schema-postgresql.sql`.

---

## 11. When you still legitimately need `@Transactional`

Plain `@EventListener` (not `@TransactionalEventListener`) has no transactional semantics of its own. If you want the listener body to run in a TX, you add `@Transactional` explicitly. That's what `ReconnectHandler.onSessionSubscribe` does, and it is correct:

```java
@EventListener
@Transactional
void onSessionSubscribe(SessionSubscribeEvent event) { ... }
```

Here the listener runs synchronously on the event-publishing thread (STOMP subscribe), and the `@Transactional` annotation actively provides the transaction — there's no meta-annotation doing it for you. Spring 7's restriction does **not** apply, because `RestrictedTransactionalEventListenerFactory` only inspects `@TransactionalEventListener`-backed methods.

**Heuristic**: if the annotation on the method name contains the word "Transactional" (e.g. `@TransactionalEventListener`, `@ApplicationModuleListener` which composes it) — don't add your own `@Transactional`. Otherwise — you probably need to.

---

## 12. Cheat sheet

| You want…                                                                 | Use…                                                      | Extra `@Transactional`? |
| ------------------------------------------------------------------------- | --------------------------------------------------------- | ----------------------- |
| Decoupled intra-modulith event, runs after publisher commits, own TX      | `@ApplicationModuleListener`                              | **No**                  |
| Same as above, but you really want to document propagation at call site   | `@ApplicationModuleListener` + `@Transactional(REQUIRES_NEW)` | Optional, explicit only |
| Decoupled event, after commit, **no** TX needed (pure push/notification)  | `@ApplicationModuleListener` + `@Transactional(NOT_SUPPORTED)` | Yes, explicit only      |
| Synchronous reaction on the publisher's thread, inside the publisher's TX | `@EventListener`                                          | No (joins caller's)     |
| Synchronous reaction, own TX required (e.g. STOMP subscribe handler)      | `@EventListener` + `@Transactional`                       | **Yes**                 |

### Quick mental model

- `@ApplicationModuleListener` = "async + after-commit + new TX". Complete package. Don't add to it.
- Plain `@EventListener` = "synchronous, nothing else". Add what you need.
- Spring 7's startup check: if the method is a `@TransactionalEventListener` **and** has a plain `@Transactional` without `REQUIRES_NEW` / `NOT_SUPPORTED`, boot fails. Fix by deleting the extra annotation or making propagation explicit.
