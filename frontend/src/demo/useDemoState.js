// 담당: 공용 — R5 업무 화면이 함께 쓰는 가상 상태. 화면에서 처리하면 같은 상태를 쓰는 플랜에도 반영된다.
import { useCallback, useEffect, useState } from 'react';
import { getDemoState, demoAction } from '../api/client.js';
import { useNavigation } from '../navigation/index.jsx';

export function useDemoState() {
  const [state, setState] = useState(null);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState(false);
  // 플랜에서 들어온 화면은 planId를 함께 보낸다(분실 카드 확인 전 카드 처리는 서버가 막는다).
  const { params } = useNavigation();
  const planId = params?.planId;

  const reload = useCallback(() => {
    setError('');
    return getDemoState().then(setState).catch((e) => setError(`화면 정보를 불러오지 못했어요. (${e.message})`));
  }, []);
  useEffect(() => { void reload(); }, [reload]);

  /** 처리 후 완료 문구를 보여 준다. 실패하면 서버 안내(예: 먼저 분실신고)를 그대로 보여 준다. */
  const act = useCallback(async (payload, doneMessage) => {
    setBusy(true);
    setError('');
    setMessage('');
    try {
      setState(await demoAction(planId ? { ...payload, planId } : payload));
      if (doneMessage) setMessage(doneMessage);
      return true;
    } catch (e) {
      setError(e.message);
      return false;
    } finally {
      setBusy(false);
    }
  }, [planId]);

  return { state, error, message, busy, act, reload };
}
