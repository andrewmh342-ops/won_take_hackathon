// 담당: 공용 — 역할 2도 사용할 접근 가능한 팝업 틀
import React, { useRef, useEffect } from 'react';
export default function Popup({ title, children, buttons = [], onClose }) {
  const ref = useRef(null);
  useEffect(() => { const dialog = ref.current; dialog.showModal(); return () => dialog.close(); }, []);
  return <dialog ref={ref} className="popup" aria-labelledby="popup-title" onCancel={event => { event.preventDefault(); onClose?.(); }}><h2 id="popup-title">{title}</h2><div className="popup-body">{children}</div><div className="popup-buttons">{buttons.map((button, index) => <button key={index} className={button.primary ? 'primary' : 'secondary'} onClick={button.onClick}>{button.label}</button>)}</div></dialog>;
}
