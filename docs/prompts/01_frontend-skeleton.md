# 프롬프트: 프론트 골조 (작업 2, 역할 1)

> 사용법: 아래 `=== 시작 ===`부터 `=== 끝 ===`까지를 AI(Claude Code, Codex)에 붙여 넣습니다.
> 실행 위치: 저장소 루트(`won_take_hackathon/`). main에 승인된 SPEC이 합쳐진 뒤 실행합니다.

=== 시작 ===

너는 WON테이크 해커톤 프로젝트의 **프론트 골조**를 만든다. SPEC.md 4장의 작업 2이고, 나는 역할 1(프론트 · 앱 화면)이다.
시작 전에 AGENTS.md, SPEC.md, PROGRESS.md를 읽고, 마지막 PROGRESS 항목과 `git log --oneline -5`를 확인한다.

## 목표
다른 팀원 3명이 이 골조 위에서 각자 자기 파일만 채워 병합할 수 있게 만든다.
- 공용 부분은 **완성**한다.
- 역할 1(나)의 화면은 정적으로 80% 수준까지 만든다.
- 다른 역할의 화면은 **빈 자리**만 만들고 라우팅만 연결한다.
- 백엔드가 없어도 돌아가도록 **가짜 응답 모드**를 만든다.

## 하지 말 것
- `backend/` 폴더를 만들거나 고치지 않는다 (역할 3 담당)
- 대화·녹음·요약 팝업·상담사 화면의 실제 내용을 만들지 않는다 (역할 2 담당)
- 새 라이브러리를 추가하지 않는다. 필요하면 멈추고 나에게 묻는다. 라우팅·아이콘 라이브러리도 쓰지 않는다
  - 라우팅은 React 상태로 현재 화면을 바꾸는 간단한 방식으로 만든다
  - 아이콘은 글자나 CSS 도형으로 대신한다
- 토스·우리WON뱅킹의 이미지, 로고, 아이콘 파일을 쓰지 않는다 (AGENTS.md 디자인 규칙)
- 실제 사람 이름, 계좌번호를 쓰지 않는다

## 기술
- `frontend/`에 React 19 + Vite, JavaScript로 만든다 (`npm create vite@latest frontend -- --template react`)
- Vite 개발 서버 프록시: `/api` → `http://localhost:8080`
- 가로 390px 휴대폰 틀을 화면 가운데에 둔다. 반응형은 하지 않는다

## 폴더 구조
```
frontend/src/
  styles/      tokens.css       색·글꼴·여백 변수 (공용, 완성)
  layout/      PhoneFrame, TopBar, BottomTab (공용, 완성)
  components/  MenuList, Bubble, Popup (공용, 완성)
  navigation/  화면 상태와 navigate(screen, params) (공용, 완성)
  session/     getUserId(), getSessionId() (공용, 완성)
  api/         client.js, mock.js (공용, 완성)
  data/        menu.js (공용, 아래 "메뉴 데이터" 참고)
  tracking/    화면 진입·클릭 전송 (역할 1)
  screens/     HomeScreen, MenuScreen, TransferScreen, AccountScreen, ConsentScreen (역할 1)
  screens/     ChatScreen, AgentScreen (빈 자리, 역할 2)
  audio/       recorder.js (빈 자리, 역할 2)
```
각 파일 맨 위에 담당 역할을 한 줄 주석으로 적는다. 예: `// 담당: 역할 2 — 대화 화면`

## 공용 부분 (완성)

**디자인 값 (`styles/tokens.css`)**
- AGENTS.md 디자인 규칙을 따른다: 큰 글씨, 넓은 여백, 강조색 하나
- 색: 배경, 카드 배경, 본문 글자, 보조 글자, 강조색(파랑 계열 하나), 경고색, 테두리
- 글자 크기: 제목, 본문(16px 이상), 보조
- 모든 화면은 이 변수만 쓴다

**화면 이동 (`navigation/`)**
- `navigate(screen, params)`와 현재 화면·params를 주는 Context
- 화면 이름: `home`, `menu`, `transfer`, `account`, `consent`, `chat`, `agent`
- 대화 화면으로 갈 때 params: `{ intentId, question }` (말풍선에서 고른 질문, 없으면 비움)
- 화면이 바뀔 때마다 `tracking`의 화면 진입 함수를 부른다

**휴대폰 틀 (`layout/`)**
- `TopBar`: 왼쪽 "WON" 글자 로고, 오른쪽 AI 버튼(→ chat), 알림(동작 없음), 햄버거(→ menu)
- `BottomTab`: 상품 · 자산소비 · 홈 · 주식 · 혜택. 홈만 동작
- `agent` 화면은 휴대폰 틀 밖, PC 화면 크기로 보여 준다

**메뉴 목록 (`components/MenuList`)**
- `data/menu.js`를 분류(`category`)별로 그린다
- 각 항목에 `data-menu-id`와 `data-track-id`를 붙인다
- `highlightMenu(menuId)`: 그 항목으로 스크롤하고 3초 동안 강조 표시. 다른 화면에서도 부를 수 있게 export 한다
- 항목을 누르면 그 항목의 `screen`으로 이동한다

**말풍선 (`components/Bubble`)**
- 화면 아래쪽에 뜨는 작은 말풍선. 화면을 가리지 않는다
- 질문 최대 3개를 버튼으로 보여 주고, [닫기]와 [다시 띄우지 않기] 버튼을 둔다
- 질문을 누르면 `navigate("chat", { intentId, question })`
- [닫기]·[다시 띄우지 않기]를 누르면 `api.closeBubble(screenId, "CLOSE" | "NEVER")` 후 말풍선을 닫는다

**팝업 (`components/Popup`)**
- 제목, 본문, 버튼 목록을 받는 공용 팝업 틀 (역할 2가 요약 확인 팝업에 쓴다)

**세션 (`session/`)**
- `getUserId()`: 고정값 `"demo-user"`
- `getSessionId()`: 앱을 열 때 한 번 만든 임의 문자열

**API 호출 (`api/client.js`)**
SPEC.md 2.4의 API를 모두 함수로 만든다. 요청·응답 모양은 SPEC.md 2.4와 똑같이 한다.
- `getConsent()`, `putConsent(agreed)`
- `postEvent({ type, screenId, elementId })`
- `closeBubble(screenId, action)`
- `postVoice(wavBlob)`, `postChat({ message, inputType, intentId })`
- `getHandoffSummary()`, `postHandoff(summary)`, `getTickets()`

**가짜 응답 모드 (`api/mock.js`)**
- 환경 변수 `VITE_USE_MOCK=true`이면 서버를 부르지 않고 SPEC.md 2.4 모양의 고정 응답을 돌려준다. 기본값은 `true`
- 고정 응답 값:
  - `getConsent` → 처음엔 `{ agreed: null }`, `putConsent` 후에는 그 값
  - `postEvent` → 평소에는 `{ bubble: null }`. 같은 화면에 60초 안에 3번째로 들어오면 아래 말풍선을 돌려준다(가짜 모드에서만 화면에서 간단히 셈)
    ```json
    { "bubble": { "screenId": "transfer", "suggestions": [
      { "intentId": "transfer-limit", "question": "내 이체한도가 궁금하신가요?" },
      { "intentId": "overseas-remittance", "question": "해외송금을 찾고 계신가요?" },
      { "intentId": "domestic-transfer", "question": "국내송금을 찾고 계신가요?" } ] } }
    ```
  - `postChat` → `{ type: "ANSWER", normalized: "", reply: "(가짜) 이체한도는 계좌이체 화면에서 확인할 수 있어요", intentId: "transfer-limit", menuId: "instant-transfer", steps: [], notFoundStreak: 0 }`
  - `postVoice` → `{ recognizedText: "(가짜) 이체 한도가 우째 되능교" }`
  - `getHandoffSummary` → 네 항목 모두 "(가짜)" 문구, `clickTimeline`은 빈 배열, `clickConsent: true`
  - `postHandoff` → `{ ticketId: "T-1", createdAt: 현재 시각 }`, `getTickets` → 그동안 접수된 목록

**메뉴 데이터 (`data/menu.js`)**
SPEC.md 2.4에는 메뉴 목록 API가 없으므로, 골조에서는 프론트에 같은 모양의 데이터를 둔다. id는 역할 3의 `menu.json`과 똑같이 맞춘다.
```js
[
  { id: "instant-transfer",    category: "이체/출금",     name: "즉시/예약이체",   screen: "transfer" },
  { id: "multi-transfer",      category: "이체/출금",     name: "다건이체",       screen: "transfer" },
  { id: "transfer-history",    category: "이체/출금",     name: "이체내역조회",    screen: "account" },
  { id: "overseas-remittance", category: "이체/출금",     name: "해외송금",       screen: "transfer" },
  { id: "atm-withdrawal",      category: "이체/출금",     name: "ATM/영업점 출금", screen: "transfer" },
  { id: "all-accounts",        category: "계좌조회/관리",  name: "전체계좌조회",    screen: "account" },
  { id: "closed-accounts",     category: "계좌조회/관리",  name: "해지계좌조회",    screen: "account" },
  { id: "auth-center",         category: "인증/보안",     name: "인증센터",       screen: "menu" }
]
```

## 역할 1 화면 (정적, 80% 수준)
- `HomeScreen`: 배너 자리(색 상자와 문구), 로그인 버튼 자리, 서비스 바로가기 8칸(글자), "유용한 서비스" 카드 3개 자리
- `MenuScreen`: 맨 위 검색창 + 마이크 버튼(누르면 `navigate("chat")`, 녹음은 역할 2가 붙임), 바로가기 4칸(인증/보안, 증명서/서류, 환전, 전담직원), 그 아래 `MenuList`
- `TransferScreen`: 출금 계좌(가상), 받는 사람 입력칸, 금액 입력칸, [다음] 버튼. **실제 이체는 하지 않는다.** [다음]은 "시연용 화면입니다" 팝업만
- `AccountScreen`: 가상 계좌 카드 2개(잔액은 원 단위 정수), 거래내역 자리
- `ConsentScreen`: SPEC R4 문구로 동의 요청, [동의]/[동의하지 않음]. 앱을 처음 열 때 `getConsent()`가 null이면 이 화면부터 보여 준다

## 클릭 기록 (`tracking/`, 역할 1)
- 화면이 바뀔 때 `postEvent({ type: "SCREEN_ENTER", screenId })`
- `data-track-id`가 붙은 요소를 누를 때 `postEvent({ type: "CLICK", screenId, elementId })`
- 응답에 `bubble`이 있으면 `Bubble`을 띄운다
- 동의하지 않았으면 아무것도 보내지 않는다 (서버도 막지만 프론트에서도 막는다)

## 빈 자리 (역할 2 몫)
- `ChatScreen`: 제목 "AI 대화", params로 받은 질문을 한 줄 보여 주고, "역할 2가 만들 자리" 상자
- `AgentScreen`: 제목 "상담사 화면", "역할 2가 만들 자리" 상자
- `audio/recorder.js`: `startRecording()`, `stopRecording()` 함수 이름만 두고 "미구현" 오류를 던진다

## 끝났을 때 확인 (전부 통과해야 완료)
- [ ] `cd frontend; npm install; npm run dev`로 실행되고, 가짜 응답 모드에서 백엔드 없이 동작한다
- [ ] 처음 열면 동의 화면이 뜨고, 동의 후 홈으로 간다
- [ ] 홈 → 햄버거 → 전체 메뉴 → 항목 클릭 → 해당 화면 이동이 된다
- [ ] 계좌이체 화면에 60초 안에 3번 들어가면 말풍선이 뜨고, 질문을 누르면 대화 화면에 그 질문이 보인다
- [ ] 말풍선 [다시 띄우지 않기] 후에는 다시 뜨지 않는다 (가짜 모드 기준)
- [ ] 브라우저 콘솔에서 `highlightMenu("all-accounts")`를 부르면 전체계좌조회가 강조된다 (개발 중에는 window에 붙여 둔다)
- [ ] `npm run build`가 오류 없이 끝난다
- [ ] README.md에 프론트 실행 방법과 `VITE_USE_MOCK` 설명을 적었다

## 작업 방식
- `feature/frontend-skeleton` 브랜치에서 작업한다
- 작은 단위로 커밋하고, 메시지 앞에 `[claude]` 또는 `[codex]`를 붙인다
- 프론트에는 자동 테스트가 없으므로(SPEC 2.1), 위 확인 항목을 직접 실행해 결과를 적는다
- 끝나면 PROGRESS.md에 기록하고, 확인하지 못한 항목은 통과라고 쓰지 않는다
- 마지막에 PR 설명에 쓸 변경 요약과, 다른 역할이 채울 자리 목록을 보여 준다

=== 끝 ===
