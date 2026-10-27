import test from 'node:test';
import assert from 'node:assert/strict';
import { helpChoices } from './helpChoices.js';

// 작업 39: 말풍선을 눌러 열린 대화 화면의 시작 카드(역질문 + 예상 질문 버튼)
test('예상 질문은 앞의 3개만 큰 버튼으로 보여 준다', () => {
  const help = helpChoices({ helpQuestion: '국내로 보내세요?', helpQuestions: ['한도는 얼마예요?', '해외로 보내려면요?', '예약은요?', '수수료는요?'] });
  assert.equal(help.title, '국내로 보내세요?');
  assert.deepEqual(help.questions, ['한도는 얼마예요?', '해외로 보내려면요?', '예약은요?']);
  assert.equal(help.fallback, false);
});

test('예상 질문이 없으면 [제안한 내용으로 안내받기]만', () => {
  const help = helpChoices({ helpQuestion: '도움이 필요하세요?' });
  assert.deepEqual(help.questions, []);
  assert.equal(help.fallback, true);
});

test('말풍선에서 오지 않았거나 이미 질문했으면 시작 카드가 없다', () => {
  assert.equal(helpChoices({}), null);
  assert.equal(helpChoices({ helpQuestion: '도움이 필요하세요?' }, true), null);
});

test('한 번 쓴 시작 카드는 뒤로 돌아와도 다시 나오지 않는다', async () => {
  const { markHelpUsed } = await import('./helpChoices.js');
  const params = { helpQuestion: '도움이 필요하세요?', helpQuestions: ['한도는요?'] };
  assert.ok(helpChoices(params));
  markHelpUsed(params);
  assert.equal(helpChoices(params), null);
  assert.ok(helpChoices({ ...params })); // 새 말풍선(새 진입)은 다시 보여 준다
});
