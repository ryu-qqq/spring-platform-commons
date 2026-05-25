# Hexagonal conventions — wiki pointer

> ConventionHub MCP 대신 Obsidian wiki를 source of truth로 사용한다.

## 진입점

| Wiki | Vault 상대 경로 |
|------|-----------------|
| **Overview** | `wiki/conventions/java-springboot-hexagonal/_overview.md` |
| About | `wiki/conventions/java-springboot-hexagonal/_about.md` |

Vault: `.claude/project.yaml` → `knowledge.vault.path`  
컨벤션 영역: `knowledge.vault.conventionsPath` (`wiki/conventions`)

## Harness에서 조회

- **wiki-lookup** agent — 주제·레이어·모듈 키워드로 관련 페이지 검색·요약
- Cursor **Obsidian MCP** (`MCP_DOCKER`) — `obsidian_get_file_contents` 등

## 이 레포와의 관계

spring-platform-commons는 헥사고날 **템플릿 + platform SDK**를 만드는 레포다.  
모듈 뼈대·패키지·의존 방향은 wiki overview와 `layers/` · `modules/` · `patterns/`를 따른다.
