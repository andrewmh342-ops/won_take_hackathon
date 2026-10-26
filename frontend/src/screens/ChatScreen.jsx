// 담당: 역할 2 — AI 대화, 음성 질문, 상담 인계 확인
import React, { useCallback, useEffect, useRef, useState } from 'react';
import { getHandoffSummary, postChat, postHandoff, postVoice } from '../api/client.js';
import { cancelRecording, startRecording, stopRecording } from '../audio/recorder.js';
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

  const send = useCallback(
    async (message, inputType = 'TEXT', intentId, heardText) => {
      const text = message.trim();
      if (!text || busy) return;

      setBusy(true);
      setError('');
      setDraft('');
      setMessages((current) => [...current, { role: 'USER', text, inputType, heardText }]);

      try {
        const result = await postChat({ message: text, inputType, intentId });
        setMessages((current) => [
          ...current,
          {
            role: 'ASSISTANT',
            type: result.type,
            text: result.reply || responseText[result.type] || '안내를 받지 못했어요.',
            normalized: result.normalized,
            menuId: result.menuId,
            steps: Array.isArray(result.steps) ? result.steps : [],
            notFoundStreak: result.notFoundStreak,
          },
        ]);
      } catch (requestError) {
        setError(`질문을 처리하지 못했어요. 다시 시도해 주세요. (${requestError.message})`);
      } finally {
        setBusy(false);
      }
    },
    [busy],
  );

  useEffect(() => {
    if (params.question && params.intentId && !initialQuestionSent.current) {
      initialQuestionSent.current = true;
      void send(params.question, 'SUGGESTION', params.intentId);
    }
  }, [params.intentId, params.question, send]);

  useEffect(() => {
    threadEnd.current?.scrollIntoView({ block: 'nearest' });
  }, [messages]);

  useEffect(
    () => () => {
      void cancelRecording();
    },
    [],
  );

  async function submit(event) {
    event.preventDefault();
    await send(draft);
  }

  async function recordQuestion() {
    setError('');
    setMicState('starting');
    try {
      const wav = await startRecording(() => setMicState('recording'));
      if (!wav) return;

      setMicState('transcribing');
      const { recognizedText } = await postVoice(wav);
      const heardText = typeof recognizedText === 'string' ? recognizedText.trim() : '';
      if (!heardText) {
        setError('음성을 확인하지 못했어요. 글자로 입력해 주세요.');
        return;
      }
      await send(heardText, 'VOICE', undefined, heardText);
    } catch (recordingError) {
      setError(`음성을 처리하지 못했어요. 글자로 입력해 주세요. (${recordingError.message})`);
    } finally {
      setMicState('idle');
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
  const inputDisabled = busy || ['starting', 'recording', 'stopping', 'transcribing'].includes(micState);

  return (
    <section className="page chat-page">
      <BackButton />
      <p className="eyebrow">WON TAKE ASSISTANT</p>
      <h1>AI 대화</h1>
      <p className="muted">찾는 메뉴나 막힌 상황을 편하게 말씀해 주세요.</p>

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
          const menuItem = message.menuId ? findMenu(message.menuId) : undefined;
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
              {message.role === 'ASSISTANT' && message.normalized && (
                <p className="normalized-text">
                  <strong>이렇게 이해했어요</strong>
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
              {menuItem && (
                <button
                  className="text-link"
                  type="button"
                  data-track-id={`chat-menu-${menuItem.id}`}
                  onClick={() => navigate(menuItem.screenId)}
                >
                  {menuItem.name} 화면 보기
                </button>
              )}
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
      {micState !== 'idle' && (
        <p className="recording-status" role="status">
          {micState === 'starting' && '마이크 연결을 기다리고 있어요.'}
          {micState === 'recording' && '녹음 중이에요. 최대 30초까지 녹음합니다.'}
          {micState === 'stopping' && '녹음을 마치고 있어요.'}
          {micState === 'transcribing' && '들은 말을 확인하고 있어요.'}
        </p>
      )}

      <form className="chat-composer" onSubmit={submit}>
        <label className="sr-only" htmlFor="chat-draft">질문 입력</label>
        <input
          id="chat-draft"
          value={draft}
          onChange={(event) => setDraft(event.target.value)}
          placeholder="무엇이 궁금하세요?"
          autoComplete="off"
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
              void stopRecording();
            }}
          >
            녹음 끝
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
