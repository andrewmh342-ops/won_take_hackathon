# 프롬프트: 업무 블록 조립 엔진 (카드 분실 · 해외여행 준비)

> 사용법: 아래 `=== 시작 ===`부터 `=== 끝 ===`까지를 AI(Claude Code, Codex)에 붙여 넣는다.
> 실행 위치: 저장소 루트, DongHa 브랜치(main과 같은 21d440e 이후). 실제 Claude 키 없이 FAKE 모드로 끝까지 동작해야 한다.

=== 시작 ===

너는 WON테이크의 **업무 블록 조립 엔진**을 만든다. 시작 전에 AGENTS.md, SPEC.md, PROGRESS.md 마지막 항목, `git log --oneline -5`, docs/claude-local-test.md를 읽는다.

## 0. 일하는 순서 (AGENTS.md)
1. 이 프롬프트를 바탕으로 **SPEC.md 수정안**을 만든다(R5 업무 블록 조립 + 2.3 데이터 + 2.4 API + 2.5 AI + 3장 테스트 + 4장 작업). 수정안을 나에게 보여 주고 "승인"을 받은 뒤에만 저장한다.
2. 승인 후 예시표 한 행당 테스트 하나를 먼저 쓰고, 실패를 확인한 뒤 구현한다.
3. 끝나면 PROGRESS.md에 항목을 추가하고 AGENTS.md "끝났을 때 보고" 형식으로 보고한다. 커밋 메시지는 `[claude]`/`[codex]`로 시작한다. push는 내가 요청할 때만 한다.

## 1. 무엇을 만드나
고객이 사건을 한마디로 말하면(글자·음성·검색어), AI가 **블록 사전 안에서** 필요한 업무 블록을 골라 급한 순서로 배치하고 이유를 붙인다. 서버는 그 결과를 검증하고 가상 고객 정보를 채워 "원테이크 플랜" 한 화면을 만든다. 고객은 블록 버튼을 눌러 하나씩 끝낸다.
- AI는 새 기능을 지어내지 않는다. 블록 ID는 enum으로 묶는다(지금 menuIds와 같은 방식).
- **순서는 AI가 정한다.** 고정 템플릿 순서를 코드에 두지 않는다. 서버는 꼭 지켜야 할 선후 관계만 검사한다.
- 블록 사전에 없는 일은 지어내지 않는다. `unhandled`로 돌려주고 플랜 끝에 **상담사 연결 블록**을 둔다.
- 실제 거래는 하지 않는다. 버튼은 확인 팝업 뒤 **가상 고객 상태**만 바꾼다.

## 2. 시나리오와 기대 (시연 확인용, 문구는 고정하지 않음)
**A. 카드 분실:** "지갑 잃어버렸어요, 카드 두 장 들어 있었어요"
- 기대 순서(예): 카드 분실신고(2장) → 간편결제 해제 → 카드 재발급 → 자동결제 카드 변경. 이유: 부정 사용 차단을 먼저.
- "신분증도 잃어버렸어요"처럼 사전에 없는 일 → unhandled + 상담사 연결 블록.
- 정형 대비: "카드 한 장 잃어버렸어" → 블록 수가 줄어듦.

**B. 해외여행 준비:** "다음 달 일본 여행 가는데 준비할 거 알려줘"
- 기대: 엔화 환율 확인 → 해외결제 설정 확인 → 트래블카드 확인(없으면 신청 화면) → 환전 신청(선택) → 여행자 보험 가입 안내.
- **같은 질문, 다른 고객 → 다른 조립:** 고객 A(트래블카드 있음, 해외결제 꺼짐)는 [해외결제 켜기]. 고객 B(트래블카드 없음)는 [트래블카드 신청]. 이 차이가 "고정 흐름이 아니다"를 보여 주는 핵심 장면이다.
- 지원하지 않는 통화(예: 몽골) → 환율 블록 대신 unhandled + 상담 블록.

## 3. 데이터 (backend/src/main/resources/data/)
**menu.json·frontend/src/data/menu.js에 메뉴 추가** (id·category·name·screenId를 두 파일에서 똑같이 둔다. catalog.test.js가 검사한다. description·guide·commonIssues·relatedMenuIds도 기존 형식으로 채운다)
- 새 분류 `카드` (screenId `card`, 공통 시연 화면 ServiceScreen 사용): `card-loss-report` 카드 분실신고, `card-reissue` 카드 재발급, `auto-payment` 자동결제 관리, `simple-pay` 간편결제 관리, `overseas-payment` 해외결제 설정, `travel-card-apply` 트래블카드 신청
- `환전` 분류에 `travel-insurance` 여행자 보험 가입 안내 추가(screenId `exchange`)
- DemoStore의 화면 화이트리스트에 `card`를 추가하고, main.jsx의 screens에 `card: ServiceScreen`을 추가한다.

**blocks.json (새)**: 블록 사전. 필드는 다음과 같다.
- `id`, `name`, `menuId`
- `type`: `ACTION`(가상 상태 변경) | `CHECK`(상태 보여 주기) | `LINK`(신청·안내 화면 이동) | `HANDOFF`
- `target`: `CARD` | `NONE`. 카드별 블록이면 대상 카드가 필요하다.
- `requires`: 같은 플랜에서 먼저 와야 할 블록 id들
- `skipWhen`: 이미 충족된 상태면 넣지 않음
- `effect`: ACTION이 바꾸는 상태 키
- `description`
블록 목록:
| id | type | target | requires | 하는 일 |
|---|---|---|---|---|
| card-lost-report | ACTION | CARD | - | 카드 사용 정지(status=STOPPED) |
| simple-pay-unlink | ACTION | CARD | - | 간편결제 등록 해제 |
| card-reissue | ACTION | CARD | card-lost-report | 재발급 신청(reissued=true) |
| auto-payment-switch | ACTION | CARD | card-reissue | 자동결제를 새 카드로 옮김(건별 해지 선택 가능) |
| fx-rate-check | CHECK | NONE | - | 통화 가상 환율 표시 |
| overseas-payment-check | ACTION | CARD | - | 해외결제 설정 확인·켜기 |
| travel-card-check | CHECK | NONE | - | 트래블카드 보유 확인 |
| travel-card-apply | LINK | NONE | - | 신청 화면 이동 (skipWhen: 트래블카드 보유) |
| fx-exchange-apply | LINK | NONE | fx-rate-check | 환전 신청 화면 이동 |
| travel-insurance-guide | LINK | NONE | - | 여행자 보험 가입 안내 화면 이동(상품 추천 없음) |
| counselor-handoff | HANDOFF | NONE | - | 남은 일을 상담사에게 전달 |

**profiles.json (새)**: 가상 고객 2명 A·B. 실제 사람·번호는 쓰지 않는다.
- 카드: `ref`, 이름(예: "우리 체크카드"), 끝자리(가상 4자리), 간편결제 등록 여부, 해외결제 여부, 자동결제 목록(분류·가맹점 가상 이름·금액 원 단위 long), 트래블카드 여부
- A: 카드 2장, 자동결제 3건(통신·OTT·보험), 간편결제 1장, 트래블카드 있음, 해외결제 꺼짐
- B: 카드 1장, 자동결제 1건, 트래블카드 없음
- 가상 환율표: 통화 코드 → 기준 단위·원 환산값(long 정수, 예 100엔 단위). 화면에 "시연용 가상 환율"로 표시한다. 사기 좋은 시점 같은 조언은 금지한다.
- 세션 상태는 서버 메모리(DemoStore 세션)에 프로필 복사본으로 두고, 새로고침하면 초기화한다. 시연 고객 전환: `PUT /api/demo/profile {userId, sessionId, profileId}`, 전체 메뉴에 개발용 [시연 고객 A/B] 전환 버튼(프로덕션 빌드에서는 숨김).

## 4. 가명화 (AI가 실제 정보를 보지 않게)
- AI에는 **모양만** 보낸다. 예: `cards:[{ref:"card-1", kind:"체크", simplePay:true, overseasPayment:false, autoPayments:["통신","OTT","보험"]}], hasTravelCard:true`. 이름·끝자리·금액·가맹점명은 보내지 않는다.
- AI는 `ref`만 돌려준다. 서버가 `ref`를 "우리 체크카드 끝자리 1234(가상)"로 복원해 화면에 채운다.
- 시연 투명성: 플랜 화면 아래 접히는 "AI에 보낸 정보" 영역에 가명화된 모양을 그대로 보여 준다(개발 확인용).

## 5. AI 계약 (기존 /api/chat 한 번의 호출로)
- 지금 Claude 답변 schema(scope, reply, normalized, menuIds, steps, handoff)에 `plan`을 추가한다. 항상 포함하고, 플랜이 아니면 빈 배열로 둔다.
  `plan: { events:[string], blocks:[{blockId(enum), targetRef(string, 없으면 ""), reason(string)}], unhandled:[string], orderReason:string }`
- chat-system.txt에 [업무 블록 조립] 절을 추가한다.
  - 고객 말에 처리할 사건이 있으면 사건을 나눠 events에 적고, blocks에서 필요한 블록만 골라 급한 순서(부정 사용·손실 차단 → 재발급·신청 → 편의)로 배치한다.
  - 각 블록에 한 문장 reason을 붙이고, orderReason에 순서 근거를 적는다.
  - 블록 사전에 없는 일은 unhandled에만 적고 블록을 지어내지 않는다. unhandled가 있으면 counselor-handoff를 마지막에 넣는다.
  - 이미 충족된 일(트래블카드 보유 등)은 넣지 않거나 CHECK로만 둔다.
  - 여행자 보험은 가입 안내만 한다. 특정 상품 추천·권유·보장 설명은 하지 않는다. 환율은 주어진 가상 값만 쓰고 전망·매수 시점 조언을 하지 않는다.
  - 범위 규제(scope)·사실 생성 금지 규정은 그대로 둔다.
- FakeAiClient: 키워드로 결정적 플랜을 만든다("잃어버/분실" → 카드 블록, "여행/일본/환전" → 여행 블록, "신분증" → unhandled). 연결 테스트용이라고 표시한다.

## 6. 서버 검증·복원 (새 패키지 `com.wontake.assist`: BlockCatalog, ProfileStore, PlanService, PlanController)
- blockId는 사전 안의 값이어야 한다. CARD 대상 블록은 targetRef가 고객이 가진 카드여야 하고, 아니면 그 블록만 버린다.
- (blockId, targetRef)가 중복이면 버린다. 블록은 최대 12개, reason은 120자, unhandled 항목은 100자 이내로 한다.
- skipWhen이 이미 충족된 블록은 버린다(예: 트래블카드 보유 시 travel-card-apply).
- **선후 관계:** requires가 플랜에서 뒤에 있으면, 그 블록을 필요한 블록 바로 뒤로 옮기고 `adjusted:true`로 표시한다. requires가 플랜에 아예 없으면 필요한 블록을 앞에 넣는다(예: 재발급만 왔으면 분실신고 추가). AI 순서를 최대한 유지한다.
- unhandled가 있는데 counselor-handoff가 없으면 끝에 추가한다.
- 남은 블록이 0개면 플랜 없이 기존 안내 답변만 보낸다.
- 플랜은 `planId`와 함께 세션에 저장하고 대화 기록(answer)에 포함한다. 화면 이동 후 돌아와도 다시 그린다.

## 7. 실행 API
- `POST /api/plan/execute {userId, sessionId, planId, index, options?}` → 갱신된 플랜
  - ACTION: requires가 완료되지 않았으면 409 `PLAN_PREREQUISITE`("먼저 ○○를 끝내 주세요"). 완료되면 가상 상태를 바꾸고 status=DONE으로 한다. 같은 블록을 다시 누르면 중복 처리 없이 같은 결과를 돌려준다.
  - auto-payment-switch는 options로 건별 `SWITCH`/`CANCEL`을 받는다(기본 SWITCH).
  - CHECK: 실행 없이 현재 상태를 보여 준다(DONE).
  - LINK: 프런트가 해당 menuId 화면으로 이동하고 서버에 VISITED를 기록한다.
  - HANDOFF: 프런트가 기존 상단 상담 흐름(openHandoff)을 연다.
- HandoffService 요약 입력에 플랜 진행(완료·남은 블록, unhandled)을 넣는다. 상담사 화면에 "처리한 일/남은 일"을 보여 준다.
- 실행 API도 UsageGuard처럼 세션당 분당 상한을 둔다(AI 호출은 없음).

## 8. 프런트
- ChatScreen: 답에 plan이 있으면 **원테이크 플랜 카드**를 그린다.
  - 맨 위: 사건 요약(events), orderReason 한 줄
  - 블록 목록: 순서 번호, 블록 이름, 대상(복원된 카드 이름), reason, 상태(대기/완료/선행 필요), 버튼
  - adjusted 블록에는 "순서 조정됨" 작은 표시
  - ACTION·CHECK 버튼은 공용 Popup으로 확인한 뒤 execute를 호출한다. 확인 문구는 "가상 시연이며 실제 카드·결제에는 영향이 없습니다"로 한다.
  - auto-payment-switch 팝업에서는 건별 변경/해지를 고른다.
  - 진행 표시: "5개 중 2개 완료". 모두 끝나면 "원테이크 완료"와 처리 요약.
- 공용 컴포넌트는 `components/PlanCard.jsx`로 만든다. 디자인은 AGENTS.md 규칙(큰 글씨, 강조색 하나, 노란 강조는 상담 추천에만)을 따른다.
- mock.js(VITE_USE_MOCK=true)에도 같은 모양의 결정적 플랜을 넣는다.

## 9. 테스트 (먼저 작성)
백엔드 JUnit:
- PlanService: 사전에 없는 blockId는 버린다 / 가지지 않은 카드 ref는 버린다 / 중복 블록 1개로 / 재발급이 분실신고보다 앞이면 뒤로 옮기고 adjusted / 재발급만 오면 분실신고 추가 / 트래블카드 보유 시 신청 블록 제외 / unhandled가 있으면 상담 블록이 끝에 / 남은 블록 0개면 플랜 없음 / 블록 13개면 12개
- 가명화: AI 입력에 끝자리·금액·가맹점명이 없다 / 화면 응답에는 복원된 카드 이름이 있다
- 실행: 선행 미완료 ACTION은 409 / 완료 후 실행하면 상태 변경 / 재실행 시 중복 없음 / 자동결제 건별 해지 반영 / 다른 세션 planId는 404
- 같은 여행 질문, 프로필 A·B → 서로 다른 블록 집합 (FAKE로 연결 검증)
- ClaudeAiClient: schema에 plan·blockId enum 포함, 응답 파싱, 형식 오류 거부
- 기존 테스트 66개 통과 유지(기대값 변경 금지)
프런트 Node: mock 플랜 모양이 백엔드 응답 모양과 같다, menu.js와 menu.json 일치(기존 테스트)

## 10. 하지 말 것
- 고정 순서 템플릿을 코드에 두지 않는다(순서는 AI, 서버는 선후 관계만).
- 실제 카드·결제·보험 API 호출, 실제 사람·카드번호, 실시간 환율, 보험 상품 추천을 하지 않는다.
- 새 라이브러리를 추가하지 않는다.
- 범위 규제, 사용량 상한, 상단 상담 버튼, 음성 인식, R1 말풍선 흐름을 깨지 않는다.

## 11. 끝났을 때
- docs/claude-local-test.md에 시연 순서를 추가한다: 카드 분실 정형/비정형, 여행 A/B 비교, 지원 안 되는 통화, 신분증 → 상담.
- 실제 Claude로 확인할 것(키 연결 후): 순서 이유가 타당한지, 사전 밖 블록을 지어내지 않는지, 보험 추천을 하지 않는지, 같은 질문에 A/B가 다르게 조립되는지.

=== 끝 ===
