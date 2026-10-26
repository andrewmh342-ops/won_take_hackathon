# won_take_hackathon
원테이크 해커톤

## Codex 프론트 골조

작업 공간: `C:\ITStudy\98_HACKATHON_CODEX`, 브랜치: `codex/frontend-skeleton`.
Claude 작업 공간과 파일을 공유하지 않는 Git worktree입니다. React 19.3.1, Vite 8.3.5를 사용하며 라우팅·아이콘 라이브러리는 추가하지 않았습니다.

```powershell
cd C:\ITStudy\98_HACKATHON_CODEX\frontend
npm install
npm run dev
```

브라우저에서 http://localhost:5174 를 엽니다. Claude의 5173과 나란히 비교할 수 있습니다. 포트가 사용 중이면 임의의 다른 포트로 넘어가지 않고 오류를 표시합니다.

```powershell
npm run build
npm run preview
```

빌드 산출물은 `frontend/dist/`, 빌드 미리보기는 http://localhost:4174 입니다.

### 가짜 응답 모드와 서버 연결

- `VITE_USE_MOCK` 기본값은 `true`입니다. 백엔드 없이 화면을 확인할 수 있습니다. 가짜 모드의 질문·음성·대화는 고정 응답이며 실제 AI 분석이 아닙니다.
- 실제 서버 연결: `frontend/.env.local`에 `VITE_USE_MOCK=false`를 적고 개발 서버를 다시 시작합니다. `/api` 요청은 `http://localhost:8080`으로 전달됩니다. 키는 프론트에 넣지 않습니다.
- 동의·재진입 횟수·가짜 상담 접수는 새로고침 시 초기화합니다. `다시 띄우지 않기`만 `wontake-demo-never`라는 localStorage 키에 보관합니다. 재시연하려면 개발자 도구 콘솔에서 `localStorage.removeItem('wontake-demo-never')` 실행 후 새로고침합니다.
- 처음 동의 후 전체 메뉴 → 즉시/예약이체 → 전체 메뉴를 오가며 60초 안에 이체 화면에 세 번 진입하면 고정 질문 세 개가 나옵니다. 질문을 선택하면 대화 빈자리에 선택 문구가 전달됩니다.
- 메뉴 하이라이트: 개발자 도구에서 `highlightMenu('all-accounts')` 또는 휴대폰 틀 아래 개발 확인 버튼. 전체 메뉴로 이동·스크롤한 뒤 3초 강조합니다. 개발 확인 버튼과 전역 함수는 프로덕션 빌드에 노출하지 않습니다.
- 로그인·알림·홈 이외 하단 탭·미구현 메뉴 바로가기는 비활성 자리입니다. 이체의 다음 버튼은 시연 안내만 띄웁니다.

### 팀원이 이어받을 자리

| 담당 | 파일 / 연결점 |
|---|---|
| 역할 2 | `frontend/src/screens/ChatScreen.jsx`: `useNavigation().params`의 `intentId`, `question` 활용 |
| 역할 2 | `frontend/src/screens/AgentScreen.jsx`: 휴대폰 틀 밖 PC 상담 화면 |
| 역할 2 | `frontend/src/audio/recorder.js`: `startRecording`, `stopRecording`은 미구현 오류 발생 |
| 역할 2 | `frontend/src/components/Popup.jsx`: 제목·children·버튼 목록·onClose를 받는 공용 틀 |
| 역할 3·4 | `frontend/src/api/client.js`: SPEC 2.4 계약에 맞춘 호출, 서버 연결 모드로 검증 필요 |
| 역할 3 | `frontend/src/data/menu.js`: 백엔드 메뉴 ID를 동일하게 유지. 프론트는 `screen`, 서버 카탈로그는 SPEC의 `screenId` 사용 |

### 확인 결과 (2026-10-26)

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
