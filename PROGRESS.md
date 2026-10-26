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
