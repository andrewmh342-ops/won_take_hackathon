# PROGRESS.md — 진행 기록

> 작업이 끝날 때마다 맨 아래에 항목을 추가합니다. 기존 항목은 고치거나 지우지 않습니다.
> 시작 전에 이 파일의 마지막 항목들과 `git log --oneline -5`를 읽습니다.

## 항목 형식

```
### YYYY-MM-DD HH:MM · [claude | codex | 사람 이름]
- 한 일:
- 바뀐 파일과 이유:
- 실행한 테스트와 결과 (통과·실패 개수):
- 확인하지 못한 것:
- 다음에 할 일:
```

---

### 2026-10-26 · [claude]
- 한 일: 프로젝트 틀 준비, 규칙 통합(AGENTS.md 기준, CLAUDE.md는 불러오기), PROGRESS.md 추가
- 바뀐 파일과 이유: AGENTS.md(공통 규칙), CLAUDE.md(@AGENTS.md), SPEC.md(빈 틀), TEAM_CONTEXT_PROMPT.md(팀 공유용 프롬프트)
- 실행한 테스트와 결과: 테스트 없음
- 확인하지 못한 것: Codex가 AGENTS.md를 실제로 읽는지, Claude가 @AGENTS.md로 불러오는지는 새 세션에서 확인 필요
- 다음에 할 일: SPEC.md 1장 작성(주제와 핵심 흐름 확정 후)

### 2026-10-26 11:26 · [codex]
- 한 일: ‘Codex 연결 확인’ — AGENTS.md를 읽고, 작업 시작 전 PROGRESS.md와 최근 커밋 기록을 확인한 뒤 이 기록을 추가함
- 바뀐 파일과 이유: PROGRESS.md (Codex가 공통 규칙을 읽고 따랐음을 기록)
- 실행한 테스트와 결과: 테스트 없음 (문서 기록 작업)
- 확인하지 못한 것: Claude Code가 CLAUDE.md를 통해 AGENTS.md를 불러오는지는 확인하지 못함
- 다음에 할 일: SPEC.md 1장 작성 (주제와 핵심 흐름 확정 후)

### 2026-10-26 · [claude]
- 한 일: 팀 공유용 프롬프트 맨 앞에 "AGENTS.md, SPEC.md, PROGRESS.md를 먼저 읽고, 충돌하면 파일이 우선"이라는 안내를 추가
- 바뀐 파일과 이유: TEAM_CONTEXT_PROMPT.md (자동 불러오기에 의존하지 않고 어떤 도구에서도 규칙을 읽게 하려고)
- 실행한 테스트와 결과: 테스트 없음 (문서 작업)
- 확인하지 못한 것: CLAUDE.md의 @AGENTS.md 자동 불러오기가 되는지는 아직 검증하지 못함
- 다음에 할 일: SPEC.md 1장 작성 (주제와 핵심 흐름 확정 후)

### 2026-10-26 · [claude]
- 한 일: 팀 아이디어 회의 결과로 기획서(Lean Canvas) 작성. 주제를 고객 중심 한 문장으로 일원화하고, 4개 기능을 고객 여정 3단계로 묶음
- 바뀐 파일과 이유: 기획서_LeanCanvas.md (새 파일, 사용자 승인한 초안), PROGRESS.md (기록)
- 실행한 테스트와 결과: 테스트 없음 (문서 작업)
- 확인하지 못한 것: 시장 규모 수치, 지표 숫자(모두 가정), 서비스 이름, 팝업 장면 순서
- 다음에 할 일: 팀 역할 분담, SPEC.md 1장 작성

### 2026-10-26 · [claude]
- 한 일: 팀이 정리한 최종 캔버스(서비스 이름 WON테이크)로 기획서를 갱신하고, 팀 공유 프롬프트의 프로젝트 설명을 맞춤
- 바뀐 파일과 이유: 기획서_LeanCanvas.md (최종 캔버스 반영, 데모 시나리오·AI 활용 표 갱신), TEAM_CONTEXT_PROMPT.md (3장을 WON테이크 확정안으로 교체, 미정 목록에서 서비스 이름 삭제)
- 실행한 테스트와 결과: 테스트 없음 (문서 작업)
- 확인하지 못한 것: ATM 출금 근거, 능동 팝업 빈도 규칙, 지표 수치(가정)
- 다음에 할 일: 팀 역할 분담, SPEC.md 1장 작성

### 2026-10-26 · [claude]
- 한 일: 저장소를 GitHub(origin)에 연결해 push, 강의 자료 원본을 .gitignore에 추가
- 바뀐 파일과 이유: .gitignore (강의 실습 카드 등 배포 금지 자료가 공개 저장소에 올라가지 않게)
- 실행한 테스트와 결과: 테스트 없음
- 확인하지 못한 것: 없음
- 다음에 할 일: 팀원 Collaborator 추가, 역할 분담, SPEC.md 1장 작성

### 2026-10-26 · [claude]
- 한 일: SPEC.md 1장(요구사항 R1~R4, 예시표 포함) 작성, 사용자 승인 후 저장
- 바뀐 파일과 이유: SPEC.md (1장 승인본 저장, 제목에 서비스 이름 반영), PROGRESS.md (기록)
- 실행한 테스트와 결과: 테스트 없음 (문서 작업)
- 확인하지 못한 것: 없음
- 다음에 할 일: SPEC.md 2장(설계: 버전·빌드 도구·API 계약·데이터 형식)

### 2026-10-26 · [claude]
- 한 일: 강사 주제 검토용 발표자료(5장) 작성. SPEC 1장(GitHub DongHa 브랜치) 기준으로 리뷰 근거 → 핵심 문제·대상 → 해결(R1~R4) → 이유 순서
- 바뀐 파일과 이유: WON테이크_주제검토.pptx (새 파일), PROGRESS.md (기록)
- 실행한 테스트와 결과: 테스트 없음 (문서 작업). 슬라이드 5장, 색상 형식, 글자 넘침 추정 검사 통과
- 확인하지 못한 것: 실제 화면 렌더링 (이 PC에 PowerPoint·LibreOffice가 없어 눈으로 확인하지 못함)
- 다음에 할 일: 강사 검토 반영, SPEC 2장 승인

### 2026-10-26 · [claude]
- 한 일: AGENTS.md에 디자인 규칙 추가 (토스 디자인 원칙만 참고, 토스·실제 앱 자산 사용 금지, 반응형 없음)
- 바뀐 파일과 이유: AGENTS.md (사용자 승인, 보호 파일), PROGRESS.md (기록)
- 실행한 테스트와 결과: 테스트 없음 (문서 작업)
- 확인하지 못한 것: 없음 (TDS 라이선스는 앱인토스 공식 문서로 확인)
- 다음에 할 일: SPEC 2장 수정본(화면 목록, menu.json 구조, 디자인 반영) 승인

### 2026-10-26 · [claude]
- 한 일: CLAUDE.md에 추가돼 있던 "모든 기능들을 최대한 모듈화 할 것"을 AGENTS.md의 "모듈화" 섹션으로 구체화해 옮기고, CLAUDE.md는 @AGENTS.md 한 줄로 되돌림
- 바뀐 파일과 이유: AGENTS.md, CLAUDE.md (사용자 승인, 규칙은 AGENTS.md 한 곳에만 둔다는 원칙), PROGRESS.md (기록)
- 실행한 테스트와 결과: 테스트 없음 (문서 작업)
- 확인하지 못한 것: CLAUDE.md에 그 줄을 누가 넣었는지
- 다음에 할 일: SPEC 2장 수정본 승인

### 2026-10-26 · [claude]
- 한 일: 강사 피드백과 팀 결정(R1 행동 의도 역제안, R2 음성·사투리, R3 문맥 요약 인계)을 반영해 SPEC.md 1~4장 새 초안 작성, 4명 역할 분담 포함. 동의(R4) 추가
- 바뀐 파일과 이유: SPEC.md (전 장 초안, 승인 대기. 이전 승인 1장은 대체됨), PROGRESS.md (기록)
- 실행한 테스트와 결과: 테스트 없음 (문서 작업)
- 확인하지 못한 것: AI(LLM) 제공사, CLOVA 결제 가능 여부, 사투리 인식 품질(CLOVA 문서에 방언 언급 없음), 대상(고령층) 근거 자료
- 다음에 할 일: SPEC 1~4장 승인, 역할 배정, 골조 시작

### 2026-10-26 · [claude]
- 한 일: SPEC.md 1~4장 사용자 승인, 승인 표시로 변경
- 바뀐 파일과 이유: SPEC.md (각 장 제목을 "승인: 2026-10-26"으로), PROGRESS.md (기록)
- 실행한 테스트와 결과: 테스트 없음 (문서 작업)
- 확인하지 못한 것: AI(LLM) 제공사, CLOVA 결제 가능 여부, 대상(고령층) 근거 자료
- 다음에 할 일: 역할 배정, 작업 1(백엔드 골조)·작업 2(프론트 골조) 시작

### 2026-10-26 · [codex]
- 한 일: 사용자 요청으로 Claude와 동시에 독립 작업할 Codex 전용 Git worktree 준비. 기준 커밋 435c9dc, 브랜치 codex/frontend-skeleton, 경로 C:\ITStudy\98_HACKATHON_CODEX
- 바뀐 파일과 이유: docs/prompts/01_frontend-skeleton.md (원본 프롬프트 복사), PROGRESS.md (이 작업 공간에만 기록 추가)
- 실행한 테스트와 결과: 테스트 없음 (작업 공간 분리만 수행, 앱 코드 작성 없음). Git worktree·브랜치 및 프롬프트 복사 확인
- 고치면 안 되는 파일: 안 고침
- 확인하지 못한 것: 프론트 실행·화면 비교 (구현 전)
- 다음에 할 일: 이 작업 공간에서 공통 규칙과 SPEC 및 프롬프트를 읽고 Codex 버전 골조 제작. Claude 원본 폴더는 수정하지 않음
### 2026-10-26 · [codex]
- 한 일: Codex 전용 worktree에서 프론트 골조 구현. 390px 앱 틀, 홈·메뉴·이체·계좌·동의 화면, 공용 팝업·말풍선·메뉴 강조, 세션·API·가짜 응답·동의 기반 클릭 전송 구성. 5174 포트 사용
- 바뀐 파일과 이유: frontend/ (앱 구현 및 잠금 파일), README.md (실행·연계·검증 안내), SPEC.md (작업 2 체크박스만 완료 표시), PROGRESS.md (기록)
- 실행한 테스트와 결과: 프론트 자동 테스트 없음(SPEC 작업 2). npm install 성공, npm run build 성공. 브라우저 수동 9개 통과·최종 실패 0개(README 참조). 하이라이트 스크롤 초기화 충돌 발견 후 수정·재확인
- 고치면 안 되는 파일: AGENTS.md·CLAUDE.md 안 고침. SPEC 본문 안 고침, 허용된 작업 2 체크박스만 변경
- 확인하지 못한 것: 실제 백엔드 API, 실제 AI·음성 인식. 역할 2 대화·상담·녹음은 의도적으로 빈자리
- 다음에 할 일: 두 UI 비교 후 골조 선택, 역할 2 구현 및 역할 3·4 백엔드 연결. Claude 폴더는 수정하지 않음

### 2026-10-26 · [claude]
- 한 일: main 프론트 골조 정리. 한 줄로 압축된 코드를 줄바꿈 정리(Prettier 3.9.10 일회성 실행, 프로젝트 의존성 추가 없음, 작은따옴표·한 줄 100자), Codex 비교용 흔적 제거(패키지 이름 won-take-frontend, 개발 포트 5173·미리보기 4173, 브라우저 제목, 화면 위 "CODEX UI" 표시, README의 개인 PC 경로·비교 문구)
- 바뀐 파일과 이유: frontend/src 전체·index.html·vite.config.js·package.json(형식), package.json·package-lock.json(이름·포트), index.html(제목), src/main.jsx(표시 문구), README.md(실행 안내), PROGRESS.md
- 실행한 테스트와 결과: npm run build 성공. 정리 전후 빌드 결과 비교: CSS 바이트 동일, JS는 "CODEX UI" 표시 문구 한 곳만 다름. 5173에서 브라우저 확인: 동의 → 홈 → 이체 3번째 진입 말풍선 → 질문 선택 시 대화 화면 전달 → 하이라이트 통과, 콘솔 오류 없음
- 고치면 안 되는 파일: 안 고침
- 확인하지 못한 것: 없음 (커밋하지 않음, 브랜치 없이 main 코드에서 작업)
- 다음에 할 일: 사용자가 VS Code에서 변경 확인 후 브랜치·커밋 결정

### 2026-10-26 · [claude]
- 한 일: 사용자 피드백 7건 반영 (커밋 없음, 브랜치 없이 main 코드에서 작업)
  1. 휴대폰 틀을 브라우저 높이에 맞춤(최대 844px, 틀 안에서만 스크롤), 홈의 "유용한 서비스" 삭제
  2. 도움 제안을 휴대폰 틀 오른쪽 아래 "도움이 필요하세요?" 말풍선으로 변경. 누르면 AI 대화로 이동하며 추천 질문 최대 3개를 넘김. [닫기]·[다시 띄우지 않기] 유지(SPEC R1)
  3. 화면 기록 기반 뒤로가기(goBack). 홈으로 가면 기록을 비움
  4. 메뉴 데이터를 screenId 형식으로 바꾸고 분류 5개(이체/출금 5, 계좌조회/관리 4, 인증/보안 4, 증명서/서류 4, 환전 4)로 균형 있게 늘림. 새 분류는 공통 시연 화면(ServiceScreen)
  5. 홈에 검색창 추가(검색하면 전체 메뉴로 이동해 결과 표시)
  6. 시연용 로그인(간편비밀번호 아무 숫자 6자리, 서버·DB 없음, 키패드는 클릭 기록 제외)
  7. 전체 메뉴 바로가기를 실제 분류 5개로 교체, 누르면 그 분류로 스크롤
- 바뀐 파일과 이유: frontend/src (navigation, components/Bubble·MenuList·SearchBar, layout/BackButton, data/menu.js, session/auth.js, screens/Home·Menu·Transfer·Account·Chat·Login·Service, main.jsx, styles/app.css), README.md, PROGRESS.md
- 실행한 테스트와 결과: npm run build 성공. 브라우저(1280x800) 확인: 페이지 스크롤 없음(틀 660px), 유용한 서비스 없음, 분류별 메뉴 수 5/4/4/4/4, 분류 바로가기가 해당 분류로 스크롤 호출, 뒤로가기(홈→이체→뒤로=홈, 홈→메뉴→계좌조회→뒤로=메뉴→뒤로=홈), 홈 검색 "증명" → 증명서 4개, 로그인(5자리는 대기, 6자리에 로그인, 로그아웃), 말풍선(오른쪽 아래, 끝까지 스크롤해도 같은 자리, 누르면 AI 대화 + 추천 질문 3개, 질문 선택 시 전달)
- 고치면 안 되는 파일: 안 고침
- 확인하지 못한 것: 부드러운 스크롤 애니메이션은 미리보기 창에서 그려지지 않아 눈으로 확인 못 함(호출은 확인). 역할 3 menu.json은 아직 없어 id 일치 여부 확인 못 함
- 다음에 할 일: 사용자 확인 후 브랜치·커밋 결정, 역할 3에게 menu.js 형식 전달

### 2026-10-26 20:13 · [codex]
- 한 일: 사용자 요청으로 origin/main 최신 내용을 Jaehong에 병합. 충돌한 SPEC.md·PROGRESS.md는 origin/main 버전을 선택하고, 기존 미커밋 변경을 덮어씀
- 바뀐 파일과 이유: origin/main의 문서·프론트엔드·규칙·자료 전체를 Jaehong에 반영, PROGRESS.md (기록)
- 실행한 테스트와 결과: frontend에서 npm ci 성공, npm run build 성공
- 고치면 안 되는 파일: AGENTS.md·승인된 SPEC.md를 직접 수정하지 않음 (main 병합 버전 반영)
- 확인하지 못한 것: 프론트 브라우저 동작 수동 확인
- 다음에 할 일: 병합된 Jaehong 브랜치에서 작업 계속

### 2026-10-26 20:56 · [codex]
- 한 일: 역할 2 프론트 기능 구현. 텍스트·추천 질문 대화, 음성 녹음·전사·표준어 표시, 고객 확인 뒤 상담 전달, PC 상담사 접수 목록·요약·클릭 기록·대화 원문 화면 구성
- 바뀐 파일과 이유: frontend/src/screens/ChatScreen.jsx, AgentScreen.jsx (대화·상담 화면), frontend/src/audio/recorder.js, recorder-worklet.js (최대 30초 16kHz 모노 WAV), frontend/src/api/mock.js (시연 대화·상담 기록), frontend/src/styles/app.css (화면 스타일), frontend/src/audio/recorder.test.js (WAV 형식·리샘플·길이 검증), README.md (실행·검증 안내), PROGRESS.md (기록)
- 실행한 테스트와 결과: `cd frontend && node --test src/audio/recorder.test.js` 3개 통과·0개 실패. `cd frontend && npm run build` 성공. 브라우저에서 음성 시작·중지 및 고정 전사/정규화 표시, 요약 취소·확인 전달, 접수 목록·클릭 타임라인·대화 원문 표시 확인
- 고치면 안 되는 파일: AGENTS.md·CLAUDE.md·SPEC.md·사람이 쓴 테스트 안 고침. API 클라이언트는 기존 SPEC 계약 함수를 재사용
- 확인하지 못한 것: 실제 백엔드 API·CLOVA 연결, 실제 음성 인식 결과, 마이크 30초 자동 종료를 30초간 수동 대기해 확인하는 것
- 다음에 할 일: 백엔드 연동 후 API 계약과 시나리오 A·B 통합 확인

### 2026-10-26 · [codex]
- 한 일: 사용자 요청으로 Jaehong(b124d75) 기반 codex/web-speech 브랜치에서 네이버 CSR 대신 PC Chrome Web Speech API로 음성→글자 변환. ko-KR 확정 결과를 기존 대화 API의 VOICE 입력으로 전달하고 WAV 업로드는 사용하지 않음. 30초 중지, 시작 10초·종료 3초 제한, 권한·네트워크 오류 안내, 화면 이탈 취소 구현
- 바뀐 파일과 이유: SPEC.md (사용자가 승인한 음성 인식 방식·계약·역할·검증 변경), frontend/src/audio/speech.js (새 음성 인식 모듈), speech.test.js (브라우저 이벤트·시간 경계 테스트), frontend/src/screens/ChatScreen.jsx (음성 글자 연결 및 서비스 처리 안내), README.md (PC Chrome 실행·확인 방법), PROGRESS.md (기록)
- 실행한 테스트와 결과: 새 테스트를 먼저 실행해 모듈 미구현 실패 확인. `cd frontend && node --test src/audio/*.test.js` 19개 통과·0개 실패(새 16개, 기존 WAV 3개). `npm run build` 성공. Chromium+Playwright 화면 확인 5개 통과·0개 실패, 런타임 오류 0건(음성 인식 이벤트 대역: 글자 표시·VOICE 1회 전달·WAV 미호출, 미지원 후 텍스트 입력, 권한 거부 후 텍스트 유지, 이탈 취소·지연 결과 미전송, 가짜 시계 30초 자동 중지). 화면 검증 도구는 /tmp/wontake-browser/web-speech.cjs에 있으며 프로젝트 의존성 추가 없음
- 고치면 안 되는 파일: SPEC.md는 이번 사용자 요청에 따른 변경 승인 범위에서 수정. AGENTS.md·CLAUDE.md·기존 테스트 안 고침. 기존 WAV 녹음 모듈과 테스트 보존
- 확인하지 못한 것: 사용자 실제 마이크의 Chrome 음성 인식 서비스 응답·발화 및 사투리 정확도, 실제 AI·백엔드 연결. 가짜 응답 모드의 AI 답변·표준어 정리·상담 요약은 여전히 예시이며 실제 인식 검증과 구분함
- 다음에 할 일: PC Chrome에서 마이크 허용 후 실제 발화를 확인하고 SPEC 3.5 시연 체크리스트 완료. 확인 전 작업 9 완료 체크는 보류

### 2026-10-26 · [codex]
- 한 일: 사용자 작업 브랜치 DongHa에 codex/web-speech 구현 이력을 반영. 이 PC에서 개발 서버 실행 및 Chrome 실제 마이크 발화 인식을 사용자가 확인함. 첨부 화면에서 인식 문장 "안녕하세요" 표시 확인.
- 바뀐 파일과 이유: PROGRESS.md (로컬 실행 및 실제 음성 인식 수동 검증 기록).
- 실행한 테스트와 결과: 사용자 수동 확인 — 로컬 개발 서버 실행, Chrome 실제 발화의 글자 표시 성공. 이번 기록 작업에서 자동 테스트는 실행하지 않음.
- 고치면 안 되는 파일: 안 고침.
- 확인하지 못한 것: 실제 AI·백엔드 연결, 사투리 정확도 및 전체 시연 체크리스트. 가짜 응답 모드의 AI 답변은 예시 응답임.
- 다음에 할 일: DongHa 브랜치를 본인 작업 브랜치로 사용하고 실제 백엔드·AI 연동 진행.

### 2026-10-27 · [codex]
- 한 일: 사용자 승인에 따라 최신 origin/main과 Chrome Web Speech API 구현을 DongHa에서 통합 확인. origin/main은 이미 DongHa의 조상이므로 추가 병합은 불필요했음. 작업 폴더에 남은 이전 버전 미커밋 수정·삭제 37개를 파일 백업과 Git stash로 보존하고, main 기능과 음성 인식이 함께 있는 DongHa 커밋 상태로 복원함.
- 백업: 이 PC의 Codex 작업 공간 work/wontake-backup-20261027에 파일 사본과 working-tree.patch·staged.patch 보관. Git stash 메시지: backup before DongHa main speech reconciliation 2026-10-27. 기존 변경을 다시 적용하지 않음.
- 바뀐 파일과 이유: PROGRESS.md에 이번 복원·통합 검증 기록만 추가. 앱 코드는 기존 커밋 내용을 복원했으며 새 구현·의존성 추가 없음.
- 실행한 테스트와 결과: frontend에서 node --test src/audio/recorder.test.js src/audio/speech.test.js — 19개 통과·0개 실패. npm run build 성공. 메뉴 데이터 확인 — 5개 카테고리, 메뉴 수 5/4/4/4/4, 총 21개. main의 메뉴·화면 이동·공용 UI·홈·메뉴·로그인·시연 화면과 차이 없음. main 조상 관계 확인 성공.
- 브라우저 확인: 전체 메뉴 5개 분류·21개 항목 표시, 환전 신청 시연 화면 이동, 동의 없이 시작, Chrome 음성 서비스 안내·마이크 버튼 표시, 텍스트 질문 전송·모의 답변·메뉴 이동 버튼 표시 확인.
- 고치면 안 되는 파일: AGENTS.md·CLAUDE.md·SPEC.md·기존 테스트를 새로 편집하지 않음. 백업된 이전 변경에서 커밋된 버전으로 복원만 수행.
- 확인하지 못한 것: 복원 후 실제 사용자 마이크 재발화, 사투리 정확도, 실제 백엔드·AI 호출, 전체 시나리오 재검증. 이전 실제 발화 성공은 사용자 확인 기록이며 이번에는 음성 모듈 자동 테스트와 UI 연결을 확인함.
- 다음에 할 일: PC Chrome에서 새로고침 후 메뉴와 실제 음성 입력 재확인. 본인 작업 브랜치는 DongHa. GitHub 원격 푸시는 별도 요청 시 진행.

### 2026-10-27 · [codex] 맥락 기반 메뉴 안내 서버 및 프런트 연결
- 사용자 승인 범위: 질문·이전 대화·동의한 행동 흐름으로 AI가 설명과 메뉴를 직접 생성하도록 구현. 고정 답변 대신 제한 지침을 사용하고, 내일 Claude 키/크레딧 연결 후 실제 판단을 테스트할 수 있게 준비.
- 한 일: Seunghyun d32ce82의 Java 21/Spring Boot 4.1.2/Gradle 골조를 backend에 가져옴. 서버 동의·순서 있는 행동 기록·세션별 최근 대화·21개 메뉴 카탈로그·대화 API·Claude HTTP 어댑터·모의 AI·응답 검증·오류 처리를 구현. 기존 상담 화면용 API는 메모리 시연 및 기록 기반 요약으로 호환. 실제 상담 시스템/AI 요약은 후속 범위.
- 프런트: 기본 모드를 백엔드 연결로 변경. 이벤트 전송 순서 보장, 질문 전 기록 전송 대기, 검색어 AI 연결 버튼, 화면 이동 후 대화 복구, 최대 2개 실제 메뉴 이동, 서버 모드 표시, 질문 길이 제한과 실패 시 입력 복원, 탭별 가상 사용자 분리. Chrome 음성 인식 코드는 보존.
- 바뀐 파일과 이유: backend/ (서버·데이터·프롬프트·테스트·팀원 골조), frontend/src/api/client.js·mock.js·eventQueue.js·eventQueue.test.js·catalog.test.js (API·순서·검증), components/SearchBar.jsx·screens/MenuScreen.jsx (검색어 전달), screens/ChatScreen.jsx (대화 유지·메뉴·상태), screens/AgentScreen.jsx (기록 기반 요약 표기), screens/ConsentScreen.jsx·tracking/index.js·navigation/index.jsx·session/index.js (동의·행동·세션), main.jsx·vite.config.js (실제 서버 상태·프록시), scripts/start-backend.ps1 (키 숨김 입력과 실행), docs/context-ai-plan.md·claude-local-test.md·README.md·SPEC.md·PROGRESS.md (합의 설계·실행·검증 기록).
- 실행한 테스트와 결과: 먼저 ContextFlowTest를 실행해 미구현 클래스 오류 확인 후 구현. Gradle test/bootJar 최종 성공: 21개 통과·0개 실패. 프런트 Node 테스트: 음성 19개, 이벤트 큐 2개 통과. 메뉴 카탈로그 경로 오류 수정 후 해당 1개 재실행 통과(총 22개 통과). npm run build 성공. Claude 테스트의 JSON 픽스처 생성 오류를 직렬화로 수정했으며 기대 결과는 유지.
- 백엔드 검증: 동일 질문·다른 맥락, 이전 대화, 동의 없음/철회, 세션 분리, 최근 20건 순서, 가짜 메뉴·중복 ID 거부, 실제 Claude 형식 HTTP 요청·구조화 응답 파싱(로컬 대역), 인증·크레딧 설정·모델·한도·시간 초과·불완전 응답 오류, 명시 Claude 모드 키 누락 실패, 기존 상담 초안·중복 접수·철회 후 기록 제외.
- 브라우저 검증: 모의 서버 실제 연결, 기록 동의, A 탭 이체→내역=이체내역조회, B 탭 환전→내역=환전 내역 조회, 각각 메뉴 이동, AI 재진입 시 대화 복구, 전체 메뉴 검색어를 AI로 자동 전송. 프런트 5분류 21개 메뉴 유지. 위 차이는 모의 규칙에 의한 연결 검증이며 실제 AI 추론 검증으로 보고하지 않음.
- 실행 환경: 공식 Adoptium Java 21을 작업 폴더에 내려받아 SHA-256 검증. 시스템 Java 설정 변경 없음. backend/.java-home·.gradle-user-home에 PC별 경로를 저장하고 Git 제외. 실제 키를 저장하거나 유료 AI 요청을 보내지 않음.
- 고치면 안 되는 파일: AGENTS.md·CLAUDE.md·기존 테스트 안 고침. SPEC.md는 이번 사용자 승인 범위의 R2 설계·계약·검증 및 추가 작업 체크박스를 수정.
- 확인하지 못한 것: 실제 Claude 인증·크레딧·모델 접근과 문맥 판단/안전 지침 준수, 이번 변경 후 실제 마이크 재발화. 마이크 자동 테스트는 통과했으며 실제 발화는 앞서 사용자 확인 이력이 있음.
- 다음에 할 일: docs/claude-local-test.md에 따라 -Claude로 서버를 재시작하고 API 키 숨김 입력. 두 탭 시나리오와 명시 질문 우선·범위 밖 질문을 실제 AI로 확인. 현재 원격 푸시는 하지 않음.

### 2026-10-27 · [codex] 행동 맥락 기반 능동 역질문과 후속 대화
- 사용자 요청: 같은 화면 반복 횟수 대신 즉시/예약이체·해외송금처럼 같은 분류의 서로 다른 메뉴를 짧게 오가는 흐름을 계기로 AI가 역질문을 정하고, 뒤이은 '한도 알려줘'의 문맥을 해석하도록 구현.
- 바뀐 파일과 이유: backend/behavior/BehaviorService.java·테스트 (방문 순서·체류·분류·10초/60초 경계·호출 간격·동의·스냅샷), ai/AiClient·ClaudeAiClient·FakeAiClient 및 ai/behavior-system.txt·chat-system.txt (실제 Claude 역질문 호출과 후속 질문에 직전 맥락 전달), api/DemoController·session/DemoStore·chat/ChatService (이벤트와 비동기 분석 분리·서버 소유 contextId), ClaudeAiClientTest·ApiFlowTest (HTTP 계약·전체 흐름), frontend/api/client.js·navigation/index.jsx·components/Bubble.jsx·screens/ChatScreen.jsx·styles/app.css (비동기 제안·AI 질문 표시·글자/음성 후속 질문·가변 문구 크기), SPEC.md·README.md·docs/claude-local-test.md·context-ai-plan.md·PROGRESS.md (승인된 새 기준·시연 안내).
- 동작: 첫 시작부터 마지막 종료까지 60초 안에 같은 분류의 서로 다른 메뉴를 2개 이상 각 10초 이내로 방문하고 나오면 분석 ID 생성. 규칙은 계기만 정하며, CLAUDE는 방문 순서·체류시간·최근 기록·대화·메뉴로 도움 제안 여부와 역질문을 생성. 같은 메뉴 반복·분류 혼합·긴 방문 제외. 60초 호출 간격·동일 ID 중복 호출 방지. AI 거절·오류·잘못된 메뉴는 고정 질문으로 대체하지 않음.
- 맥락: 말풍선 직전 스냅샷을 최대 10분 유지하고 contextId로 텍스트·음성 질문에 전달. 이후 클릭 25건이 있어도 원래 흐름 유지. 동의 철회·닫기는 스냅샷 삭제. 다른 세션 접근 제외. 빠르게 화면을 옮겨도 분석 요청은 보내고, 대화 화면·다른 분류에 늦은 제안을 무조건 표시하지 않음.
- 테스트: 먼저 BehaviorServiceTest 실행해 미구현 실패 확인 후 구현. Gradle test/bootJar 성공: 34개 통과·0개 실패. 프런트 Node 테스트 22개 통과·0개 실패, npm run build 성공. 기존 음성 인식 테스트 유지. 실제 Claude 통신은 로컬 HTTP 대역으로 역질문 전용 지침·JSON 스키마·방문 순서·체류시간을 확인.
- 브라우저: 기록 동의 → 전체 메뉴 → 즉시/예약이체 → 뒤로 → 해외송금 → 뒤로를 빠르게 수행해 맞춤 주제 말풍선 표시 확인. 말풍선 클릭 → 직전 제안 표시 → '한도 알려줘' 전송 → 이체한도 조회/변경 버튼 표시 확인. 말풍선이 휴대폰 틀 안에서 줄바꿈되는 것 확인. 모의 모드 연결 검증이며 실제 AI의 의미 판단으로 보고하지 않음.
- 고치면 안 되는 파일: AGENTS.md·CLAUDE.md·사람이 쓴 테스트·기존 음성 테스트 안 고침. SPEC.md는 이번 사용자 요청 범위인 R1 분석 기준·역질문 계약·테스트·완료 표시를 변경.
- 확인하지 못한 것: 실제 Claude의 역질문 선택·후속 발화 문맥 판단·문구 품질. 실제 마이크 재발화는 사용자 확인 필요. API 키·유료 호출은 사용하지 않음.
- 다음에 할 일: 새로고침 후 동의하고 새 흐름으로 테스트. Claude 키 연결 후 이체·환전 흐름을 비교하고 AI가 근거 없는 오류·금액·거래 완료를 만들지 않는지 확인. 원격 푸시는 별도 요청 시 진행.
