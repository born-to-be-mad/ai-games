"""
Resilience utilities for terra-mcp external data sources.

Provides:
  - retry_on_network_error: tenacity-based decorator for HTTP calls
  - CircuitBreaker: simple in-process circuit breaker for live APIs
"""

from resilience.retry_decorator import retry_on_network_error
from resilience.circuit_breaker import CircuitBreaker

__all__ = ["retry_on_network_error", "CircuitBreaker"]
