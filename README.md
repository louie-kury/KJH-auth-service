# 인증 서비스 설계 문서

인증 서비스의 책임, 사용자 동작, 보안 정책과 외부 계약을 정리하는 프로젝트다. 문서의 제안과 미결정 항목은 설계 검토에 사용한다. 프로젝트는 MSA와 헥사고날 아키텍처를 채택한다. 기본 애플리케이션은 Spring Boot 4.1.2의 Web MVC 스타터, JDK 17, Gradle 8.14.3을 사용한다. Java 기본 패키지는 `com.kjh.auth`다. 영속성 설계의 MySQL 버전은 8.4.13으로 고정한다.

작업에 적용되는 공통 규칙은 [프로젝트 작업 지침](AGENTS.md)에서 확인한다. 기능 설계는 요구사항과 보안 정책에서 시작하고, 합의한 동작을 API와 저장소 설계 및 테스트에 연결한다.

## 로컬 실행

JDK 17이 설치된 환경에서 저장소 루트에서 실행한다. Gradle Wrapper는 `gradle-8.14.3-bin.zip`을 사용한다. Gradle 배포 파일과 의존성 캐시가 없으면 최초 실행 시 인터넷 연결이 필요하다. 이메일 인증 실행에는 Redis 연결 설정과 Brevo 발송 설정이 필요하다.

```powershell
.\gradlew.bat bootRun
```

macOS와 Linux에서는 `sh ./gradlew bootRun`을 실행한다. 빌드와 테스트 명령은 [개발 워크플로](docs/workflow.md#로컬-검증)를 참고한다.

기본 빌드는 외부 저장소 통합 테스트를 건너뛴다. Redis 인증 흐름과 MySQL·Redis 접속 검증의 실행 방법과 격리 조건은 [외부 저장소 테스트](docs/testing.md#환경과-도구-결정)를 참고한다.

### 보호자 가입 이메일 인증

저장소 루트의 `.env`를 Spring Config Data로 읽는다. MySQL은 `MYSQL_URL`(JDBC 형식), `MYSQL_USERNAME`, `MYSQL_PASSWORD`, Redis는 `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`를 사용한다. 이메일 인증 API는 기본 실행에서 활성화된다.

```powershell
.\gradlew.bat bootRun
```

발신 주소는 `BREVO_SENDER_EMAIL`, 인증번호 HMAC 키는 `VERIFICATION_CODE_HMAC_KEY`로 매핑한다. Brevo HTTP API 인증에는 `BREVO_API_KEY`를 사용한다.

AUTH-006을 로컬 또는 배포 환경에서 사용하려면 Redis 8.2.11과 Brevo HTTP API 키를 준비한다. 인증번호와 가입 증명값은 Redis에 저장하며 MySQL 테이블 생성은 필요하지 않다. 현재 Flyway는 비활성화돼 있다. API 상태 코드와 Gateway 헤더는 [이메일 인증 계약](docs/api.md#보호자-가입-이메일-인증-auth-006)을 참고한다.

| 환경 변수 | 값 |
| --- | --- |
| `AUTH_SIGNUP_EMAIL_TRUSTED_GATEWAY` | 선택 설정. 신뢰할 Gateway 피어 IP. 미설정 시 실제 연결 IP를 사용 |
| `AUTH_SIGNUP_EMAIL_FROM` | Brevo에 등록한 발신 이메일 주소 |
| `AUTH_SIGNUP_EMAIL_HMAC_KEY` | 무작위 32바이트 이상의 키를 Base64로 인코딩한 값 |
| `AUTH_SIGNUP_EMAIL_GLOBAL_SEND_LIMIT_PER_MINUTE` | 서비스 전체 분당 발송 한도. 기본값 `100`이며 운영 Brevo 할당량을 기준으로 조정 |
| `SPRING_DATA_REDIS_HOST`, `SPRING_DATA_REDIS_PORT` | 여러 키를 한 스크립트에서 처리할 수 있는 단일 Redis 쓰기 주 노드의 접속 정보 |
| `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` | MySQL JDBC URL과 인증 정보 |
| `BREVO_API_KEY` | Brevo 트랜잭션 메일 API 키 |

Brevo HTTP API는 HTTPS로 연결한다. 운영 발송 전에 발신 주소를 등록하고 발신 도메인의 Brevo 코드·DKIM·DMARC를 설정한다. API 키와 HMAC 키는 배포 환경의 비밀 설정으로 주입한다. [Brevo HTTP API 가이드](https://developers.brevo.com/reference/send-transac-email) · [Brevo 도메인 인증 가이드](https://help.brevo.com/hc/en-us/articles/12163873383186-Authenticate-your-domain-with-Brevo-Brevo-code-DKIM-DMARC)

가입 증명값은 Redis TTL 10분으로 자동 삭제된다. 증명값 유실이나 소비 후 가입 저장 실패 시 다시 인증한다.

## 인증 서비스 초기화 요청 예시

> 현재 저장소에 `AGENTS.md`와 `docs/`를 기준으로 인증 서비스의 기본 프로젝트 구조와 빌드 설정을 만들어줘.
> 초기 빌드와 기본 테스트까지 확인해줘.

| 문서 | 채울 내용 |
| --- | --- |
| [AGENTS.md](AGENTS.md) | 프로젝트 목적, 기술 스택, 실행 안내 위치 |
| [아키텍처](docs/architecture.md) | 실제 구조, 책임, 의존성과 상태 변경 경계 |
| [코드 컨벤션](docs/conventions.md) | 네이밍, 포매터, 테스트, Git 규칙 |
| [요구사항](docs/requirements.md) | 요구사항 ID, 관찰 가능한 동작, 계약과 오류 조건 |
| [보안](docs/security.md) | 인증·인가, 민감 데이터, 운영 설정의 출처 |
| [워크플로](docs/workflow.md) | 계획 위치, 승인 절차, 검증 명령 |
| [데이터베이스](docs/database.md) | 스키마, 무결성, 마이그레이션, 복구 |
| [API 계약](docs/api.md) | 인터페이스, 오류 응답, 호출자 호환성 |
| [테스트](docs/testing.md) | 검증 범위, 실행 명령, 환경과 데이터 |
