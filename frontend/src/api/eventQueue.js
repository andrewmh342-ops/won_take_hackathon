// 클릭·화면 이동 요청 순서를 유지하고, 질문은 앞선 기록 전송 완료 후 보낸다.
export function createEventQueue() {
  let tail = Promise.resolve();
  let failure = null;
  return {
    enqueue(send) {
      const result = tail.then(send);
      tail = result.catch((error) => { failure = error; });
      return result;
    },
    async flush() {
      await tail;
      if (failure) {
        failure = null;
        throw new Error('최근 행동 기록을 서버에 전달하지 못했어요. 연결을 확인한 뒤 다시 보내 주세요.');
      }
    },
  };
}
