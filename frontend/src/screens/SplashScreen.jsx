// 담당: 공용 — AI 길잡이로 들어가기 전 WON TAKE 스플래시 (약 1초 뒤 AI 대화 화면으로 바꾼다)
import React, { useEffect } from 'react';
import { useNavigation } from '../navigation/index.jsx';
import RobotIcon from '../components/RobotIcon.jsx';

export const SPLASH_MS = 1500;

export default function SplashScreen() {
  const { params, navigate } = useNavigation();
  useEffect(() => {
    // 스플래시는 기록에 남기지 않는다. 대화 화면에서 뒤로 가면 스플래시가 아니라 원래 화면으로 간다.
    const timer = setTimeout(() => navigate('chat', params.next ?? {}, { replace: true }), SPLASH_MS);
    return () => clearTimeout(timer);
  }, []);
  return (
    <section className="splash" role="status" aria-label="WON TAKE AI 길잡이를 여는 중">
      <div className="splash-mark">
        <RobotIcon size={64} />
      </div>
      <p className="splash-word">
        WON <strong>TAKE</strong>
      </p>
      <p className="splash-copy">
        찾기 전에 먼저,
        <br />
        해결될 때까지 끊김 없이
      </p>
      <span className="splash-bar" aria-hidden="true" />
      <small className="splash-foot">WON TAKE ASSISTANT</small>
    </section>
  );
}
