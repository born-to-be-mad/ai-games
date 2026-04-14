package com.aiarchitect.terraquery

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._

/**
 * Spike load simulation — models a sudden surge of users (e.g. breaking-news disaster event).
 *
 * Profile:
 *   - Baseline: 2 users/s for 60s
 *   - Spike:    ramp to 20 users/s over 30s, hold for 60s
 *   - Recovery: ramp back to 2 users/s over 30s
 *
 * Assertions (relaxed vs baseline — latency degrades under spike):
 *   - Simple p95 ≤ 20s (baseline 12s)
 *   - Error rate ≤ 10% (baseline 5%)
 *
 * Usage:
 *   ./gradlew :terra-infrastructure:gatlingRun \
 *     --simulation com.aiarchitect.terraquery.TerraQuerySpikeSimulation \
 *     -DTERRA_QUERY_BASE_URL=http://localhost:8080
 */
class TerraQuerySpikeSimulation extends Simulation {

  val baseUrl: String = System.getProperty("TERRA_QUERY_BASE_URL", "http://localhost:8080")

  val httpProtocol = http
    .baseUrl(baseUrl)
    .contentTypeHeader("application/json")
    .acceptHeader("application/json")
    .shareConnections

  // ── Feeders ───────────────────────────────────────────────────────────────

  val spikeQueries = List(
    """{"message": "How many earthquakes occurred in Japan in 2011?"}""",
    """{"message": "What was the deadliest flood in Bangladesh?"}""",
    """{"message": "How many hurricanes hit the United States in 2005?"}""",
    """{"message": "What are the most common disaster types in India?"}""",
    """{"message": "How many people died in the 2010 Haiti earthquake?"}""",
    """{"message": "List recent wildfire events in California."}""",
    """{"message": "What is the current status of active volcanoes?"}"""
  )

  val queryFeeder = spikeQueries.map(q => Map("body" -> q)).circular

  // ── Scenario ──────────────────────────────────────────────────────────────

  val spikeScenario = scenario("Spike Load")
    .feed(queryFeeder)
    .exec(
      http("POST /api/v1/chat — spike")
        .post("/api/v1/chat")
        .body(StringBody("#{body}"))
        .check(
          status.in(200, 429, 503),   // 429 = rate-limited (acceptable under spike), 503 = degraded
          responseTimeInMillis.lte(30000)
        )
    )

  val healthScenario = scenario("Health Probe")
    .exec(
      http("GET /actuator/health — spike")
        .get("/actuator/health")
        .check(status.is(200))
    )

  // ── Spike load profile ────────────────────────────────────────────────────
  //
  //  Users/s  │
  //    20  ───│──────────────────────────
  //           │         ╱             ╲
  //           │        ╱               ╲
  //     2  ───│───────╱                 ╲───────────
  //           └────────────────────────────────────── time
  //             60s ramp   30s   60s spike  30s
  //
  setUp(
    healthScenario.inject(
      constantUsersPerSec(0.1).during(180.seconds)
    ),
    spikeScenario.inject(
      constantUsersPerSec(2).during(60.seconds),         // baseline
      rampUsersPerSec(2).to(20).during(30.seconds),     // ramp up
      constantUsersPerSec(20).during(60.seconds),        // spike
      rampUsersPerSec(20).to(2).during(30.seconds)      // recovery
    )
  ).protocols(httpProtocol)
    .assertions(
      // Under spike: allow p95 ≤ 20s (vs 12s baseline)
      details("POST /api/v1/chat — spike").responseTime.percentile(95).lte(20000),
      // Error rate ≤ 10% — rate limiter may fire, which is correct behaviour
      details("POST /api/v1/chat — spike").failedRequests.percent.lte(10.0),
      // Health endpoint must stay green
      details("GET /actuator/health — spike").failedRequests.percent.is(0)
    )
}
