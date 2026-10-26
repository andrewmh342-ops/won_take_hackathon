// 담당: 역할 1 — 시연용 간편비밀번호 로그인. 아무 숫자 6자리면 로그인된다.
// 키패드 버튼에는 data-track-id를 붙이지 않는다 (비밀번호 입력이 클릭 기록에 남지 않게).
import React, { useState } from 'react';
import { useNavigation } from '../navigation/index.jsx';
import BackButton from '../layout/BackButton.jsx';
import { login, PIN_LENGTH } from '../session/auth.js';

const KEYS = ['1', '2', '3', '4', '5', '6', '7', '8', '9', '', '0', '⌫'];

export default function LoginScreen() {
  const { navigate } = useNavigation();
  const [pin, setPin] = useState('');

  const press = (key) => {
    if (key === '⌫') {
      setPin((old) => old.slice(0, -1));
      return;
    }
    const next = (pin + key).slice(0, PIN_LENGTH);
    setPin(next);
    if (next.length === PIN_LENGTH && login(next)) navigate('home');
  };

  return (
    <div className="page login">
      <BackButton />
      <p className="eyebrow">LOGIN</p>
      <h1>간편비밀번호를 입력해 주세요</h1>
      <p className="muted">시연용이에요. 아무 숫자 6자리를 누르면 로그인돼요.</p>
      <div className="pin-dots" aria-label={`${pin.length}자리 입력됨`}>
        {Array.from({ length: PIN_LENGTH }, (_, i) => (
          <span key={i} className={i < pin.length ? 'filled' : ''} />
        ))}
      </div>
      <div className="pin-pad">
        {KEYS.map((key, i) =>
          key === '' ? (
            <span key={i} />
          ) : (
            <button
              key={i}
              type="button"
              aria-label={key === '⌫' ? '지우기' : key}
              onClick={() => press(key)}
            >
              {key}
            </button>
          ),
        )}
      </div>
    </div>
  );
}
