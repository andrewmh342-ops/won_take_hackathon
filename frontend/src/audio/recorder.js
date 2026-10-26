// 담당: 역할 2 — 브라우저 마이크를 16kHz 모노 WAV로 녹음한다
export const TARGET_SAMPLE_RATE = 16000;
export const MAX_RECORDING_MS = 30000;

let activeCapture = null;

export function resampleToTarget(samples, sourceRate, targetRate = TARGET_SAMPLE_RATE) {
  if (!Number.isFinite(sourceRate) || sourceRate <= 0) {
    throw new RangeError('원본 오디오 샘플링 주파수가 올바르지 않습니다.');
  }
  if (!Number.isFinite(targetRate) || targetRate <= 0) {
    throw new RangeError('목표 오디오 샘플링 주파수가 올바르지 않습니다.');
  }
  if (sourceRate === targetRate) return samples.slice();

  const outputLength = Math.floor((samples.length * targetRate) / sourceRate);
  const output = new Float32Array(outputLength);
  const ratio = sourceRate / targetRate;

  for (let index = 0; index < outputLength; index += 1) {
    const position = index * ratio;
    const left = Math.floor(position);
    const right = Math.min(left + 1, samples.length - 1);
    const fraction = position - left;
    output[index] = samples[left] + (samples[right] - samples[left]) * fraction;
  }

  return output;
}

export function encodeWav(samples, sampleRate = TARGET_SAMPLE_RATE) {
  if (!Number.isInteger(sampleRate) || sampleRate <= 0) {
    throw new RangeError('WAV 샘플링 주파수가 올바르지 않습니다.');
  }

  const dataLength = samples.length * 2;
  const buffer = new ArrayBuffer(44 + dataLength);
  const view = new DataView(buffer);

  view.setUint32(0, 0x46464952, true);
  view.setUint32(4, 36 + dataLength, true);
  view.setUint32(8, 0x45564157, true);
  view.setUint32(12, 0x20746d66, true);
  view.setUint32(16, 16, true);
  view.setUint16(20, 1, true);
  view.setUint16(22, 1, true);
  view.setUint32(24, sampleRate, true);
  view.setUint32(28, sampleRate * 2, true);
  view.setUint16(32, 2, true);
  view.setUint16(34, 16, true);
  view.setUint32(36, 0x61746164, true);
  view.setUint32(40, dataLength, true);

  for (let index = 0; index < samples.length; index += 1) {
    const sample = Number.isFinite(samples[index])
      ? Math.max(-1, Math.min(1, samples[index]))
      : 0;
    const pcm = sample < 0 ? Math.round(sample * 0x8000) : Math.round(sample * 0x7fff);
    view.setInt16(44 + index * 2, pcm, true);
  }

  return new Blob([buffer], { type: 'audio/wav' });
}

function stopTracks(stream) {
  stream?.getTracks().forEach((track) => track.stop());
}

async function releaseCapture(capture) {
  if (capture.timer) clearTimeout(capture.timer);
  if (capture.flushTimer) clearTimeout(capture.flushTimer);
  let cleanupError;
  if (capture.processor) {
    capture.processor.port.onmessage = null;
    try {
      capture.processor.disconnect();
    } catch (error) {
      cleanupError = error;
    }
  }
  try {
    capture.source?.disconnect();
  } catch (error) {
    cleanupError ??= error;
  }
  capture.flushResolver = null;
  stopTracks(capture.stream);
  try {
    if (capture.context && capture.context.state !== 'closed') {
      await capture.context.close();
    }
  } catch (error) {
    cleanupError ??= error;
  }
  if (cleanupError) throw cleanupError;
}

function settleCapture(capture, result, error) {
  if (capture.settled) return;
  capture.settled = true;
  if (activeCapture === capture) activeCapture = null;
  if (error) capture.reject(error);
  else capture.resolve(result);
}

async function finishCapture(capture, keepAudio) {
  if (capture.finishing) {
    if (!keepAudio) capture.cancelled = true;
    return capture.promise;
  }
  if (capture.status === 'starting' && capture.context === null) {
    capture.stopRequested = true;
    if (!keepAudio) capture.cancelled = true;
    return capture.promise;
  }

  capture.finishing = true;
  capture.status = 'stopping';
  let result = null;

  try {
    if (keepAudio && !capture.cancelled) {
      if (capture.processor) {
        await new Promise((resolve, reject) => {
          capture.flushResolver = resolve;
          capture.flushTimer = setTimeout(() => {
            capture.flushResolver = null;
            reject(new Error('녹음 데이터를 마무리하지 못했습니다.'));
          }, 2000);
          capture.processor.port.postMessage({ type: 'flush' });
        });
      }
      if (!capture.cancelled) {
        const sampleCount = capture.chunks.reduce((total, chunk) => total + chunk.length, 0);
        const samples = new Float32Array(sampleCount);
        let offset = 0;
        for (const chunk of capture.chunks) {
          samples.set(chunk, offset);
          offset += chunk.length;
        }
        const converted = resampleToTarget(samples, capture.context.sampleRate);
        result = encodeWav(converted);
      }
    }
    capture.chunks.length = 0;
    await releaseCapture(capture);
    settleCapture(capture, capture.cancelled ? null : result);
  } catch (error) {
    capture.chunks.length = 0;
    try {
      await releaseCapture(capture);
    } finally {
      settleCapture(capture, null, error);
    }
  }

  return capture.promise;
}

async function initializeCapture(capture, onStarted) {
  try {
    if (!navigator.mediaDevices?.getUserMedia) {
      throw new Error('이 브라우저에서는 마이크를 사용할 수 없습니다.');
    }
    const AudioContextType = window.AudioContext || window.webkitAudioContext;
    if (!AudioContextType) {
      throw new Error('이 브라우저에서는 오디오 녹음을 지원하지 않습니다.');
    }

    capture.stream = await navigator.mediaDevices.getUserMedia({
      audio: {
        channelCount: 1,
        echoCancellation: true,
        noiseSuppression: true,
        autoGainControl: true,
      },
    });
    if (capture.cancelled) {
      await releaseCapture(capture);
      settleCapture(capture, null);
      return;
    }

    capture.context = new AudioContextType();
    await capture.context.resume();
    const AudioWorkletNodeType = window.AudioWorkletNode;
    if (!capture.context.audioWorklet || !AudioWorkletNodeType) {
      throw new Error('이 브라우저에서는 AudioWorklet 녹음을 지원하지 않습니다.');
    }
    await capture.context.audioWorklet.addModule(
      new URL('./recorder-worklet.js', import.meta.url),
    );
    capture.source = capture.context.createMediaStreamSource(capture.stream);
    capture.processor = new AudioWorkletNodeType(capture.context, 'wontake-wav-capture', {
      channelCount: 1,
      channelCountMode: 'explicit',
      numberOfInputs: 1,
      numberOfOutputs: 1,
      outputChannelCount: [1],
    });
    capture.processor.port.onmessage = (event) => {
      if (event.data?.type === 'flushed') {
        if (capture.flushTimer) clearTimeout(capture.flushTimer);
        capture.flushTimer = null;
        capture.flushResolver?.();
        capture.flushResolver = null;
        return;
      }
      if (
        event.data?.type !== 'samples' ||
        !['recording', 'stopping'].includes(capture.status) ||
        capture.cancelled
      ) {
        return;
      }
      const input = event.data.samples;
      const maximumFrames = Math.floor(
        (MAX_RECORDING_MS / 1000) * capture.context.sampleRate,
      );
      const remainingFrames = maximumFrames - capture.frameCount;
      if (remainingFrames <= 0) {
        stopRecording();
        return;
      }
      const frameCount = Math.min(input.length, remainingFrames);
      capture.chunks.push(input.slice(0, frameCount));
      capture.frameCount += frameCount;
      if (frameCount < input.length || capture.frameCount >= maximumFrames) {
        void stopRecording().catch(() => {});
      }
    };
    capture.source.connect(capture.processor);
    capture.processor.connect(capture.context.destination);

    if (capture.cancelled || capture.stopRequested) {
      await finishCapture(capture, !capture.cancelled);
      return;
    }

    capture.status = 'recording';
    capture.timer = setTimeout(() => {
      void stopRecording().catch(() => {});
    }, MAX_RECORDING_MS);
    onStarted?.();
  } catch (error) {
    capture.chunks.length = 0;
    try {
      await releaseCapture(capture);
    } finally {
      settleCapture(capture, null, error);
    }
  }
}

export function startRecording(onStarted) {
  if (activeCapture) {
    return Promise.reject(new Error('이미 녹음 중입니다.'));
  }

  let resolve;
  let reject;
  const promise = new Promise((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  const capture = {
    chunks: [],
    context: null,
    frameCount: 0,
    promise,
    reject,
    resolve,
    status: 'starting',
    stopRequested: false,
    cancelled: false,
    finishing: false,
    settled: false,
    timer: null,
    flushTimer: null,
    flushResolver: null,
    processor: null,
    source: null,
    stream: null,
  };
  activeCapture = capture;
  void initializeCapture(capture, onStarted).catch((error) => settleCapture(capture, null, error));
  return promise;
}

export function stopRecording() {
  if (!activeCapture) return Promise.resolve(null);
  const capture = activeCapture;
  void finishCapture(capture, true).catch((error) => settleCapture(capture, null, error));
  return capture.promise;
}

export function cancelRecording() {
  if (!activeCapture) return Promise.resolve(null);
  const capture = activeCapture;
  capture.cancelled = true;
  void finishCapture(capture, false).catch((error) => settleCapture(capture, null, error));
  return capture.promise;
}
