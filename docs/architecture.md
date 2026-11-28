# 인증 서비스 아키텍처

상태 — 검토 초안. 기본 애플리케이션 진입점은 `com.kjh.auth.AuthApplication`이다. 이메일 인증 실행에는 Redis와 Brevo 설정이 필요하다. MySQL 연결 설정은 유지하며 Flyway 자동 실행은 현재 비활성화한다.

## 확정된 구성

- MSA에서 Auth Service와 User Service를 별도로 둔다. Auth Service의 내부 구조에는 헥사고날 아키텍처를 적용한다. 패키지 배치는 아래 헥사고날 패키지 구조를 따른다.
- Auth Service는 로그인 정보를 관리한다. svc_identity의 user_profile은 닉네임과 사용자 유형을 관리하며, User Service가 관리하는 자체 프로필과 별개다.
- 웹 클라이언트는 로그인에 성공하면 Auth Service에서 JWT를 받는다. 보호 요청의 JWT는 API Gateway가 검증하고 사용자 ID를 추출해 대상 서비스에 전달한다. 대상 서비스는 전달받은 ID로 요청 주체를 식별하고 리소스 접근 권한을 판단한다.

## 헥사고날 패키지 구조

헥사고날 아키텍처는 애플리케이션이 정의한 포트를 통해 외부와 상호작용하고, 어댑터가 그 포트와 웹·데이터베이스 같은 외부 기술을 연결하는 구조다.

아래 트리는 User Service 커밋 `1ea4ccb`을 참고해 정한 인증 서비스의 배치 기준이다. `<base-package>`는 Java 기본 패키지 `com.kjh.auth`에 대응한다. 패키지는 해당 책임의 코드를 구현할 때 만들며, 테스트 경로는 운영 패키지와 대응하는 배치 예시다.

파일명은 보호자 가입 기능을 중심으로 각 패키지의 책임과 배치를 보여 주는 예시다.

```text
src/
├── main/
│   ├── java/<base-package>/
│   │   ├── AuthApplication.java
│   │   ├── domain/
│   │   │   ├── model/
│   │   │   │   ├── Account.java
│   │   │   │   └── Email.java
│   │   │   └── exception/
│   │   │       └── InvalidEmailException.java
│   │   ├── application/
│   │   │   ├── exception/
│   │   │   │   └── EmailVerificationRequiredException.java
│   │   │   ├── port/
│   │   │   │   ├── in/
│   │   │   │   │   ├── SignupUseCase.java
│   │   │   │   │   └── dto/
│   │   │   │   │       ├── SignupCommand.java
│   │   │   │   │       └── SignupResult.java
│   │   │   │   └── out/
│   │   │   │       └── AccountRepositoryPort.java
│   │   │   └── service/
│   │   │       └── SignupService.java
│   │   ├── adapter/
│   │   │   ├── in/web/
│   │   │   │   ├── SignupController.java
│   │   │   │   ├── config/
│   │   │   │   │   └── SecurityConfig.java
│   │   │   │   ├── dto/
│   │   │   │   │   ├── SignupRequest.java
│   │   │   │   │   └── SignupResponse.java
│   │   │   │   ├── exception/
│   │   │   │   │   └── GlobalExceptionHandler.java
│   │   │   │   └── mapper/
│   │   │   │       └── SignupWebMapper.java
│   │   │   └── out/
│   │   │       ├── mysql/
│   │   │       │   ├── AccountRepositoryAdapter.java
│   │   │       │   ├── config/
│   │   │       │   │   └── JpaConfig.java
│   │   │       │   ├── entity/
│   │   │       │   │   └── AccountEntity.java
│   │   │       │   ├── mapper/
│   │   │       │   │   └── AccountEntityMapper.java
│   │   │       │   └── repository/
│   │   │       │       └── AccountJpaRepository.java
│   │   │       ├── redis/
│   │   │       │   ├── EmailVerificationCodeAdapter.java
│   │   │       │   └── config/
│   │   │       │       └── RedisConfig.java
│   │   │       ├── security/
│   │   │       │   └── PasswordHashAdapter.java
│   │   │       └── email/
│   │   │           └── VerificationEmailSenderAdapter.java
│   │   └── config/
│   │       └── SignupConfig.java
│   └── resources/
│       └── application.yml
└── test/
    ├── java/<base-package>/
    │   ├── domain/model/
    │   │   └── EmailTest.java
    │   ├── application/service/
    │   │   └── SignupServiceTest.java
    │   └── adapter/
    │       ├── in/web/
    │       │   └── SignupControllerTest.java
    │       └── out/mysql/
    │           └── AccountRepositoryAdapterTest.java
    └── resources/
        └── application-test.yml
```

| 패키지 | 책임 |
| --- | --- |
| `domain` | 도메인 모델, 규칙과 도메인 예외를 정의한다. |
| `application` | 로그인이나 회원가입 같은 유스케이스의 처리 순서를 조율한다. |
| `application/port` | 애플리케이션 경계의 인터페이스를 정의한다. 입력 포트는 외부에서 실행할 수 있는 유스케이스를, 출력 포트는 애플리케이션이 외부에서 필요로 하는 기능을 정의한다. |
| `application/port/in` | 입력 포트 인터페이스와 유스케이스 입출력 DTO를 정의한다. |
| `application/port/out` | 저장소, 비밀번호 검증, 토큰 발급 등 외부 기능의 계약을 정의한다. |
| `application/exception` | 유스케이스 실행 전제조건 위반과 처리 과정에서 발생하는 애플리케이션 예외를 정의한다. |
| `application/service` | 입력 포트를 구현하며 처리 순서와 트랜잭션을 관리한다. |
| `adapter/in/web` | HTTP 요청을 검증하고, 웹 요청 DTO를 유스케이스 입력 DTO로 변환해 입력 포트를 호출한다. 유스케이스 결과 DTO는 웹 응답 DTO로 변환한다. |
| `adapter/out` | 출력 포트를 구현한다. MySQL은 영속성, Redis는 인증 임시 상태와 요청 제한, 보안 어댑터는 해싱·토큰, 이메일 어댑터는 발송 연동을 담당한다. |
| `config` | 여러 계층의 Bean 구성과 서비스 전역 설정을 담당한다. 어댑터 전용 설정은 해당 어댑터의 `config`에 둔다. |

요청은 컨트롤러에서 입력 포트의 구현인 애플리케이션 서비스로 이어진다. 서비스는 출력 포트를 통해 외부 기능을 호출하고, 출력 어댑터가 해당 계약을 구현한다.

소스 코드 의존성은 바깥에서 안쪽으로 향한다.

- 도메인은 `application`, `adapter`, 설정 패키지에 의존하지 않는다.
- 애플리케이션은 구체 어댑터에 의존하지 않는다.
- 입력 어댑터는 출력 어댑터를 직접 참조하지 않는다.

클래스명과 변환 기준은 [코드 컨벤션](conventions.md)을 따른다.

## 서비스 경계 제안

| 책임 | 제안 |
| --- | --- |
| 인증 수단 확인 | 인증 서비스가 자격 증명 검증과 인증 결과를 담당한다. |
| 세션 또는 토큰 수명 | 인증 서비스가 발급, 갱신, 폐기 결과를 관리한다. Gateway에서 요청의 JWT를 검증한다. Refresh Token Rotation과 기존 토큰을 즉시 무효화할지는 팀 논의 후 정한다. Gateway의 폐기 확인 방식도 이 결정에 따른다. |
| 리소스 접근 | 각 리소스 서비스가 해당 서비스의 소유권과 업무 권한을 검사한다. 공통 권한의 원본과 전달 방식은 호출자와 함께 결정한다. |
| 상태 변경 | 갱신·종료가 같은 세션에서 경합할 때의 우선순위와 원자적 변경을 보장할 저장 경계를 저장 구조 설계 시 합의한다. |

## 구조 결정에 필요한 입력

- Gateway가 추출할 JWT 클레임과 값의 의미를 정한다. 로그인 아이디를 사용할지, Auth Service와 User Service가 공유하는 내부 사용자 ID를 사용할지는 팀 논의 후 확정한다.
- 각 서비스가 Gateway 경유 요청만 받을지, 서비스 간 직접 호출도 허용할지는 팀 논의 후 확정한다. 허용한 호출 경로의 신뢰 기준은 [보안 정책](security.md)에서 정한다. 배포 단위도 함께 확인한다.
- 추가 어댑터의 세부 배치는 구현 범위에 맞춰 정한다.
- 외부 연동이 필요한 경우에는 응답 지연·장애·재시도 시 서비스가 어떻게 동작할지 정하고, 영향을 받는 계약에 반영한다.

동작은 [요구사항](requirements.md), 인증 검증과 키 운영은 [보안](security.md), 상태 저장은 [데이터베이스](database.md)를 참고한다.
