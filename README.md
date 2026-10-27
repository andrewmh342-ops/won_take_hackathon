# won_take_hackathon

우리 WON 청년 IT 아카데미 AX 해커톤(2026-10-26~28) 프로젝트. 금융앱을 쓰다 막힌 고객이 말 한마디로 물으면, AI가 고객의 상태·행동 기록·이전 대화를 보고 맞춤 해결 순서(원테이크 플랜)를 만들어 끝까지 안내합니다. 요구사항과 설계는 [SPEC.md](SPEC.md), 작업 기록은 [PROGRESS.md](PROGRESS.md)에 있습니다.

## 프로토타입 실행 방법

WON테이크는 **백엔드(Spring Boot, 8080)** 와 **프런트(React + Vite, 5173)** 두 개를 함께 켜서 씁니다. 은행·고객·카드·금액은 모두 가상이고, 실제 거래는 일어나지 않습니다.

### 준비물

| 항목 | 버전·내용 |
|---|---|
| Windows PC + PowerShell | 실행 스크립트가 PowerShell용입니다 |
| Java | 21 (`JAVA_HOME` 또는 PATH에 있어야 함) |
| Node.js | 20.19 이상 또는 22.12 이상 (Vite 8 요구 사항) |
| 브라우저 | PC Chrome (음성 질문은 Chrome 음성 인식을 씁니다) |
| Claude API 키 | 선택. 없으면 모의 AI 모드로 실행합니다 |

> 처음 실행할 때 Gradle·npm이 필요한 파일을 내려받으므로 인터넷 연결이 필요합니다.
> 이 PC처럼 Java 경로가 따로 있으면 `backend/.java-home`(Java 경로 한 줄), `backend/.gradle-user-home`(Gradle 캐시 경로 한 줄)에 적어 두면 스크립트가 읽어 씁니다. 두 파일은 Git에 올라가지 않습니다.

### 1. 처음 한 번: 프런트 의존성 설치

```powershell
cd C:\won_take_hackathon\frontend
npm install
```

### 2. 백엔드 켜기 (터미널 1, 저장소 루트에서)

모의 AI 모드 (키 없이 흐름 확인용, 답변은 미리 정한 결정적 예시):

```powershell
cd C:\won_take_hackathon
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-backend.ps1 -Fake
```

Claude 모드 (실제 AI 판단, 크레딧 사용):

```powershell
cd C:\won_take_hackathon
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-backend.ps1 -Claude
```

- `Claude API key (hidden input):`이 나오면 키를 붙여 넣고 Enter. 입력한 글자는 화면에 보이지 않는 게 정상입니다. 키는 파일에 저장되지 않습니다.
- 로그에 `Tomcat started on port 8080`이 나오면 준비된 것입니다.
- 확인: 브라우저에서 http://127.0.0.1:8080/api/health → `{"status":"UP","aiMode":"FAKE"}` 또는 `"CLAUDE"`
- 끄기: 그 터미널에서 `Ctrl + C`

### 3. 프런트 켜기 (터미널 2)

```powershell
cd C:\won_take_hackathon\frontend
npm run dev
```

PC Chrome에서 **http://localhost:5173** 을 엽니다. 화면 오른쪽 위에 지금 모드(`모의 AI · 서버 연결됨` / `Claude 연결 모드`)가 표시됩니다.

백엔드 없이 화면만 보려면 `frontend/.env.local`에 `VITE_USE_MOCK=true`를 넣고 `npm run dev`를 다시 실행합니다(고정 예시 답변, 원테이크 플랜은 간단한 모의판).

### 4. 화면에서 해 볼 것 (시연 순서)

1. **동의 화면:** [동의하고 시작하기]. 동의해야 행동 기록과 도움 말풍선(R1)이 동작합니다. 동의하지 않아도 AI 대화는 됩니다.
2. **도움 말풍선(R1):** 전체 메뉴(☰)에서 같은 분류의 서로 다른 메뉴 두 개를 각각 10초 안에 들어갔다 나옵니다(예: 즉시/예약이체 → 해외송금). 오른쪽 아래 말풍선을 누르면 대화가 열리고, 역질문 아래 **예상 질문 3개**가 버튼으로 나옵니다.
3. **원테이크 플랜(R5) 1막:** 상단 [AI]를 누르고 `나 일본 여행 가`
   → 대화의 요약 카드에서 [지금 해결하기] → 위 단계 탭을 따라 출국일 → 트래블카드 확인 → 해외결제 켜기 → 환전 신청 → 마무리.
4. **2막:** 같은 대화에서 `나 카드 잃어버렸어` → 잃어버린 카드를 고르고 카드 정지 → 재발급 → 남은 카드 해외결제 켜기. 1막에서 알려 준 나라·출국일은 다시 묻지 않습니다. 위쪽 [이유]를 누르면 AI가 이렇게 정한 이유를 볼 수 있습니다.
5. **대화로 이체:** `월세 65만원 내고, 엄마한테 30만원 보내줘` → 작업 목록 확인 → 가상 간편 비밀번호로 승인(기본값 `123456`, 시연용 가상 값).
6. **상담 연결(R3):** 상단 [상담] → 요약 확인 → [확인 후 전달] → 접수 안내의 [상담사 화면에서 확인] (전체 메뉴 맨 아래 [상담사 화면 미리보기]로도 열 수 있습니다).
7. **시연 고객 바꾸기:** 전체 메뉴 맨 아래 [고객 A/B/C] (개발 실행에서만 보입니다). 같은 질문이 고객마다 다르게 조립되는 것을 비교할 수 있습니다. 바꾸면 그 고객의 상태가 처음으로 돌아갑니다.

시연 대본과 Claude 모드 확인 항목은 [docs/claude-local-test.md](docs/claude-local-test.md)를 보세요.

### 환경 변수 (백엔드, 필요할 때만)

| 이름 | 기본값 | 설명 |
|---|---|---|
| `AI_PROVIDER` | `auto` | `fake` 모의 AI / `claude` 실제 AI (`-Fake`·`-Claude`가 자동으로 넣음) |
| `ANTHROPIC_API_KEY` | (없음) | Claude 키. 미리 넣어 두면 스크립트가 묻지 않습니다 |
| `ANTHROPIC_MODEL` | `claude-sonnet-5-5` | 사용할 Claude 모델 |
| `AI_MAX_CALLS` | `500` | 서버를 켠 뒤 Claude 호출 총 상한(크레딧 보호) |
| `ANTHROPIC_FALLBACKS` | `default` | 서버 측 대체 모델 재시도. 계정에서 400이 나면 `off` |
| `PLAN_PASSWORD` | `123456` | 대화 이체 승인용 가상 간편 비밀번호(시연 전용) |

### 테스트

```powershell
cd C:\won_take_hackathon\backend
$env:JAVA_HOME=(Get-Content .java-home -Raw).Trim()   # .java-home이 있을 때
$env:GRADLE_USER_HOME=(Get-Content .gradle-user-home -Raw).Trim()
.\gradlew.bat test
```

```powershell
cd C:\won_take_hackathon\frontend
node --test src/audio/*.test.js src/api/*.test.js src/chat/*.test.js src/data/*.test.js src/demo/*.test.js src/plan/*.test.js
npm run build
```

### 자주 막히는 문제

| 증상 | 원인과 해결 |
|---|---|
| `Port 8080 was already in use` | 예전 백엔드가 켜져 있습니다. 그 터미널에서 `Ctrl + C`, 또는 `Stop-Process -Id (Get-NetTCPConnection -LocalPort 8080 -State Listen).OwningProcess` |
| `-NoProfile` 용어가 인식되지 않음 | 명령 맨 앞의 `powershell`이 빠졌습니다 |
| 명령 뒤에 `>>`만 나옴 | 붙여 넣을 때 따옴표 등이 섞였습니다. `Ctrl + C` 후 직접 입력 |
| 말풍선이 안 뜸 | 동의했는지, 같은 분류의 **서로 다른** 메뉴 두 개를 각각 10초 안에 오갔는지 확인. 같은 메뉴 반복은 세지 않습니다. 분석은 60초에 한 번입니다. [다시 띄우지 않기]를 눌렀다면 개발자 도구 콘솔에서 `localStorage.removeItem('wontake-demo-never')` 후 새로고침 |
| "질문이 너무 빨라요" | 같은 대화에서 1분에 6번까지 질문할 수 있습니다. 잠시 뒤 다시 |
| 새로고침했더니 처음으로 돌아감 | 시연용이라 대화·고객 상태를 서버 메모리에만 둡니다. 1막·2막은 새로고침 없이 이어서 하세요 |
| 오른쪽 위가 `서버 연결 확인 필요` | 백엔드가 꺼져 있거나 8080이 아닌 곳에서 켜졌습니다 |

---

> 아래는 개발 중에 남긴 프런트 작업 메모입니다. 실행 방법은 위 "프로토타입 실행 방법"이 기준입니다.

## 프론트엔드

React 19.3.1, Vite 8.3.5를 사용하며 라우팅·아이콘 라이브러리는 추가하지 않았습니다.

```powershell
cd frontend
npm install
npm run dev
```

브라우저에서 http://localhost:5173 을 엽니다(SPEC 2.1). 포트가 사용 중이면 다른 포트로 넘어가지 않고 오류를 표시합니다.

```powershell
npm run build
npm run preview
```

빌드 산출물은 `frontend/dist/`, 빌드 미리보기는 http://localhost:4173 입니다.

### 가짜 응답 모드와 서버 연결

- `VITE_USE_MOCK` 기본값은 `false`입니다. 기본 실행에는 백엔드가 필요합니다. 백엔드 없이 기존 화면만 확인하려면 `frontend/.env.local`에 `VITE_USE_MOCK=true`를 설정하고 프런트를 재시작합니다. 이 독립 모드는 기존 고정 예시 답변이며 실제 AI 분석이 아닙니다. 음성→글자는 두 모드 모두 Chrome Web Speech API를 사용합니다.
- `/api` 요청은 `http://127.0.0.1:8080`으로 전달됩니다. 백엔드 키가 없으면 모의 AI, 키가 있으면 Claude 모드로 실행할 수 있습니다. 키는 프론트에 넣지 않습니다.
- 동의·재진입 횟수·가짜 상담 접수는 새로고침 시 초기화합니다. `다시 띄우지 않기`만 `wontake-demo-never`라는 localStorage 키에 보관합니다. 재시연하려면 개발자 도구 콘솔에서 `localStorage.removeItem('wontake-demo-never')` 실행 후 새로고침합니다.
- 도움 말풍선은 동의한 뒤 같은 분류의 서로 다른 메뉴를 각각 10초 안에 오가면 뜹니다(SPEC R1, 위 "화면에서 해 볼 것" 2번). 말풍선을 누르면 AI 대화가 열리고 역질문과 예상 질문 3개가 버튼으로 나옵니다.
- 휴대폰 틀은 브라우저 높이에 맞춰 줄어들고(최대 844px), 틀 안에서만 스크롤됩니다. 말풍선은 스크롤과 상관없이 같은 자리에 있습니다.
- 뒤로 버튼은 바로 전 화면으로 돌아갑니다. 홈으로 가면 지나온 기록을 비웁니다.
- 로그인: 홈의 로그인 → 간편비밀번호 아무 숫자 6자리. 서버·DB 없이 화면 안에서만 유지되고 새로고침하면 로그아웃됩니다. 키패드 버튼은 클릭 기록에 남기지 않습니다.
- 검색: 홈 검색창에서 검색하면 전체 메뉴로 이동해 결과를 보여 줍니다. 메뉴 이름과 분류 이름으로 찾습니다.
- 전체 메뉴 위쪽 분류 바로가기(이체/출금, 계좌조회/관리, 인증/보안, 증명서/서류, 환전)를 누르면 그 분류로 스크롤합니다.
- 메뉴 하이라이트: 개발자 도구에서 `highlightMenu('all-accounts')` 또는 휴대폰 틀 아래 개발 확인 버튼. 전체 메뉴로 이동·스크롤한 뒤 3초 강조합니다. 개발 확인 버튼과 전역 함수는 프로덕션 빌드에 노출하지 않습니다.
- 알림·홈 이외 하단 탭은 비활성 자리입니다. 이체의 다음 버튼은 시연 안내만 띄우고, 인증/보안·증명서/서류·환전 메뉴는 공통 시연 화면(`ServiceScreen`)을 씁니다.

### 팀원이 이어받을 자리

| 담당 | 파일 / 연결점 |
|---|---|
| 역할 2 | `frontend/src/screens/ChatScreen.jsx`: `suggestions` 또는 `intentId`·`question`을 이용한 대화, 음성 질문, 상담 요약 확인·전달 |
| 역할 2 | `frontend/src/screens/AgentScreen.jsx`: PC 상담 화면의 접수 목록, 요약, 클릭 기록, 대화 원문 |
| 역할 2 | `frontend/src/audio/speech.js`: Chrome Web Speech API 한국어 인식, 30초 자동 중지·취소·오류 처리 |
| 공용 | `frontend/src/components/Popup.jsx`: 요약 확인에 재사용하는 제목·본문·버튼 공용 팝업 |
| 역할 2 연결 / 역할 3·4 연동 | `frontend/src/api/client.js`: SPEC 2.4의 대화·요약·접수 호출 함수. 이전 `postVoice` 함수는 사용하지 않음. 실제 서버 모드는 백엔드와 연결해 검증 필요 |
| 역할 3 | `frontend/src/data/menu.js`: 백엔드 `menu.json`과 `id`·`category`·`name`·`screenId`를 똑같이 유지. 분류 5개, 분류당 4~5개 |

### 역할 2 대화·상담

- 대화 화면은 텍스트 질문과 말풍선 추천 질문을 `/api/chat` 계약에 맞춰 보냅니다. 응답의 `normalized`, `reply`, `steps`, `menuId`를 표시하며 실제 거래는 실행하지 않습니다.
- 음성 질문은 PC Chrome Web Speech API로 최대 30초 동안 한국어(`ko-KR`)를 인식합니다. 확정된 글자만 `/api/chat`에 `inputType: VOICE`로 전달합니다. WAV 변환·`/api/voice`·네이버 인증 키는 사용하지 않습니다. 기존 WAV 모듈과 테스트는 보존했습니다.
- 상단 AI 버튼 왼쪽의 [상담] 버튼은 어느 화면에서든 항상 누를 수 있습니다. 금융앱 이용 불편 질문인데 AI가 상담을 권하거나(`handoffSuggested`) 찾지 못함이 연속 2번이면 노란색으로 강조합니다.
- **원테이크 플랜(R5):** "지갑 잃어버렸어요", "일본 여행 준비할 거 알려줘"처럼 사건을 말하면 AI가 블록 사전 안에서 필요한 업무(분실신고·간편결제 해제·재발급·자동결제 변경, 환율·해외결제·트래블카드·환전·보험 안내)를 골라 급한 순서로 한 화면에 조립합니다. 버튼은 확인 후 가상 상태만 바꾸고, 사전에 없는 일은 상담사에게 넘깁니다. 전체 메뉴 아래 개발용 [고객 A/B] 버튼으로 시연 고객을 바꿀 수 있습니다.
- AI 대화는 금융앱 이용 불편 질문만 답합니다. 날씨·잡담·코딩 같은 범위 밖 질문은 고정 안내로 거절하고, 질문 길이·횟수와 Claude 호출 총량에 상한을 둡니다(SPEC R2, `docs/claude-local-test.md`). 버튼을 누르면 서버가 `AiClient.summarize`로 네 항목 요약을 만들고(FAKE 모드는 기록 기반 요약), 고객이 네 항목을 확인하고 [확인 후 전달]을 누른 경우에만 `/api/handoff`를 호출합니다. [취소]는 접수 API를 호출하지 않습니다.
- 상담사 화면은 `/api/handoff/tickets`에서 받은 접수 목록과 선택한 티켓의 요약, 클릭 기록, 대화 원문을 보여 줍니다.
- 가짜 응답 모드에서는 AI 답변·표준어 정리·상담 요약이 예시입니다. 음성 인식은 Chrome 서비스에 의존하며, 실제 발화 인식 여부는 사용자 PC에서 별도로 확인해야 합니다.
- 음성 인식·기존 WAV 단위 테스트: `cd frontend` 후 `node --test src/audio/*.test.js`

### Chrome에서 음성을 글자로 확인하기

1. 의존성이 없으면 `cd frontend` 후 `npm ci`를 실행합니다.
2. `npm run dev`로 실행하고 PC Chrome에서 로컬 개발 서버(기본 5173)를 엽니다. 외부 배포 시 HTTPS가 필요합니다.
3. 동의 화면을 지나 **AI 길잡이** → **마이크**를 누르고 마이크 권한을 허용합니다. 클릭 기록 동의 여부와 마이크 권한은 별개입니다.
4. “이체 한도가 얼마인가요”라고 말하고 **말하기 끝**을 누릅니다. 또는 인식 시작 후 최대 30초에 자동 중지됩니다. 브라우저가 먼저 인식을 종료할 수도 있습니다.
5. 대화의 **들은 말** 아래에 인식된 문장이 표시됩니다. 기본 가짜 응답 모드라면 뒤따르는 AI 답변·표준어 정리는 시연용입니다. 네이버 API 키를 등록할 필요가 없습니다.

마이크를 누르면 음성이 Chrome의 외부 음성 인식 서비스에서 처리될 수 있습니다. 인터넷 연결이 필요하고 브라우저 지원·서비스 상태에 따라 실패할 수 있습니다. 별도 유료 API 키 없이 사용하지만 무제한 제공이나 서비스 가용성을 보장하지 않습니다. 권한 거부·네트워크 오류·빈 결과·미지원 환경에서는 글자 입력으로 계속할 수 있습니다. 권한 응답을 10초 기다린 후 취소하며, 인식 중지 후 3초 동안 종료 응답이 없으면 확정된 글자만 사용합니다. 화면을 떠나면 음성 인식을 취소하고 결과를 전송하지 않습니다.

현재 검증: 음성 인식 모듈 자동 테스트 16개 + 기존 WAV 테스트 3개 통과. 실제 Chrome 서비스와 사람의 발화 정확도·사투리 품질은 미검증입니다. 아래 골조 확인 결과는 이전 구현 시점의 기록입니다.

### 골조 확인 결과 (2026-10-26)

프론트 자동 테스트는 SPEC 작업 2에 따라 추가하지 않았습니다. 브라우저 수동 확인 9개 통과, 최종 실패 0개:

1. 첫 진입 동의 화면 및 동의 후 홈 표시
2. 홈 → 전체 메뉴 → 이체 화면 이동
3. 60초 이내 이체 화면 3회 진입 시 질문 3개 표시
4. 질문 선택 시 대화 화면에 질문 전달
5. 다시 띄우지 않기 이후 재진입해도 말풍선 없음
6. 전체계좌조회 메뉴 이동·스크롤·강조 (공개 함수와 동일한 개발 버튼으로 확인)
7. 이체 다음 버튼은 실제 거래 없이 시연 팝업 표시
8. 가상 계좌 2개 및 원 단위 잔액 표시
9. 전체 메뉴에서 동의 변경 후, 동의하지 않아도 AI 대화 진입 가능

`npm install` 및 `npm run build` 성공. 실제 백엔드 API, 실제 AI·음성 인식, 역할 2 화면 내용은 이번 골조 범위 밖이며 검증하지 않았습니다.
