// 담당: 공용 — 메뉴 목록과 외부 호출 가능한 강조
import React, { useEffect } from 'react';
import { menu } from '../data/menu.js';
import { useNavigation } from '../navigation/index.jsx';
let pending = null,
  openMenu = null,
  timer = null;
export function highlightMenu(menuId) {
  if (!menu.some((item) => item.id === menuId)) return false;
  pending = menuId;
  openMenu?.();
  return true;
}
export function registerMenuNavigation(fn) {
  openMenu = fn;
  return () => {
    openMenu = null;
  };
}
export default function MenuList({ query = '' }) {
  const { navigate, key } = useNavigation();
  useEffect(() => {
    if (!pending) return;
    const target = document.querySelector(`[data-menu-id="${pending}"]`);
    if (!target) return;
    document
      .querySelectorAll('.menu-highlight')
      .forEach((el) => el.classList.remove('menu-highlight'));
    target.scrollIntoView({ behavior: 'smooth', block: 'center' });
    target.focus({ preventScroll: true });
    target.classList.add('menu-highlight');
    pending = null;
    clearTimeout(timer);
    timer = setTimeout(() => target.classList.remove('menu-highlight'), 3000);
  }, [key, query]);
  const keyword = query.trim();
  const filtered = menu.filter(
    (item) => item.name.includes(keyword) || item.category.includes(keyword),
  );
  return (
    <div className="menu-groups">
      {[...new Set(filtered.map((item) => item.category))].map((category) => (
        <section key={category} data-category={category}>
          <h2>{category}</h2>
          {filtered
            .filter((item) => item.category === category)
            .map((item) => (
              <button
                className="menu-row"
                key={item.id}
                data-menu-id={item.id}
                data-track-id={item.id}
                onClick={() => navigate(item.screenId, { menuId: item.id })}
              >
                {item.name}
                <span aria-hidden="true">›</span>
              </button>
            ))}
        </section>
      ))}
      {filtered.length === 0 && (
        <p className="empty">찾는 메뉴가 없어요. AI 길잡이에게 물어보세요.</p>
      )}
    </div>
  );
}
