import assert from 'node:assert/strict';
import { test } from 'node:test';
import { createSpeechInput } from './speech.js';

function setup(browserOptions = {}) {
  let recognition;
  let now = 0;
  let id = 0;
  const timers = new Map();
  class Recognition {
    constructor() { recognition = this; }
    start() { this.onstart?.(); }
    stop() { this.stops = (this.stops || 0) + 1; }
    abort() { this.aborts = (this.aborts || 0) + 1; }
  }
  const browser = { isSecureContext: true, webkitSpeechRecognition: Recognition, ...browserOptions };
  const input = createSpeechInput({
    getBrowser: () => browser,
    setTimer(fn, delay) { timers.set(++id, { fn, time: now + delay }); return id; },
    clearTimer(key) { timers.delete(key); },
  });
  return {
    input, browser, timers,
    get recognition() { return recognition; },
    advance(ms) {
      now += ms;
      for (const [key, timer] of [...timers]) {
        if (timer.time <= now) { timers.delete(key); timer.fn(); }
      }
    },
    results(texts) {
      recognition.onresult({ results: texts.map(([text, isFinal = true]) =>
        Object.assign([{ transcript: text }], { isFinal })) });
    },
  };
}

test('한국어 확정 결과를 순서대로 합치고 중간 결과·중복 이벤트는 보내지 않는다', async () => {
  const s = setup(); const result = s.input.start();
  assert.equal(s.recognition.lang, 'ko-KR');
  s.results([['이체 한도가'], ['얼마', false]]);
  s.results([['이체 한도가'], ['얼마인가요']]);
  s.results([['이체 한도가'], ['얼마인가요']]);
  s.recognition.onend();
  assert.equal(await result, '이체 한도가 얼마인가요');
  assert.equal(s.timers.size, 0);
});

test('빈 결과는 빈 문자열로 반환한다', async () => {
  const s = setup(); const result = s.input.start(); s.recognition.onend();
  assert.equal(await result, '');
});

test('수동 중지는 마지막 확정 결과가 도착할 때까지 기다린다', async () => {
  const s = setup(); const result = s.input.start(); s.input.stop();
  assert.equal(s.recognition.stops, 1);
  s.results([['잔액 알려줘']]); s.recognition.onend();
  assert.equal(await result, '잔액 알려줘');
});

test('29,999ms에는 유지하고 30,000ms에 자동 중지한다', async () => {
  const s = setup(); const result = s.input.start();
  s.advance(29999); assert.equal(s.recognition.stops, undefined);
  s.advance(1); assert.equal(s.recognition.stops, 1);
  s.recognition.onend(); await result;
});

test('종료 응답이 없어도 3초 후 확정 결과로 마무리한다', async () => {
  const s = setup(); const result = s.input.start(); s.results([['계좌 조회']]);
  s.input.stop(); s.advance(3000);
  assert.equal(await result, '계좌 조회'); assert.equal(s.recognition.aborts, 1);
});

for (const code of ['not-allowed', 'audio-capture', 'network', 'no-speech', 'service-not-allowed']) {
  test(`${code} 오류를 안내하고 다음 인식을 시작할 수 있다`, async () => {
    const s = setup(); const result = s.input.start();
    s.recognition.onerror({ error: code });
    await assert.rejects(result, /글자로 입력해 주세요/);
    assert.equal(s.timers.size, 0);
    const retry = s.input.start(); s.input.cancel(); assert.equal(await retry, null);
  });
}

test('미지원 브라우저는 텍스트 입력을 안내한다', async () => {
  const s = setup({ webkitSpeechRecognition: undefined });
  await assert.rejects(s.input.start(), /Chrome/);
});

test('보안 연결이 아니면 HTTPS 또는 localhost를 안내한다', async () => {
  const s = setup({ isSecureContext: false });
  await assert.rejects(s.input.start(), /HTTPS.*localhost/);
});

test('화면 이탈 취소 후 늦은 결과는 버린다', async () => {
  const s = setup(); const result = s.input.start(); const old = s.recognition.onresult;
  s.results([['전송하면 안 됨']]); s.input.cancel();
  old({ results: [Object.assign([{ transcript: '늦은 결과' }], { isFinal: true })] });
  assert.equal(await result, null); assert.equal(s.recognition.aborts, 1);
  assert.equal(s.timers.size, 0);
});

test('중복 시작은 거부하고 기존 인식은 유지한다', async () => {
  const s = setup(); const result = s.input.start();
  await assert.rejects(s.input.start(), /이미/);
  s.results([['첫 질문']]); s.recognition.onend(); assert.equal(await result, '첫 질문');
});

test('마이크 권한 응답 대기가 10초를 넘으면 종료한다', async () => {
  const s = setup(); s.browser.webkitSpeechRecognition.prototype.start = function () {};
  const result = s.input.start(); s.advance(10000);
  await assert.rejects(result, /글자로 입력해 주세요/);
  assert.equal(s.recognition.aborts, 1);
});

test('시작 자체가 실패해도 다시 시작할 수 있다', async () => {
  const s = setup(); const start = s.browser.webkitSpeechRecognition.prototype.start;
  s.browser.webkitSpeechRecognition.prototype.start = () => { throw new Error('장치 오류'); };
  await assert.rejects(s.input.start(), /장치 오류/);
  s.browser.webkitSpeechRecognition.prototype.start = start;
  const retry = s.input.start(); s.input.cancel(); assert.equal(await retry, null);
});
