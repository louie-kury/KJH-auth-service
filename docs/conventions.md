# 코드 컨벤션

아래 규칙은 User Service 커밋 `1ea4ccb`을 참고해 정한 인증 서비스의 기준이다. 패키지 배치는 [아키텍처](architecture.md)를 따른다.

## 이름과 배치

### Java 표기법

| 대상 | 표기법 | 예시 |
| --- | --- | --- |
| 타입과 해당 Java 소스 파일 | `PascalCase` | `SignupCommand`, `SignupCommand.java` |
| 메서드·필드·지역 변수·매개변수 | `lowerCamelCase` | `signUp`, `loginId` |
| 상수·열거형 값·오류 코드 식별자 | `UPPER_SNAKE_CASE` | `MAX_PASSWORD_LENGTH`, `ACTIVE`, `INVALID_CREDENTIALS` |
| 패키지 | 소문자 | `adapter.in.web` |

공개 오류 코드 값과 응답 형식은 [API 계약](api.md)에 정의한다.

### 역할별 타입 이름

| 역할 | 이름 규칙 | 예시 |
| --- | --- | --- |
| 도메인 모델과 값 객체 | 도메인 개념의 이름 | `Account`, `Email` |
| 입력 포트 | `*UseCase` | `SignupUseCase` |
| 유스케이스 구현 | `*Service` | `SignupService` |
| 출력 포트 | `*Port` | `AccountRepositoryPort` |
| 출력 포트 구현 | `*Adapter` | `AccountRepositoryAdapter` |
| JPA 엔티티 | `*Entity` | `AccountEntity` |
| Spring Data JPA 리포지토리 | `*JpaRepository` | `AccountJpaRepository` |
| 웹 요청·응답 DTO | `*Request`, `*Response` | `SignupRequest`, `SignupResponse` |
| 유스케이스 입출력 DTO | `*Command`, `*Result` | `SignupCommand`, `SignupResult` |
| 애플리케이션 내부 모델 | 데이터의 의미와 용도를 나타내는 이름 | `AccountSummary` |
| 웹·영속성 매퍼 | `*WebMapper`, `*EntityMapper` | `SignupWebMapper`, `AccountEntityMapper` |

- 입력 포트와 구현은 유스케이스의 목적을 기준으로 이름을 짓는다. 회원가입과 로그인처럼 호출 목적과 정책이 독립적이면 포트를 분리한다. 한 목적을 함께 이루고 같은 호출자가 사용하는 작업은 하나의 포트에 둘 수 있다.
- 웹과 유스케이스의 DTO는 표의 역할별 접미사를 사용한다. 애플리케이션 내부 모델은 의미가 드러나는 이름을 사용한다.
- Java 패키지명에는 `adapter`, 구현 타입 접미사에는 `Adapter`를 사용한다.

## 의존성과 경계

- 의존성은 생성자로 주입한다. 컨트롤러는 입력 포트를 호출하고, 애플리케이션 서비스는 출력 포트를 통해 외부 기능을 사용한다.
- 웹 DTO는 `adapter/in/web/dto`, 유스케이스 DTO는 `application/port/in/dto`에 둔다. 웹 요청·응답 형식이 바뀌어도 유스케이스의 입력·결과 타입에 미치는 영향을 줄이기 위해, 두 DTO 사이의 변환은 웹 어댑터의 웹 매퍼에서 수행한다. 도메인 모델과 JPA 엔티티는 영속성 매퍼가 변환한다.
- 도메인 값의 불변식 위반은 `domain/exception`, 이메일 인증 미완료 같은 유스케이스 전제조건 위반은 `application/exception`에서 정의한다.
- 웹 계층의 `GlobalExceptionHandler`에서 관련 예외를 HTTP 응답으로 변환한다. 상태 코드와 오류 응답 형식은 [API 계약](api.md)을 따른다.
- 예상하지 못한 예외는 공통 서버 오류 응답으로 변환하고, 내부 예외 메시지나 스택 트레이스는 응답에 포함하지 않는다.

## 설정 배치

- 여러 계층을 연결하는 Bean 구성과 서비스 전역 설정은 최상위 `config`에 둔다.
- 어댑터 전용 수동 설정은 해당 어댑터의 `config`에 둔다. 예를 들어 `OpenApiConfig`와 `SecurityConfig`는 `adapter/in/web/config`, `RedisConfig`는 `adapter/out/redis/config`, `JpaConfig`는 `adapter/out/mysql/config`에 둔다.

## 검증과 저장소 규칙

- 테스트는 `src/test/java`에서 검증 대상과 대응하는 패키지에 둔다. 검증할 동작과 실행 환경은 [테스트](testing.md)를 참고한다.
- 참고 저장소는 ArchUnit으로 도메인과 애플리케이션의 의존성 경계 및 입력·출력 어댑터 사이의 직접 의존 금지를 검사한다. 인증 서비스의 검증 도구와 서식 설정은 미결정이다.

## Git

작업 순서와 PR 대상은 [개발 워크플로](workflow.md#github-작업-순서)를 따른다.

### 공통 타입

이슈 제목, 작업 브랜치명, 커밋 메시지, PR 제목에는 아래 여섯 가지 타입만 사용한다. 타입은 영문 소문자로 작성한다.

| 타입 | 사용 기준 |
| --- | --- |
| `feat` | 기능 추가 또는 변경 |
| `fix` | 버그 수정 |
| `refactor` | 동작을 유지하면서 코드 구조 개선 |
| `docs` | 문서 추가 또는 수정 |
| `test` | 테스트 추가 또는 수정 |
| `chore` | 설정, 의존성, 개발 도구 변경 |

### 이슈 제목

`[타입] 제목` 형식으로 작성한다. 이슈 템플릿의 제목에는 공통 타입만 사용한다.

```text
[feat] 가입 이메일 인증 기능 추가
[fix] 로그인 실패 응답 통일
```

### 작업 브랜치명

`타입/이슈번호-작업명` 형식으로 작성한다. 이슈가 없으면 `타입/작업명`으로 작성한다. 작업명은 영문 소문자로 쓰고 단어는 하이픈(`-`)으로 연결한다.

```text
feat/12-add-email-verification
docs/update-readme
```

### 커밋 메시지

`타입: 변경 내용` 형식으로 작성한다. 콜론 뒤에는 공백 한 칸을 넣고, 변경 내용은 반드시 한글로 짧고 구체적으로 작성한다. 본문을 추가하는 경우에도 반드시 한글로 작성한다.

영문 타입·기술 용어·파일명·식별자는 그대로 쓸 수 있지만 변경 설명 문장은 한국어로 작성한다. 커밋 실행 직전에 최종 제목과 본문을 확인한다. 커밋 후 `git log -1 --format=%B`로 실제 저장된 메시지도 확인한다. 기존 커밋이나 공유 이력의 재작성은 별도 작업 범위 없이 진행하지 않는다.

커밋마다 대상 변경, 메시지와 검증 결과를 사용자에게 제시하고 명시적 승인을 받은 후 실행한다. 커밋 단위 완료 시 전체 계획상 진행 위치와 다음 작업을 함께 알린다.

```text
feat: 가입 이메일 인증 기능 추가
test: 이미 사용한 이메일 인증 증명값의 재사용 거부 검증
```

### PR 제목

이슈가 있으면 `[#이슈번호][타입] 제목`, 이슈가 없으면 `[타입] 제목` 형식으로 작성한다.

```text
[#12][feat] 가입 이메일 인증 기능 추가
[docs] README 실행 방법 정리
```

### PR과 이슈 완료 확인

- 작업 이슈가 있는 PR은 실제 PR 본문 맨 아래에 `Closes #12`처럼 적는다. 주석·코드 블록 밖에 작성하고 템플릿의 번호 자리표시자를 실제 번호로 바꾼다. 여러 이슈면 각각 한 줄로 쓰며, 다른 저장소 이슈는 `Closes 소유자/저장소#12`로 적는다.
- `Refs #12`·관련 이슈 링크·PR 제목의 번호는 종료 문구가 아니다. 작업 이슈의 `Closes`를 임의로 `Refs`로 대체하지 않는다. 남은 후속 사항은 본문에 적고, 사용자가 일부 작업으로 명시하거나 `Refs`를 지정한 경우에만 `Refs`를 쓴다.
- PR을 작성할 실제 저장소와 GitHub의 현재 기본 브랜치, PR 대상 브랜치를 확인한다. 오래된 로컬 `origin/HEAD`나 폴더 이름만으로 판단하지 않는다. 기본 브랜치 외 대상으로 머지하면 종료 키워드가 적용되지 않으므로 후속 기본 브랜치 반영 또는 수동 종료가 필요한지 검토 요청에 적는다. 후속 PR로 기본 브랜치에 반영해 이슈를 완료할 때는 그 후속 PR 본문에도 `Closes #이슈번호`를 적는다. 첫 PR 본문의 종료 문구는 후속 PR에 자동 승계되지 않는다. 이슈 종료를 위해 PR 대상을 임의 변경하지 않는다.
- PR 게시 직전에 작업 이슈 번호와 `Closes` 문구를 대조한다. 게시·본문 수정 후 실제 저장된 본문·대상 브랜치·이슈 번호를 다시 읽어 확인하고, 다르면 바로 수정한다.
- 머지는 사람이 수행한다. 사람이 머지할 때, 또는 이후 에이전트가 해당 작업을 이어받을 때는 PR의 실제 머지 상태와 이슈의 종료 상태를 각각 확인한다. 열려 있으면 완료 기준·종료 문구·대상 브랜치를 확인해 원인과 후속 조치를 보고한다. 수동 종료는 사용자 요청 범위에서만 하며 이슈 상태를 확인하지 않고 종료 완료로 보고하지 않는다.

자동 종료 조건은 [GitHub 공식 안내](https://docs.github.com/en/issues/tracking-your-work-with-issues/using-issues/linking-a-pull-request-to-an-issue)를 따른다.
