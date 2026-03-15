import { useState, useCallback } from 'react';

/**
 * Encapsulates the repeated loading / error state pattern used by every
 * data-fetching component.
 *
 * Usage:
 *   const { loading, error, run } = useAsyncAction();
 *   const result = await run(
 *     () => someApi(args),
 *     err => err.response?.data?.message || err.message
 *   );
 *   if (result) setState(result.data);
 */
export function useAsyncAction() {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const run = useCallback(async (asyncFn, errorMapper) => {
    setLoading(true);
    setError(null);
    try {
      return await asyncFn();
    } catch (err) {
      setError(
        errorMapper
          ? errorMapper(err)
          : (err.response?.data?.message || err.message || 'Request failed.')
      );
      return null;
    } finally {
      setLoading(false);
    }
  }, []);

  return { loading, error, run };
}
