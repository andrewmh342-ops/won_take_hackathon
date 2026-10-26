// 담당: 역할 2 — Chrome Web Speech API. 앱에서 음성 파일을 만들거나 전송하지 않는다.
export const MAX_LISTENING_MS = 30000;
const TEXT_FALLBACK = '글자로 입력해 주세요.';

export function createSpeechInput({
  getBrowser = () => globalThis.window,
  setTimer = globalThis.setTimeout,
  clearTimer = globalThis.clearTimeout,
} = {}) {
  let active = null;

  function complete(session, value, error, abort = false) {
    if (active !== session) return;
    active = null;
    for (const timer of session.timers) clearTimer(timer);
    const recognition = session.recognition;
    recognition.onstart = recognition.onresult = recognition.onerror = recognition.onend = null;
    if (abort) {
      try { recognition.abort(); } catch { /* 이미 종료된 인식은 정리만 한다. */ }
    }
    if (error) session.reject(error);
    else session.resolve(value);
  }

  const transcript = (session) => [...session.results.values()].join(' ').trim();

  function stop() {
    const session = active;
    if (!session || session.stopping) return;
    session.stopping = true;
    session.timers.push(setTimer(() => {
      complete(session, transcript(session), null, true);
    }, 3000));
    try {
      session.recognition.stop();
    } catch (error) {
      complete(session, null, new Error(`${error.message} ${TEXT_FALLBACK}`), true);
    }
  }

  function start(onStarted) {
    if (active) return Promise.reject(new Error('이미 음성을 인식하고 있습니다.'));
    const browser = getBrowser();
    const Recognition = browser?.SpeechRecognition || browser?.webkitSpeechRecognition;
    if (!Recognition) {
      return Promise.reject(new Error(`음성 인식을 지원하는 PC Chrome을 사용하거나 ${TEXT_FALLBACK}`));
    }
    if (browser.isSecureContext === false) {
      return Promise.reject(new Error(`HTTPS 또는 localhost에서 실행하거나 ${TEXT_FALLBACK}`));
    }

    return new Promise((resolve, reject) => {
      const recognition = new Recognition();
      const session = { recognition, resolve, reject, results: new Map(), timers: [], stopping: false };
      active = session;
      recognition.lang = 'ko-KR';
      recognition.continuous = true;
      recognition.interimResults = false;
      const startupTimer = setTimer(() => {
        complete(session, null, new Error(`마이크 연결 시간이 초과되었습니다. ${TEXT_FALLBACK}`), true);
      }, 10000);
      session.timers.push(startupTimer);
      recognition.onstart = () => {
        if (active !== session) return;
        clearTimer(startupTimer);
        session.timers.push(setTimer(stop, MAX_LISTENING_MS));
        onStarted?.();
      };
      recognition.onresult = (event) => {
        if (active !== session) return;
        for (let index = 0; index < event.results.length; index += 1) {
          const result = event.results[index];
          if (result.isFinal) session.results.set(index, result[0].transcript.trim());
        }
      };
      recognition.onend = () => complete(session, transcript(session));
      recognition.onerror = (event) => {
        if (session.stopping && event.error === 'aborted') {
          complete(session, transcript(session));
          return;
        }
        const messages = {
          'not-allowed': '마이크 사용 권한을 허용해 주세요.',
          'service-not-allowed': '브라우저 음성 인식 서비스를 사용할 수 없습니다.',
          'audio-capture': '마이크를 확인해 주세요.',
          network: '음성 인식 서비스에 연결하지 못했습니다.',
          'no-speech': '음성을 듣지 못했습니다.',
        };
        complete(session, null, new Error(`${messages[event.error] || '음성을 인식하지 못했습니다.'} ${TEXT_FALLBACK}`), true);
      };
      try { recognition.start(); }
      catch (error) { complete(session, null, new Error(`${error.message} ${TEXT_FALLBACK}`), true); }
    });
  }

  function cancel() {
    if (active) complete(active, null, null, true);
  }

  return { start, stop, cancel };
}

const input = createSpeechInput();
export const startRecognition = input.start;
export const stopRecognition = input.stop;
export const cancelRecognition = input.cancel;
