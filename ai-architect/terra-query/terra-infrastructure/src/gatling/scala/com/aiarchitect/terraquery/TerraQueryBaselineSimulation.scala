package com.aiarchitect.terraquery

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._

/**
 * Gatling baseline performance simulation for TerraQuery.
 *
 * Targets:
 *   - Simple queries:  p50 ≤ 3s,   p95 ≤ 12s
 *   - Complex queries: p50 ≤ 8s,   p95 ≤ 20s
 *   - MCP tool calls:  p95 ≤ 200ms (measured separately via terra-mcp)
 *
 * Usage:
 *   ./gradlew :terra-infrastructure:gatlingRun \
 *     -DTERRA_QUERY_BASE_URL=http://localhost:8080
 *
 * Results are written to build/reports/gatling/.
 */
class TerraQueryBaselineSimulation extends Simulation {

  val baseUrl: String = System.getProperty("TERRA_QUERY_BASE_URL", "http://localhost:8080")

  val httpProtocol = http
    .baseUrl(baseUrl)
    .contentTypeHeader("application/json")
    .acceptHeader("application/json")
    .shareConnections

  // ── Simple queries (single-tool, deterministic) ───────────────────────────

  val simpleQueries = List(
    """{"message": "How many earthquakes occurred in Japan in 2011?"}""",
    """{"message": "What was the deadliest flood in Bangladesh?"}""",
    """{"message": "How many hurricanes hit the United States in 2005?"}""",
    """{"message": "What are the most common disaster types in India?"}""",
    """{"message": "How many people died in the 2010 Haiti earthquake?"}"""
  )

  val complexQueries = List(
    """{"message": "Compare flood frequency and death tolls across Bangladesh, India, and Pakistan from 1990 to 2020."}""",
    """{"message": "What are the trends in earthquake frequency and severity in the Pacific Ring of Fire over the past 30 years?"}""",
    """{"message": "Analyze the relationship between drought events and GDP impact in sub-Saharan Africa since 2000."}"""
  )

  val simpleQueryFeeder = simpleQueries.map(q => Map("body" -> q)).circular

  val complexQueryFeeder = complexQueries.map(q => Map("body" -> q)).circular

  // ── Scenarios ─────────────────────────────────────────────────────────────

  val simpleQueryScenario = scenario("Simple Queries")
    .feed(simpleQueryFeeder)
    .exec(
      http("POST /api/v1/chat — simple")
        .post("/api/v1/chat")
        .body(StringBody("#{body}"))
        .check(
          status.is(200),
          jsonPath("$.answer").exists,
          responseTimeInMillis.lte(12000)
        )
    )

  val complexQueryScenario = scenario("Complex Queries")
    .feed(complexQueryFeeder)
    .exec(
      http("POST /api/v1/chat — complex")
        .post("/api/v1/chat")
        .body(StringBody("#{body}"))
        .check(
          status.is(200),
          jsonPath("$.answer").exists,
          responseTimeInMillis.lte(30000)
        )
    )

  val healthCheckScenario = scenario("Health Check")
    .exec(
      http("GET /actuator/health")
        .get("/actuator/health")
        .check(status.is(200))
    )

  // ── Load profile: 5 concurrent users, 2-minute ramp ──────────────────────

  setUp(
    healthCheckScenario.inject(
      atOnceUsers(1)
    ),
    simpleQueryScenario.inject(
      rampUsers(5).during(30.seconds),
      constantUsersPerSec(1).during(90.seconds)
    ),
    complexQueryScenario.inject(
      nothingFor(30.seconds),
      rampUsers(2).during(30.seconds),
      constantUsersPerSec(0.5).during(60.seconds)
    )
  ).protocols(httpProtocol)
    .assertions(
      // Simple queries: p50 ≤ 3s, p95 ≤ 12s
      details("POST /api/v1/chat — simple").responseTime.percentile(50).lte(3000),
      details("POST /api/v1/chat — simple").responseTime.percentile(95).lte(12000),
      details("POST /api/v1/chat — simple").failedRequests.percent.lte(5.0),
      // Complex queries: p50 ≤ 8s, p95 ≤ 20s
      details("POST /api/v1/chat — complex").responseTime.percentile(50).lte(8000),
      details("POST /api/v1/chat — complex").responseTime.percentile(95).lte(20000),
      details("POST /api/v1/chat — complex").failedRequests.percent.lte(5.0),
      // Overall success rate
      global.failedRequests.percent.lte(5.0)
    )
}
