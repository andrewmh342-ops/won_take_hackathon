// 담당: R5 업무 화면 — 환율 조회 (시연용 가상 환율, 외화→원 계산기. 전망·매수 시점 조언 없음)
import React, { useState } from 'react';
import { useNavigation } from '../navigation/index.jsx';
import BizLayout from '../components/BizLayout.jsx';
import { useDemoState } from '../demo/useDemoState.js';
import { toKrw, won, rateScreenAction } from '../demo/fx.js';

export default function FxRateScreen() {
  const { params, navigate } = useNavigation();
  const { state, error, message } = useDemoState();
  const [code, setCode] = useState(params.param || 'JPY');
  const [amount, setAmount] = useState('10000');
  const rate = state?.fxRates.find((item) => item.code === code);
  const value = Number(amount);
  const krw = toKrw(value, rate);

  return (
    <BizLayout category="환전" title="환율 조회" lead="시연용 가상 환율이에요. 실제 환율과 다르며 환율 전망은 안내하지 않아요."
      state={state} error={error} message={message}>
      <table className="fx-table">
        <thead><tr><th>통화</th><th>기준</th><th>원화</th></tr></thead>
        <tbody>
          {state?.fxRates.map((item) => (
            <tr key={item.code} className={item.code === code ? 'focus' : ''} onClick={() => setCode(item.code)}>
              <td><strong>{item.code}</strong> {item.name}</td>
              <td>{item.unit.toLocaleString('ko-KR')} {item.code}</td>
              <td>{won(item.krw)}</td>
            </tr>
          ))}
        </tbody>
      </table>
      <form className="biz-form" onSubmit={(event) => event.preventDefault()}>
        <p><strong>얼마가 필요한지 계산해 보세요</strong></p>
        <label>
          통화
          <select value={code} onChange={(event) => setCode(event.target.value)}>
            {state?.fxRates.map((item) => <option key={item.code} value={item.code}>{item.code} · {item.name}</option>)}
          </select>
        </label>
        <label>
          외화 금액 ({code})
          <input inputMode="numeric" value={amount} onChange={(event) => setAmount(event.target.value.replace(/[^0-9]/g, ''))} />
        </label>
        <p className="fx-result" role="status">{krw === null ? '1 이상 정수로 입력해 주세요.' : `${value.toLocaleString('ko-KR')} ${code} ≈ ${won(krw)} (원 미만 버림)`}</p>
        {/* 작업 37: 플랜에서 들어오면 환전은 플랜의 환전 단계에서 한다(같은 일을 두 번 하지 않게). */}
        {rateScreenAction(params) === 'NEXT' ? (
          <button className="primary full" type="button" data-track-id="plan-next" onClick={() => navigate('plan', { planId: params.planId })}>
            다음 단계로
          </button>
        ) : (
          <button className="primary full" type="button" disabled={krw === null} data-track-id="fx-go-exchange"
            onClick={() => navigate('exchange-apply', { menuId: 'exchange-apply', param: code, amount: value })}>
            이 금액으로 환전 신청하기
          </button>
        )}
      </form>
    </BizLayout>
  );
}
