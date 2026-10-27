// 담당: 역할 2 — 접수 목록과 고객이 확인한 상담 전달 내용을 표시
import React, { useEffect, useState } from 'react';
import { getTickets } from '../api/client.js';
import { useNavigation } from '../navigation/index.jsx';

const summaryFields = [
  ['purpose', '목적'],
  ['stuckPoint', '막힌 지점'],
  ['triedActions', '시도한 조치'],
  ['unresolved', '남은 문제'],
];

function formatDate(value) {
  if (!value) return '접수 시간 확인 안 됨';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('ko-KR');
}

function getTicketSummary(ticket) {
  return ticket.summary && typeof ticket.summary === 'object' ? ticket.summary : ticket;
}

function getTimeline(ticket, summary) {
  return ticket.clickTimeline ?? summary.clickTimeline ?? [];
}

function getConversation(ticket) {
  return ticket.conversations ?? ticket.conversation ?? ticket.messages ?? [];
}

function eventDescription(event) {
  const action = event.elementId ? ` · ${event.elementId}` : '';
  const screen = event.screenId || '화면 확인 안 됨';
  return `${event.type || '기록'} · ${screen}${action}`;
}

export default function AgentScreen() {
  const { navigate } = useNavigation();
  const [tickets, setTickets] = useState([]);
  const [selectedId, setSelectedId] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  async function loadTickets() {
    setLoading(true);
    setError('');
    try {
      const result = await getTickets();
      if (!Array.isArray(result)) {
        throw new Error('상담 접수 목록의 응답 형식이 올바르지 않습니다.');
      }
      setTickets(result);
      setSelectedId((current) =>
        result.some((ticket) => ticket.ticketId === current)
          ? current
          : (result[0]?.ticketId ?? ''),
      );
    } catch (loadError) {
      setError(`상담 접수 목록을 불러오지 못했어요. (${loadError.message})`);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    loadTickets();
  }, []);

  const selected = tickets.find((ticket) => ticket.ticketId === selectedId) ?? tickets[0];
  const summary = selected ? getTicketSummary(selected) : null;
  const timeline = selected && summary ? getTimeline(selected, summary) : [];
  const conversation = selected ? getConversation(selected) : [];

  return (
    <main className="agent">
      <header className="section-title">
        <span className="wordmark">
          WON<span>테이크</span>
        </span>
        <div className="agent-header-actions">
          <span>상담 인계 데스크</span>
          <button className="secondary" onClick={() => navigate('home')}>
            고객 화면으로
          </button>
        </div>
      </header>

      <div className="agent-heading">
        <div>
          <p className="eyebrow">COUNSELOR WORKSPACE</p>
          <h1>상담 인계</h1>
          <p className="muted">고객이 확인하고 전달한 상담 내용입니다.</p>
        </div>
        <button className="secondary" onClick={loadTickets} disabled={loading}>
          {loading ? '불러오는 중' : '새로고침'}
        </button>
      </div>

      {error && (
        <div className="agent-error" role="alert">
          <p>{error}</p>
          <button className="secondary" onClick={loadTickets} disabled={loading}>
            다시 시도
          </button>
        </div>
      )}

      <div className="agent-layout">
        <aside className="ticket-panel" aria-label="상담 접수 목록">
          <div className="panel-heading">
            <h2>접수 목록</h2>
            <span>{tickets.length}건</span>
          </div>
          {loading && tickets.length === 0 && <p className="agent-empty">접수를 확인하고 있어요.</p>}
          {!loading && tickets.length === 0 && !error && (
            <p className="agent-empty">아직 접수된 상담이 없습니다.</p>
          )}
          <ul className="ticket-list">
            {tickets.map((ticket) => {
              const itemSummary = getTicketSummary(ticket);
              return (
                <li key={ticket.ticketId}>
                  <button
                    className={`ticket-item${ticket.ticketId === selected?.ticketId ? ' selected' : ''}`}
                    type="button"
                    aria-current={ticket.ticketId === selected?.ticketId ? 'true' : undefined}
                    onClick={() => setSelectedId(ticket.ticketId)}
                  >
                    <strong>{ticket.ticketId || '접수 번호 없음'}</strong>
                    <span>{itemSummary.purpose || '목적 확인 안 됨'}</span>
                    <small>{formatDate(ticket.createdAt)}</small>
                  </button>
                </li>
              );
            })}
          </ul>
        </aside>

        <section className="ticket-detail" aria-label="선택한 상담 내용">
          {selected && summary ? (
            <>
              <div className="ticket-detail-heading">
                <div>
                  <p className="eyebrow">TICKET {selected.ticketId}</p>
                  <h2>고객 전달 내용</h2>
                </div>
                <time dateTime={selected.createdAt}>{formatDate(selected.createdAt)}</time>
              </div>

              <section className="agent-card">
                <h3>{summary.source === 'RECORDED' ? '대화 기록 요약' : 'AI 요약'}</h3>
                <dl className="agent-summary">
                  {summaryFields.map(([field, label]) => (
                    <div key={field}>
                      <dt>{label}</dt>
                      <dd>{summary[field] || '확인 안 됨'}</dd>
                    </div>
                  ))}
                </dl>
                {summary.clickConsent === false && (
                  <p className="consent-note">클릭 기록: 동의 안 함</p>
                )}
              </section>

              {summary.planProgress?.length > 0 && (
                <section className="agent-card">
                  <div className="panel-heading">
                    <h3>원테이크 플랜 진행</h3>
                    <span>{summary.planProgress.length}건</span>
                  </div>
                  <ul className="agent-plan">
                    {summary.planProgress.map((line, index) => (
                      <li key={`${line}-${index}`}>{line}</li>
                    ))}
                  </ul>
                </section>
              )}

              <section className="agent-card">
                <div className="panel-heading">
                  <h3>클릭 기록</h3>
                  <span>{timeline.length}건</span>
                </div>
                {summary.clickConsent === false ? (
                  <p className="agent-empty">고객이 기록 수집에 동의하지 않아 표시하지 않습니다.</p>
                ) : timeline.length > 0 ? (
                  <ol className="event-timeline">
                    {timeline.map((event, index) => (
                      <li key={`${event.timestamp || index}-${index}`}>
                        <time>{formatDate(event.timestamp || event.createdAt)}</time>
                        <span>{eventDescription(event)}</span>
                      </li>
                    ))}
                  </ol>
                ) : (
                  <p className="agent-empty">전달된 클릭 기록이 없습니다.</p>
                )}
              </section>

              <section className="agent-card">
                <div className="panel-heading">
                  <h3>대화 원문</h3>
                  <span>{conversation.length}건</span>
                </div>
                {conversation.length > 0 ? (
                  <ol className="agent-conversation">
                    {conversation.map((entry, index) => {
                      const speaker =
                        entry.role === 'USER' || entry.role === 'user'
                          ? '고객'
                          : entry.role === 'ASSISTANT' || entry.role === 'assistant'
                            ? 'WON테이크'
                            : '대화 기록';
                      const text =
                        entry.message ?? entry.text ?? entry.reply ?? '내용 확인 안 됨';
                      return (
                        <li key={`conversation-${index}`}>
                          <strong>{speaker}</strong>
                          {entry.inputType === 'VOICE' && <small>음성 인식 원문</small>}
                          <p>{text}</p>
                          {entry.normalized && (
                            <p className="normalized-text">
                              이렇게 이해했어요: {entry.normalized}
                            </p>
                          )}
                        </li>
                      );
                    })}
                  </ol>
                ) : (
                  <p className="agent-empty">전달된 대화 원문이 없습니다.</p>
                )}
              </section>
            </>
          ) : (
            <div className="agent-empty-state">
              <span className="ai-mark">W</span>
              <h2>{loading ? '접수 내용을 불러오고 있어요.' : '상담 접수를 기다리고 있어요.'}</h2>
              <p>고객이 전달에 동의한 내용만 이 화면에 표시됩니다.</p>
            </div>
          )}
        </section>
      </div>
    </main>
  );
}
