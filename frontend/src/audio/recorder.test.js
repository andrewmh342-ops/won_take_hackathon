import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { encodeWav, MAX_RECORDING_MS, resampleToTarget } from './recorder.js';

describe('WAV 변환', () => {
  it('16kHz 모노 PCM WAV 헤더와 샘플을 생성한다', async () => {
    const blob = encodeWav(new Float32Array([-1, 0, 1]), 16000);
    const bytes = new DataView(await blob.arrayBuffer());

    assert.equal(blob.type, 'audio/wav');
    assert.equal(bytes.getUint32(0, true), 0x46464952);
    assert.equal(bytes.getUint32(4, true), 42);
    assert.equal(bytes.getUint32(8, true), 0x45564157);
    assert.equal(bytes.getUint16(22, true), 1);
    assert.equal(bytes.getUint32(24, true), 16000);
    assert.equal(bytes.getUint16(34, true), 16);
    assert.equal(bytes.getUint32(40, true), 6);
    assert.equal(bytes.getInt16(44, true), -32768);
    assert.equal(bytes.getInt16(46, true), 0);
    assert.equal(bytes.getInt16(48, true), 32767);
  });

  it('48kHz 오디오를 16kHz로 낮춘다', () => {
    const result = resampleToTarget(new Float32Array([0, 0.25, 0.5, 0.75, 1, 0.5]), 48000);

    assert.deepEqual(Array.from(result), [0, 0.75]);
  });

  it('녹음은 30초이고 WAV는 3MB 이하이다', () => {
    assert.equal(MAX_RECORDING_MS, 30000);
    const blob = encodeWav(new Float32Array(16000 * 30));

    assert.equal(blob.size, 960044);
    assert.ok(blob.size <= 3 * 1024 * 1024);
  });
});
