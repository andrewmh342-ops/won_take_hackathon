// 담당: R5 업무 화면 — 여행자 보험 가입 안내 (안내만, 특정 상품 추천·권유 없음)
import React, { useState } from 'react';
import Popup from '../components/Popup.jsx';
import BizLayout from '../components/BizLayout.jsx';
import { useDemoState } from '../demo/useDemoState.js';

const STEPS = [
  '여행 나라와 출국·귀국 날짜를 정해요.',
  '보험사 안내에서 보장 내용(의료비·휴대품·항공 지연 등)을 직접 비교해요.',
  '출국 전에 가입을 마치고 가입 증명서를 저장해 둬요.',
];
const CHECKLIST = ['여권 정보(영문 이름)', '여행 기간', '현지 연락처·숙소 주소', '지병이 있다면 미리 알릴 내용'];

export default function TravelInsuranceScreen() {
  const { state, error, message } = useDemoState();
  const [open, setOpen] = useState(false);
  return (
    <BizLayout category="환전" title="여행자 보험 가입 안내" lead="가입 방법만 안내해요. 이 앱은 특정 보험 상품을 추천하거나 권유하지 않아요."
      state={state} error={error} message={message}>
      <section className="biz-group">
        <div className="biz-group-head"><strong>가입 순서</strong></div>
        <ol className="answer-steps">{STEPS.map((step) => <li key={step}>{step}</li>)}</ol>
      </section>
      <section className="biz-group">
        <div className="biz-group-head"><strong>미리 준비할 것</strong></div>
        <ul className="sub-list">{CHECKLIST.map((item) => <li key={item}><span className="sub-brand">{item}</span></li>)}</ul>
      </section>
      <button className="primary full" type="button" data-track-id="insurance-open" onClick={() => setOpen(true)}>
        보험사 가입 안내 보기
      </button>
      {open && (
        <Popup title="보험사 안내로 이동" onClose={() => setOpen(false)} buttons={[{ label: '확인', primary: true, onClick: () => setOpen(false) }]}>
          <p>실제 서비스에서는 여기서 보험사 안내 페이지로 이동해요. 시연에서는 이동하지 않아요.</p>
          <p className="muted">보장 내용과 보험료는 보험사 안내에서 직접 확인해 주세요.</p>
        </Popup>
      )}
    </BizLayout>
  );
}
