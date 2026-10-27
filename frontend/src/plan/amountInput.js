// 원 단위 정수는 Number로 바꾸지 않는다. 서버 long 범위를 넘는 입력은 AI에 보내지 않는다.
export function amountMessage(targets, amounts) {
  if (targets.length === 0) return null;
  const parts = [];
  for (const target of targets) {
    const value = amounts[target] ?? '';
    if (!/^[0-9]{1,19}$/.test(value) || BigInt(value) < 1n || BigInt(value) > 9223372036854775807n) return null;
    parts.push(`${target}에게 ${value}원`);
  }
  return parts.join(', ');
}
