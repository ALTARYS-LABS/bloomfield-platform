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
8. [When you still legitimately need `@Transactional` on a listener](#8-when-you-still-need-transactional)
9. [Cheat sheet](#9-cheat-sheet)

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

## 8. When you still legitimately need `@Transactional`

Plain `@EventListener` (not `@TransactionalEventListener`) has no transactional semantics of its own. If you want the listener body to run in a TX, you add `@Transactional` explicitly. That's what `ReconnectHandler.onSessionSubscribe` does, and it is correct:

```java
@EventListener
@Transactional
void onSessionSubscribe(SessionSubscribeEvent event) { ... }
```

Here the listener runs synchronously on the event-publishing thread (STOMP subscribe), and the `@Transactional` annotation actively provides the transaction — there's no meta-annotation doing it for you. Spring 7's restriction does **not** apply, because `RestrictedTransactionalEventListenerFactory` only inspects `@TransactionalEventListener`-backed methods.

**Heuristic**: if the annotation on the method name contains the word "Transactional" (e.g. `@TransactionalEventListener`, `@ApplicationModuleListener` which composes it) — don't add your own `@Transactional`. Otherwise — you probably need to.

---

## 9. Cheat sheet

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
