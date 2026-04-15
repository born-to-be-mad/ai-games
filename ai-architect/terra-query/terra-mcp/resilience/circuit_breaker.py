"""
Simple thread-safe in-process circuit breaker for terra-mcp live API calls.

States: CLOSED → OPEN (after threshold failures) → HALF_OPEN (after reset timeout) → CLOSED

This is intentionally simple (count-based, single-machine) — suitable for the
single-process terra-mcp server. For multi-process deployments, use Redis-backed state.

Usage:
    cb = CircuitBreaker(name="eonet", failure_threshold=5, reset_timeout_s=60)

    result = cb.call(some_function, arg1, arg2)
"""

import logging
import threading
import time
from enum import Enum
from typing import Any, Callable, Optional

logger = logging.getLogger(__name__)


class CircuitState(str, Enum):
    CLOSED = "CLOSED"
    OPEN = "OPEN"
    HALF_OPEN = "HALF_OPEN"


class CircuitBreakerOpenError(RuntimeError):
    """Raised when a call is attempted on an OPEN circuit breaker."""


class CircuitBreaker:
    """
    Count-based circuit breaker.

    Args:
        name:              Identifier for logging / metrics.
        failure_threshold: Consecutive failures to trigger OPEN state.
        reset_timeout_s:   Seconds to wait in OPEN before probing (HALF_OPEN).
        success_threshold: Consecutive successes in HALF_OPEN to return to CLOSED.
    """

    def __init__(
        self,
        name: str,
        failure_threshold: int = 5,
        reset_timeout_s: float = 60.0,
        success_threshold: int = 2,
    ) -> None:
        self.name = name
        self._failure_threshold = failure_threshold
        self._reset_timeout_s = reset_timeout_s
        self._success_threshold = success_threshold

        self._state = CircuitState.CLOSED
        self._failure_count = 0
        self._success_count = 0
        self._opened_at: Optional[float] = None
        self._lock = threading.Lock()

    @property
    def state(self) -> CircuitState:
        with self._lock:
            return self._state

    def call(self, fn: Callable, *args: Any, **kwargs: Any) -> Any:
        """
        Execute fn(*args, **kwargs) protected by the circuit breaker.
        Raises CircuitBreakerOpenError if the circuit is OPEN.
        """
        with self._lock:
            if self._state == CircuitState.OPEN:
                if (
                    self._opened_at is not None
                    and time.monotonic() - self._opened_at >= self._reset_timeout_s
                ):
                    logger.info("[CB:%s] → HALF_OPEN (probing)", self.name)
                    self._state = CircuitState.HALF_OPEN
                    self._success_count = 0
                else:
                    raise CircuitBreakerOpenError(
                        f"Circuit breaker '{self.name}' is OPEN — "
                        f"retry after {self._reset_timeout_s}s"
                    )

        try:
            result = fn(*args, **kwargs)
            self._on_success()
            return result
        except CircuitBreakerOpenError:
            raise
        except Exception as exc:
            self._on_failure(exc)
            raise

    def _on_success(self) -> None:
        with self._lock:
            if self._state == CircuitState.HALF_OPEN:
                self._success_count += 1
                if self._success_count >= self._success_threshold:
                    logger.info("[CB:%s] → CLOSED (recovered)", self.name)
                    self._state = CircuitState.CLOSED
                    self._failure_count = 0
            elif self._state == CircuitState.CLOSED:
                self._failure_count = 0  # reset on any success

    def _on_failure(self, exc: Exception) -> None:
        with self._lock:
            self._failure_count += 1
            logger.warning(
                "[CB:%s] failure %d/%d — %s: %s",
                self.name,
                self._failure_count,
                self._failure_threshold,
                type(exc).__name__,
                exc,
            )
            if self._failure_count >= self._failure_threshold:
                logger.error("[CB:%s] → OPEN", self.name)
                self._state = CircuitState.OPEN
                self._opened_at = time.monotonic()

    def reset(self) -> None:
        """Force-reset to CLOSED (useful in tests)."""
        with self._lock:
            self._state = CircuitState.CLOSED
            self._failure_count = 0
            self._success_count = 0
            self._opened_at = None
