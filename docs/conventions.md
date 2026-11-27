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

- 기능 브랜치 이름은 `feat/<이슈 번호>-<작업 요약>` 형식을 사용한다. 예를 들어 이슈 12의 회원가입 작업은 `feat/12-signup`으로 만든다. 작업 요약은 소문자와 하이픈으로 작성한다.
- 커밋 메시지 형식과 PR 제목 규칙은 팀에서 확정한 설정을 따르며, 프로젝트 기준이 정해지면 이곳에 기록한다.
- 작업 브랜치 생성, PR 대상과 머지 조건은 [개발 워크플로](workflow.md#github-작업-순서)를 따른다.
