import React from 'react';
import { replyMenus } from './menuNavigation.js';

// 확인 답변은 대화에 남고, 사용자가 후보 버튼을 눌러야 열린다. 메뉴 이름·경로는 기존 카탈로그 한 곳을 쓴다.
export default function MenuReply({ reply, navigate }) {
  return replyMenus(reply).map(item => (
    <button
      key={item.id}
      className="text-link"
      type="button"
      data-track-id={`chat-menu-${item.id}`}
      onClick={() => navigate(item.screenId, { menuId: item.id })}
    >
      {item.name}{reply.type === 'CONFIRM_MENU' ? ' 맞아요, 열기' : ' 화면 보기'}
    </button>
  ));
}
