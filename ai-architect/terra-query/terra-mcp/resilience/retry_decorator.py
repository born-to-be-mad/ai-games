"""
Tenacity-based retry decorator for external HTTP calls.

Usage:
    @retry_on_network_error(max_attempts=3, wait_seconds=1.0)
    async def fetch_data():
        ...

Retries on:
  - httpx.ConnectError, httpx.ConnectTimeout, httpx.ReadTimeout
  - Generic connection-level exceptions

Does NOT retry:
  - HTTP 4xx status errors (client fault, retrying is pointless)
  - Successful responses (obviously)
"""

import logging
from typing import Callable

import httpx

logger = logging.getLogger(__name__)


def retry_on_network_error(
    max_attempts: int = 3,
    wait_seconds: float = 1.0,
    exponential_base: float = 2.0,
) -> Callable:
    """
    Decorator factory — wraps an async or sync function with tenacity retry logic.

    Args:
        max_attempts:     Total attempts (1 = no retry).
        wait_seconds:     Base wait between retries (exponential backoff).
        exponential_base: Multiplier for each subsequent wait.
    """
    try:
        from tenacity import (
            retry,
            stop_after_attempt,
            wait_exponential,
            retry_if_exception_type,
            before_sleep_log,
        )

        _retry_exceptions = (
            httpx.ConnectError,
            httpx.ConnectTimeout,
            httpx.ReadTimeout,
            ConnectionError,
            TimeoutError,
        )

        def decorator(fn: Callable) -> Callable:
            retrying = retry(
                stop=stop_after_attempt(max_attempts),
                wait=wait_exponential(
                    multiplier=wait_seconds,
                    exp_base=exponential_base,
                    min=wait_seconds,
                    max=30,
                ),
                retry=retry_if_exception_type(_retry_exceptions),
                before_sleep=before_sleep_log(logger, logging.WARNING),
                reraise=True,
            )
            return retrying(fn)

        return decorator

    except ImportError:
        # tenacity not installed — degrade gracefully (no retry)
        logger.warning("tenacity not installed — retry_on_network_error is a no-op")

        def noop_decorator(fn: Callable) -> Callable:
            return fn

        return noop_decorator
