// 담당: 공용 — AI 길잡이 로봇 아이콘 (직접 그린 SVG, 실제 앱 캐릭터·이미지는 쓰지 않는다)
import React from 'react';

export default function RobotIcon({ size = 20, className = '' }) {
  return (
    <svg
      className={`robot-icon ${className}`.trim()}
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      aria-hidden="true"
    >
      <line x1="12" y1="2.5" x2="12" y2="5.5" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
      <circle cx="12" cy="2.5" r="1.4" fill="currentColor" />
      <rect x="4" y="5.5" width="16" height="13" rx="4.5" fill="currentColor" />
      <rect x="6.6" y="8.6" width="10.8" height="6.4" rx="3.2" fill="#fff" />
      <circle cx="9.6" cy="11.8" r="1.35" fill="currentColor" />
      <circle cx="14.4" cy="11.8" r="1.35" fill="currentColor" />
      <rect x="1.6" y="10" width="2.4" height="4.5" rx="1.2" fill="currentColor" />
      <rect x="20" y="10" width="2.4" height="4.5" rx="1.2" fill="currentColor" />
      <path d="M9.5 21h5" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
    </svg>
  );
}
