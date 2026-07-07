# Blankit Backend

Blankit 백엔드 프로젝트입니다.

## 기술 스택

| 카테고리 | 기술 | 버전 | 설명 |
| --- | --- | --- | --- |
| 언어 | Java | 21 | 백엔드 개발 언어 |
| 프레임워크 | Spring Boot | 4.1.0 | 백엔드 애플리케이션 프레임워크 |
| 빌드 도구 | Gradle | Wrapper 사용 | 의존성 관리 및 빌드 |
| 웹 | Spring Web MVC | - | REST API 개발 |
| ORM | Spring Data JPA | - | 데이터베이스 접근 |
| 보안 | Spring Security | - | 인증/인가 처리 |
| 검증 | Validation | - | 요청 데이터 검증 |
| DB | MySQL | - | 개발/운영 데이터베이스 |
| 테스트 DB | H2 | - | CI 및 테스트용 인메모리 DB |
| 코드 보조 | Lombok | - | 반복 코드 제거 |
| CI | GitHub Actions | - | 빌드 및 테스트 자동화 |
| 코드 리뷰 | CodeRabbit | - | PR 자동 리뷰 보조 |

---

## 시작하기

### 1. 프로젝트 클론

```bash
git clone https://github.com/IT-Cotato/13th-Blankit-BE.git
cd 13th-Blankit-BE
```

### 2. Gradle Wrapper 권한 부여

Mac 또는 Linux 환경에서 최초 1회 실행합니다.

```bash
chmod +x ./gradlew
```

### 3. 빌드

```bash
./gradlew clean build
```

### 4. 서버 실행

```bash
./gradlew bootRun
```

기본 실행 주소는 아래와 같습니다.

```text
http://localhost:8080
```

### 5. Swagger

Swagger 설정이 적용된 경우 아래 주소로 접속합니다.

```text
http://localhost:8080/swagger-ui/index.html
```

---

## 프로젝트 구조

이 프로젝트는 **도메인 중심 구조**를 사용합니다.

```text
13th-Blankit-BE/
├── .github/
│   ├── ISSUE_TEMPLATE/             # 이슈 템플릿
│   ├── workflows/                  # GitHub Actions CI
│   └── pull_request_template.md    # PR 템플릿
│
├── docs/                           # 프로젝트 문서
├── gradle/                         # Gradle Wrapper 설정
│
├── src/
│   ├── main/
│   │   ├── java/com/cotato/blankit/
│   │   │   ├── BlankitApplication.java
│   │   │   ├── global/             # 전역 공통 모듈
│   │   │   └── domain/             # 도메인별 기능 모듈
│   │   │
│   │   └── resources/              # 설정 파일
│   │
│   └── test/                       # 테스트 코드
│
├── build.gradle
├── settings.gradle
├── gradlew
├── gradlew.bat
├── .gitignore
├── .coderabbit.yaml
└── README.md
```

---

## 패키지 구조 가이드

### `global`

프로젝트 전역에서 사용하는 공통 코드를 관리합니다.

| 폴더 | 역할 |
| --- | --- |
| `config` | 공통 설정 |
| `exception` | 전역 예외 처리 |
| `response` | 공통 응답 형식 |
| `security` | 인증/인가 설정 |
| `util` | 공통 유틸 |

### `domain`

도메인별 비즈니스 코드를 관리합니다.

각 도메인은 아래 구조를 기준으로 작성합니다.

```text
domain/도메인명/
├── controller
├── service
├── repository
├── dto
│   ├── request
│   └── response
└── entity
```

---

## 네이밍 규칙

| 구분 | 규칙 | 예시 |
| --- | --- | --- |
| 패키지명 | lowercase | `controller`, `service` |
| 클래스명 | PascalCase | `UserController` |
| 메서드/변수명 | camelCase | `findUserById`, `createdAt` |
| 상수명 | UPPER_SNAKE_CASE | `DEFAULT_PAGE_SIZE` |
| Request DTO | `기능명Request` | `LoginRequest` |
| Response DTO | `기능명Response` | `UserResponse` |

---

## Git 컨벤션

### 핵심 규칙

| 구분 | 형식 | 예시 |
| --- | --- | --- |
| Branch | `type/#이슈번호-작업명` | `feat/#12-login-api` |
| Commit | `type: 작업 내용` | `feat: 로그인 API 구현` |
| PR | `[type] 작업 내용 (#이슈번호)` | `[feat] 로그인 API 구현 (#12)` |

하나의 작업에서는 Branch, Commit, PR의 `type`을 통일하는 것을 권장합니다.

### 타입

| 타입 | 설명 |
| --- | --- |
| `feat` | 새로운 기능 추가 |
| `fix` | 버그 수정 |
| `refactor` | 코드 구조 개선 |
| `perf` | 성능 개선 |
| `docs` | 문서 수정 |
| `chore` | 설정, 의존성, 빌드 등 기타 작업 |
| `test` | 테스트 코드 작성 및 수정 |

---

## 브랜치 전략

| 브랜치 | 설명 |
| --- | --- |
| `main` | 배포 가능한 최종 브랜치 |
| `develop` | 개발 통합 브랜치 |
| `type/#이슈번호-작업명` | 기능 개발, 버그 수정 등 실제 작업 브랜치 |

### 병합 규칙

- 작업 브랜치는 `develop` 브랜치로 PR을 생성합니다.
- `develop` 병합 전 빌드 및 테스트가 통과해야 합니다.
- PR은 최소 1명 이상 확인 후 병합합니다.
- 충돌은 작업자가 우선 해결합니다.
- 병합 후 작업 브랜치는 삭제합니다.
- `main` 브랜치는 배포 시점에만 `develop`에서 PR을 생성합니다.

```text
작업 브랜치 → develop → main
```

---


## CodeRabbit 리뷰 규칙

PR 리뷰 보조 도구로 CodeRabbit을 사용합니다.

| 태그 | 의미 | 처리 기준 |
| --- | --- | --- |
| `P1` | 머지 전 반드시 수정 | blocking |
| `P2` | 고치면 좋지만 선택 | non-blocking |
| `Q` | 질문 | 답변 필요 |
| `Nit` | 사소한 의견 | 선택 반영 |

`P1`은 반드시 수정하거나, 수정하지 않는 이유를 PR 코멘트로 남깁니다.

---