// 담당: 역할 2 — AI 대화, 음성 질문, 상담 인계 확인
import React, { useCallback, useEffect, useRef, useState } from 'react';
import { getHandoffSummary, postChat, postHandoff, getHistory } from '../api/client.js';
import { cancelRecognition, startRecognition, stopRecognition } from '../audio/speech.js';
import Popup from '../components/Popup.jsx';
import BackButton from '../layout/BackButton.jsx';
import { useNavigation } from '../navigation/index.jsx';
import { findMenu } from '../data/menu.js';

const responseText = {
  NOT_FOUND: '말씀하신 내용을 찾지 못했어요. 다른 표현으로 다시 물어봐 주세요.',
  ASK_TEXT_INPUT: '음성을 확인하지 못했어요. 글자로 입력해 주세요.',
  SUGGEST_HANDOFF: '상담사에게 지금까지의 내용을 전달할까요?',
};

const summaryFields = [
  ['purpose', '목적'],
  ['stuckPoint', '막힌 지점'],
  ['triedActions', '시도한 조치'],
  ['unresolved', '남은 문제'],
];

export default function ChatScreen() {
  const { params, navigate } = useNavigation();
  const [messages, setMessages] = useState([]);
  const [historyReady, setHistoryReady] = useState(false);
  const [historyAttempt, setHistoryAttempt] = useState(0);
  const [draft, setDraft] = useState('');
  const [busy, setBusy] = useState(false);
  const [micState, setMicState] = useState('idle');
  const [error, setError] = useState('');
  const [summary, setSummary] = useState(null);
  const [summaryLoading, setSummaryLoading] = useState(false);
  const [handoffBusy, setHandoffBusy] = useState(false);
  const [receipt, setReceipt] = useState(null);
  const threadEnd = useRef(null);
  const initialQuestionSent = useRef(false);
  const mounted = useRef(true);
  const voicePending = useRef(false);

  useEffect(() => {
    let active = true;
    setError('');
    getHistory().then((history) => {
      if (!active) return;
      setMessages(history.map((entry) => entry.role === 'USER'
        ? { role: 'USER', text: entry.message, heardText: entry.inputType === 'VOICE' ? entry.message : undefined }
        : { role: 'ASSISTANT', text: entry.message, ...entry.answer, steps: entry.answer?.steps ?? [] }));
      setHistoryReady(true);
    }).catch(() => { if (active) setError('대화 기록을 불러오지 못했어요. 서버 연결을 확인하고 다시 불러와 주세요.'); });
    return () => { active = false; };
  }, [historyAttempt]);

  const send = useCallback(
    async (message, inputType = 'TEXT', intentId, heardText) => {
      const text = message.trim();
      if (!text || busy || !historyReady) return;

      setBusy(true);
      setError('');
      setDraft('');
      setMessages((current) => [...current, { role: 'USER', text, inputType, heardText }]);

      try {
        const result = await postChat({ message: text, inputType, intentId, contextId: params.contextId });
        setMessages((current) => [
          ...current,
          {
            role: 'ASSISTANT',
            type: result.type,
            text: result.reply || responseText[result.type] || '안내를 받지 못했어요.',
            normalized: result.normalized,
            menuId: result.menuId,
            menuIds: result.menuIds,
            mode: result.mode,
            steps: Array.isArray(result.steps) ? result.steps : [],
            notFoundStreak: result.notFoundStreak,
          },
        ]);
      } catch (requestError) {
        setMessages((current) => current.at(-1)?.role === 'USER' ? current.slice(0, -1) : current);
        setDraft(text);
        setError(`질문을 처리하지 못했어요. 다시 시도해 주세요. (${requestError.message})`);
      } finally {
        setBusy(false);
      }
    },
    [busy, historyReady, params.contextId],
  );

  useEffect(() => {
    if (historyReady && params.question && (params.intentId || params.autoSend) && !initialQuestionSent.current) {
      initialQuestionSent.current = true;
      void send(params.question, params.intentId ? 'SUGGESTION' : 'TEXT', params.intentId);
    }
  }, [params.intentId, params.question, params.autoSend, historyReady, send]);

  useEffect(() => {
    threadEnd.current?.scrollIntoView({ block: 'nearest' });
  }, [messages]);

  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
      cancelRecognition();
    };
  }, []);

  async function submit(event) {
    event.preventDefault();
    await send(draft);
  }

  async function recordQuestion() {
    if (voicePending.current || busy) return;
    voicePending.current = true;
    setError('');
    setMicState('starting');
    try {
      const recognizedText = await startRecognition(() => {
        if (mounted.current) setMicState('recording');
      });
      if (!mounted.current || recognizedText === null) return;
      const heardText = recognizedText.trim();
      if (!heardText) {
        setError('음성을 확인하지 못했어요. 글자로 입력해 주세요.');
        return;
      }
      await send(heardText, 'VOICE', undefined, heardText);
    } catch (recordingError) {
      if (mounted.current) setError(recordingError.message);
    } finally {
      voicePending.current = false;
      if (mounted.current) setMicState('idle');
    }
  }

  async function requestHandoff() {
    setError('');
    setSummaryLoading(true);
    try {
      const result = await getHandoffSummary();
      setSummary(result);
    } catch (requestError) {
      setError(`상담 전달 내용을 불러오지 못했어요. (${requestError.message})`);
    } finally {
      setSummaryLoading(false);
    }
  }

  async function confirmHandoff() {
    if (!summary || handoffBusy) return;
    setHandoffBusy(true);
    setError('');
    try {
      const result = await postHandoff(summary);
      setReceipt(result);
      setSummary(null);
      setMessages((current) => [
        ...current,
        {
          role: 'SYSTEM',
          text: `상담사에게 전달했어요. 접수 번호: ${result.ticketId}`,
        },
      ]);
    } catch (requestError) {
      setError(`상담 내용을 전달하지 못했어요. 다시 시도해 주세요. (${requestError.message})`);
    } finally {
      setHandoffBusy(false);
    }
  }

  const suggestions = Array.isArray(params.suggestions) ? params.suggestions.slice(0, 3) : [];
  const isRecording = micState === 'recording';
  const inputDisabled = !historyReady || busy || ['starting', 'recording', 'stopping', 'transcribing'].includes(micState);

  return (
    <section className="page chat-page">
      <BackButton />
      <p className="eyebrow">WON TAKE ASSISTANT</p>
      <h1>AI 대화</h1>
      <p className="muted">찾는 메뉴나 막힌 상황을 편하게 말씀해 주세요.</p>
      {params.helpQuestion && (
        <div className="suggestion-list">
          <p><strong>먼저 제안한 도움</strong></p>
          <p>{params.helpQuestion}</p>
          <p>궁금한 점을 짧게 적거나 마이크로 말씀해 주세요.</p>
          <button type="button" disabled={inputDisabled} onClick={() => send('방금 제안한 내용으로 안내해 줘')}>
            제안한 내용으로 안내받기
          </button>
        </div>
      )}

      {messages.length === 0 && (suggestions.length > 0 || params.question) && (
        <div className="suggestion-list">
          <p>혹시 이걸 찾고 계셨나요?</p>
          {params.question && params.intentId && (
            <button
              type="button"
              data-track-id={`suggestion-${params.intentId}`}
              disabled={busy}
              onClick={() => send(params.question, 'SUGGESTION', params.intentId)}
            >
              {params.question}
            </button>
          )}
          {suggestions.map((suggestion) => (
            <button
              key={suggestion.intentId}
              type="button"
              data-track-id={`suggestion-${suggestion.intentId}`}
              disabled={busy}
              onClick={() => send(suggestion.question, 'SUGGESTION', suggestion.intentId)}
            >
              {suggestion.question}
            </button>
          ))}
        </div>
      )}

      <ol className="chat-thread" aria-live="polite" aria-label="대화 내용">
        {messages.map((message, index) => {
          const menuItems = [...new Set(message.menuIds ?? (message.menuId ? [message.menuId] : []))]
            .slice(0, 2).map(findMenu).filter(Boolean);
          const canHandoff =
            message.type === 'SUGGEST_HANDOFF' ||
            (message.type === 'NOT_FOUND' && message.notFoundStreak >= 2);

          return (
            <li
              className={`chat-message chat-message-${message.role.toLowerCase()}`}
              key={`message-${index}`}
            >
              <strong>
                {message.role === 'USER'
                  ? '나'
                  : message.role === 'SYSTEM'
                    ? '전달 상태'
                    : 'WON테이크'}
              </strong>
              {message.heardText && <small>들은 말</small>}
              <p>{message.text}</p>
              {message.mode === 'FAKE' && <small>연결 테스트용 모의 응답 · 실제 AI 판단 아님</small>}
              {message.role === 'ASSISTANT' && message.normalized && (
                <p className="normalized-text">
                  <strong>{message.mode === 'FAKE' ? '인식 원문 (모의 모드)' : '이렇게 이해했어요'}</strong>
                  {message.normalized}
                </p>
              )}
              {message.role === 'ASSISTANT' && message.steps.length > 0 && (
                <ol className="answer-steps">
                  {message.steps.map((step, stepIndex) => (
                    <li key={`step-${stepIndex}`}>{step}</li>
                  ))}
                </ol>
              )}
              {menuItems.map((menuItem) => (
                <button
                  key={menuItem.id}
                  className="text-link"
                  type="button"
                  data-track-id={`chat-menu-${menuItem.id}`}
                  onClick={() => navigate(menuItem.screenId, { menuId: menuItem.id })}
                >
                  {menuItem.name} 화면 보기
                </button>
              ))}
              {canHandoff && (
                <button
                  className="secondary chat-handoff-button"
                  type="button"
                  disabled={summaryLoading || handoffBusy}
                  onClick={requestHandoff}
                >
                  {summaryLoading ? '내용을 정리하고 있어요' : '상담사에게 전달하기'}
                </button>
              )}
            </li>
          );
        })}
        <li ref={threadEnd} aria-hidden="true" />
      </ol>

      {receipt && (
        <div className="handoff-receipt" role="status">
          <p>상담 요청을 접수했어요.</p>
          <button type="button" onClick={() => navigate('agent')}>
            상담사 화면에서 확인
          </button>
        </div>
      )}

      {error && (
        <p className="error chat-error" role="alert">
          {error}
        </p>
      )}
      {!historyReady && <button className="secondary" onClick={() => setHistoryAttempt((n) => n + 1)}>대화 다시 불러오기</button>}
      {busy && <p role="status">질문과 맥락을 확인하고 있어요.</p>}
      {micState !== 'idle' && (
        <p className="recording-status" role="status">
          {micState === 'starting' && '마이크 연결을 기다리고 있어요.'}
          {micState === 'recording' && '듣고 있어요. 최대 30초까지 음성을 인식합니다.'}
          {micState === 'stopping' && '음성 인식을 마치고 있어요.'}
          {micState === 'transcribing' && '들은 말을 확인하고 있어요.'}
        </p>
      )}

      <p className="info-note">
        질문과 최근 대화는 AI 메뉴 안내에 사용해요. 행동 기록은 동의한 경우에만 함께 사용해요.{' '}
        마이크를 누르면 Chrome 음성 인식 서비스에서 음성이 처리될 수 있어요.
        인터넷 연결이 필요하며, 인식이 안 되면 글자로 입력해 주세요.
      </p>
      <form className="chat-composer" onSubmit={submit}>
        <label className="sr-only" htmlFor="chat-draft">질문 입력</label>
        <input
          id="chat-draft"
          value={draft}
          onChange={(event) => setDraft(event.target.value)}
          placeholder="무엇이 궁금하세요?"
          autoComplete="off"
          maxLength={2000}
          disabled={inputDisabled}
          data-track-id="chat-text-input"
        />
        {isRecording ? (
          <button
            className="voice-button recording"
            type="button"
            data-track-id="voice-stop"
            onClick={() => {
              setMicState('stopping');
              stopRecognition();
            }}
          >
            말하기 끝
          </button>
        ) : (
          <button
            className="voice-button"
            type="button"
            data-track-id="voice-start"
            onClick={recordQuestion}
            disabled={inputDisabled}
          >
            마이크
          </button>
        )}
        <button className="primary chat-send" type="submit" disabled={inputDisabled || !draft.trim()}>
          보내기
        </button>
      </form>

      {summary && (
        <Popup
          title="상담사에게 전달할 내용"
          onClose={() => {
            if (!handoffBusy) setSummary(null);
          }}
          buttons={[
            { label: '취소', onClick: () => setSummary(null) },
            {
              label: handoffBusy ? '전달 중' : '확인 후 전달',
              primary: true,
              onClick: confirmHandoff,
            },
          ]}
        >
          <p>아래 내용을 확인해 주세요. 확인을 누르기 전에는 상담사에게 전달되지 않아요.</p>
          <dl className="handoff-summary">
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
          {summary.clickTimeline?.length > 0 && (
            <p className="summary-count">클릭 기록 {summary.clickTimeline.length}건도 함께 전달합니다.</p>
          )}
        </Popup>
      )}
    </section>
  );
}
