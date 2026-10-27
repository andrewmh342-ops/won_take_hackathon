import React from 'react';

// 세 화면의 잔액 로딩·실패 안내를 한 곳에서 관리한다.
export default function AccountStatus({ data, error, reload }) {
  if (data) return null;
  return error ? (
    <div role="alert">
      <p className="warn">{error}</p>
      <button className="secondary" onClick={reload}>다시 시도</button>
    </div>
  ) : <p className="muted" role="status">잔액을 불러오는 중이에요</p>;
}
