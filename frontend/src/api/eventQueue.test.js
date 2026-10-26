import test from 'node:test';
import assert from 'node:assert/strict';
import { createEventQueue } from './eventQueue.js';

test('질문 전에 클릭과 화면 진입의 서버 전송 순서를 보장한다', async () => {
  const queue = createEventQueue();
  const sent = [];
  let release;
  const gate = new Promise((resolve) => { release = resolve; });
  queue.enqueue(async () => { await gate; sent.push('click'); });
  queue.enqueue(async () => { sent.push('screen'); });
  const question = queue.flush().then(() => sent.push('chat'));
  assert.deepEqual(sent, []);
  release();
  await question;
  assert.deepEqual(sent, ['click', 'screen', 'chat']);
});

test('앞선 기록 실패를 숨기지 않고 표시하며 이후 큐는 복구된다', async () => {
  const queue = createEventQueue();
  await assert.rejects(queue.enqueue(async () => { throw new Error('network'); }));
  await assert.rejects(queue.flush(), /행동 기록/);
  await queue.enqueue(async () => 'ok');
  await queue.flush();
});
