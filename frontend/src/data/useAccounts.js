import { useCallback, useEffect, useState } from 'react';
import { getAccounts } from '../api/client.js';

// 화면 진입마다 다시 조회해 승인 후 잔액을 반영한다. 실패하면 고정값으로 채우지 않고 재시도를 제공한다.
export function useAccounts(enabled = true) {
  const [data, setData] = useState(null);
  const [error, setError] = useState('');
  const [attempt, setAttempt] = useState(0);
  const reload = useCallback(() => setAttempt(value => value + 1), []);
  useEffect(() => {
    let active = true;
    setData(null);
    setError('');
    if (enabled) getAccounts().then(value => {
      if (active) setData(value);
    }).catch(e => {
      if (active) setError(e.message);
    });
    return () => { active = false; };
  }, [enabled, attempt]);
  return { data, error, reload };
}
