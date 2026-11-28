# 인증 서비스 API 계약 초안

보호자 가입 이메일 인증(AUTH-006)의 HTTP 계약은 아래 상세 절을 따른다. 나머지 표는 호출자가 기대할 동작을 합의하기 위한 초안이다. 기능 범위와 수용 기준은 [요구사항](requirements.md), 자격 증명과 세션의 처리 기준은 [보안](security.md)을 따른다.

| 후보 작업 | 성공 시 호출자가 확인할 결과 | 계약에서 정할 실패 조건 |
| --- | --- | --- |
| 로그인 (AUTH-001) | 로그인에 성공하면 Auth Service에서 JWT를 받는다. | 자격 증명 불일치, 인증 제한, 일시적인 의존 서비스 장애 |
| 세션 갱신 (AUTH-003) | 유효한 Refresh Token으로 갱신한다. 이전 토큰의 교체 여부는 팀 논의 후 정한다. | 만료, 폐기, 동시 갱신, 재사용 |
| 로그아웃 (AUTH-004) | 현재 세션을 로그아웃한다. 기존 토큰의 효력은 확정된 보안 정책에 따라 명시한다. | 이미 종료된 세션, 권한이 없는 세션, 처리 실패 |
| 보호자 회원가입 (AUTH-005) | 인증된 이메일과 필수 입력을 검증해 svc_identity에 저장하고 커밋 후 성공을 응답한다. | 이메일 인증 증명 오류, 입력 오류, 아이디·닉네임·이메일 중복 |
| 아이디 중복 확인 (AUTH-012) | 아이디 사용 가능 여부를 확인한다. | 입력 오류, 호출 제한 |
| 닉네임 중복 확인 (AUTH-014) | 닉네임 사용 가능 여부를 확인한다. | 입력 오류, 호출 제한 |
| 이메일 인증번호 발송 (AUTH-006) | 가입 이메일로 인증번호를 발송한다. | 입력 오류, 발송 제한, 의존성 장애 |
| 이메일 인증번호 검증 (AUTH-006) | 입력한 인증번호를 검증하고 일회용 가입 증명값을 반환한다. | 불일치, 사용 불가, 검증 시도 제한, 의존성 장애 |

## 보호자 가입 API 상세 초안

아래 경로, 필드명과 상태 코드는 검토용이다. 모든 요청 본문과 응답 본문은 JSON을 제안한다. 비밀번호·인증번호·가입 증명값은 POST 요청 본문으로 전달한다.

| 작업 | 메서드와 경로 | 요청 | 성공 응답 |
| --- | --- | --- | --- |
| 아이디 중복 확인 | `POST /api/v1/auth/signup/login-id/check` | `loginId` | `200`, `available` (boolean) |
| 닉네임 중복 확인 | `POST /api/v1/auth/signup/nickname/check` | `nickname` | `200`, `available` (boolean) |
| 보호자 회원가입 | `POST /api/v1/auth/signup/caregiver` | 아래 가입 필드 참조 | `201`, 본문 없음 |

- 가입 필드는 `loginId`, `name`, `nickname`, `password`, `phoneNumber`, `gender`, `birthDate`, `address`, `termsAgreed`, `email`, `emailVerificationProof`다. 초안에서는 `termsAgreed`를 boolean, 나머지를 string으로 전달하며 `birthDate`는 `YYYY-MM-DD`를 사용한다.
- 닉네임은 2~10자다. 이메일 비교와 비밀번호 정책은 [보안 정책](security.md)을, 성별 값은 [데이터 설계](database.md)를 따른다. 아이디 허용 문자·길이와 비교 기준, 전화번호·주소 형식, 값별 최대 길이는 계약 검토 시 확정한다.
- 가입 요청의 필드 검증 및 이메일 인증 증명 누락·만료·불일치·재사용은 `400`, 최종 저장의 중복은 `409`, 호출 제한은 `429`를 제안한다. 가입 오류 본문은 미결정이다.
- 최종 가입은 아이디·닉네임·이메일의 고유 제약을 적용한다. 중복 확인 결과를 증명하는 별도 필드는 없다. 가입 성공은 `svc_identity` 커밋을 의미한다.

## 보호자 가입 이메일 인증 (AUTH-006)

요청 본문은 `application/json`이다. 로그인 없이 호출할 수 있다. 직접 접속에서는 실제 연결 IP를 사용하며, Gateway 경유 요청의 IP 처리 기준은 [Gateway 내부 인터페이스](#gateway-내부-인터페이스)를 따른다.

### 공통 응답 형식

두 API는 아래 공통 응답 계약을 따른다.

- 성공 본문은 숫자 `code`와 `data`를 포함한다. 반환 데이터가 없으면 `data`는 `null`이다.
- 실패 본문은 숫자 `code`, 고정 문자열 `errorCode`, 사람이 읽을 수 있는 `message`를 포함한다.
- 본문의 `code`는 실제 HTTP 상태와 일치한다. 프런트엔드의 오류 분기는 `errorCode`를 사용한다.
- 필드별 유효성 오류는 `VALIDATION_FAILED`로 반환하고 오류가 한 개여도 `details` 배열에 `field`와 `message`를 포함한다. 식별 가능한 모든 필드 오류를 수집한다.
- 비즈니스 오류와 필드를 식별할 수 없는 잘못된 JSON에는 `details`를 생략한다.
- `rejectedValue`는 기본 생략한다. 비밀번호·인증번호·가입 증명값 등 민감한 입력은 포함하지 않는다. 내부 예외나 DB 오류를 응답에 노출하지 않는다.

### 이메일 인증번호 발송

`POST /api/v1/auth/signup/email/code`

입력한 이메일로 보호자 가입용 인증번호를 발송한다.

#### 요청

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `email` | string | O | 인증번호를 받을 이메일 |

```http
POST /api/v1/auth/signup/email/code HTTP/1.1
Content-Type: application/json

{
  "email": "user@example.com"
}
```

#### 성공 응답

```http
HTTP/1.1 202 Accepted
Content-Type: application/json

{
  "code": 202,
  "data": null
}
```

`data`는 `null`이다. Brevo가 발송 요청을 접수하고 Redis의 인증번호가 검증 가능한 상태가 됐음을 의미한다. 실제 수신함 도착은 Brevo의 별도 배달 이벤트로 확인한다.

#### 실패 응답

| HTTP 상태 | 오류 코드 | 조건 |
| --- | --- | --- |
| `400` | `VALIDATION_FAILED` | 이메일 누락·타입·형식 오류, 잘못된 JSON |
| `403` | `REQUEST_SOURCE_UNTRUSTED` | 유효한 요청 출처 IP를 확인할 수 없음 |
| `429` | `EMAIL_RESEND_COOLDOWN` | 같은 이메일의 발송 예약·재발송 간격 60초 제한 |
| `429` | `EMAIL_SEND_LIMIT_EXCEEDED` | 같은 이메일로 최근 1시간 발송 5회 도달 |
| `429` | `RATE_LIMITED` | IP·서비스 전체 발송 한도 초과 |
| `503` | `SERVICE_UNAVAILABLE` | Brevo HTTP API 또는 Redis 처리 실패 |

```http
HTTP/1.1 400 Bad Request
Content-Type: application/json

{
  "code": 400,
  "errorCode": "VALIDATION_FAILED",
  "message": "요청값이 올바르지 않습니다.",
  "details": [
    {
      "field": "email",
      "message": "이메일 형식이 올바르지 않습니다."
    }
  ]
}
```

```http
HTTP/1.1 429 Too Many Requests
Content-Type: application/json
Retry-After: 60

{
  "code": 429,
  "errorCode": "EMAIL_RESEND_COOLDOWN",
  "message": "인증번호는 60초 간격으로 요청할 수 있습니다."
}
```

이메일별 시간당 한도에 도달하면 `errorCode`는 `EMAIL_SEND_LIMIT_EXCEEDED`, `message`는 `이메일별 인증번호 발송은 최근 1시간에 최대 5회까지 가능합니다.`다. 재발송 간격과 시간당 한도에 동시에 걸리면 시간당 한도를 안내한다.

`Retry-After`는 모든 제한을 고려해 재시도까지 기다릴 초다. 예시의 `60`은 고정값이 아니며 현재 제한 상태에 따라 달라진다.

#### 처리 규칙

- 이메일 앞뒤 공백을 제거하고 도메인을 소문자로 변환한다. 로컬 파트는 유지한다.
- 인증번호는 ASCII 숫자 6자리이며 발송 접수 후 5분간 유효하다.
- 새 인증번호 발송이 성공하면 이전 인증번호를 폐기한다. 발송 실패 시 아직 유효한 기존 코드는 유지한다.
- 발송 예약에 성공한 요청은 Brevo 발송 실패 시에도 한도에 반영한다. 제한 수치는 [보안 정책](security.md#보호자-가입용-이메일-인증)을 따른다.

### 이메일 인증번호 검증

`POST /api/v1/auth/signup/email/verify`

인증번호를 확인하고 보호자 가입에 사용할 일회용 증명값을 발급한다.

#### 요청

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `email` | string | O | 인증번호를 받은 이메일 |
| `code` | string | O | ASCII 숫자 6자리. 선행 0을 보존하도록 문자열로 전달 |

```http
POST /api/v1/auth/signup/email/verify HTTP/1.1
Content-Type: application/json

{
  "email": "user@example.com",
  "code": "012345"
}
```

#### 성공 응답

```http
HTTP/1.1 200 OK
Content-Type: application/json
Cache-Control: no-store

{
  "code": 200,
  "data": {
    "emailVerificationProof": "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG"
  }
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `data.emailVerificationProof` | string | 인증 이메일과 보호자 가입 목적에 묶인 일회용 증명값. 유효기간 10분 |

증명값 예시는 설명용이다. 클라이언트는 실제 값을 해석하거나 변경하지 않고 후속 회원가입 요청에 그대로 전달한다.

#### 실패 응답

| HTTP 상태 | 오류 코드 | 조건 |
| --- | --- | --- |
| `400` | `VALIDATION_FAILED` | 필수 필드 누락·타입 오류, 잘못된 JSON, 인증번호 형식 오류 |
| `400` | `VERIFICATION_CODE_MISMATCH` | 활성 인증번호와 제출한 번호가 불일치 |
| `400` | `VERIFICATION_CODE_UNAVAILABLE` | 사용 가능한 코드 없음. 미발급·만료·이미 소비·실패 한도에 따른 무효화 포함 |
| `403` | `REQUEST_SOURCE_UNTRUSTED` | 유효한 요청 출처 IP를 확인할 수 없음 |
| `429` | `RATE_LIMITED` | IP별 검증 요청 또는 이메일별 검증 실패 한도 초과 |
| `503` | `SERVICE_UNAVAILABLE` | Redis 처리 실패 |

```http
HTTP/1.1 400 Bad Request
Content-Type: application/json

{
  "code": 400,
  "errorCode": "VERIFICATION_CODE_MISMATCH",
  "message": "인증번호가 일치하지 않습니다."
}
```

```http
HTTP/1.1 400 Bad Request
Content-Type: application/json

{
  "code": 400,
  "errorCode": "VERIFICATION_CODE_UNAVAILABLE",
  "message": "사용할 수 없는 인증번호입니다. 인증번호를 다시 요청해 주세요."
}
```

#### 처리 규칙

- 같은 코드의 동시 검증 요청에서는 한 요청만 성공한다.
- 코드별 다섯 번째 불일치 시 코드를 무효화한다. 코드 재발급으로 이메일별 누적 실패 한도는 초기화되지 않는다.
- 증명값 해시를 키로 이메일과 목적을 Redis에 TTL 10분으로 저장한다.
- 코드 소비 후 증명값 저장에 실패하면 재인증한다. 증명값 유실·만료 시에도 재인증한다.
- 회원가입과 증명값의 일회용 소비는 후속 AUTH-005 범위다.

### 공통 오류 예시와 미지원 미디어 타입

다음 오류 예시는 두 API에 공통으로 적용된다. 오류 본문은 `code`, `errorCode`, `message`를 사용한다. 여러 조건이 동시에 실패하면 처리 순서에 따라 먼저 확인한 오류가 반환될 수 있다.

```http
HTTP/1.1 403 Forbidden
Content-Type: application/json

{
  "code": 403,
  "errorCode": "REQUEST_SOURCE_UNTRUSTED",
  "message": "요청 출처를 확인할 수 없습니다."
}
```

```http
HTTP/1.1 503 Service Unavailable
Content-Type: application/json

{
  "code": 503,
  "errorCode": "SERVICE_UNAVAILABLE",
  "message": "일시적으로 요청을 처리할 수 없습니다. 잠시 후 다시 시도해 주세요."
}
```

JSON 이외의 요청은 `415 Unsupported Media Type`과 공통 오류 본문을 반환한다. `errorCode`는 `UNSUPPORTED_MEDIA_TYPE`, `message`는 `Content-Type은 application/json이어야 합니다.`다. 검증 요청은 IP 제한 확인 후 미디어 타입과 본문을 검사한다.

### 문서 작성 참고

상태 코드별 응답·헤더·예시 구분은 OpenAPI와 Swagger 공식 가이드를 참고한다. 현재 계약은 이 Markdown 문서에 기록하며 기계 판독용 OpenAPI 파일은 아직 제공하지 않는다. [OpenAPI Responses Object](https://spec.openapis.org/oas/v3.1.2.html#responses-object) · [Swagger 응답 작성 가이드](https://swagger.io/docs/specification/v3_0/describing-responses/) · [Swagger 예시 작성 가이드](https://swagger.io/docs/specification/v3_0/adding-examples/)

## Gateway 내부 인터페이스

보호 요청(AUTH-002)의 내부 인터페이스는 [아키텍처](architecture.md)와 [보안 정책](security.md)에 정의된 경계를 따른다. 사용자 ID의 전달 필드와 값 형식은 인터페이스가 확정되면 이 문서에 명시한다.

AUTH-006의 Gateway는 클라이언트가 보낸 `X-Client-IP`를 제거하고 관측한 클라이언트 IP 리터럴 하나를 넣는다. Auth Service는 신뢰 대상으로 설정된 Gateway 피어에서 온 요청에 한해 이 값을 사용한다. 그 외 직접 요청은 실제 연결 IP를 사용하고 전달된 IP 헤더를 무시한다. Gateway가 설정되지 않아도 직접 요청할 수 있다. 신뢰된 Gateway 요청에서 헤더가 누락되거나 잘못되면 403으로 거부한다.

## 계약 확정에 필요한 결정

- 호출자 유형과 신뢰 경계를 정한 뒤 전송 방식, 버전, 요청·응답 형식과 인증 정보 전달 방식을 정의한다.
- 웹 API를 채택하면 경로와 HTTP 메서드, 상태 코드, 오류 본문을 작업별로 명시한다. 자격 증명 오류 응답에서 계정 존재 여부를 추론할 수 있는지도 검토한다.
- 재시도 가능한 실패와 중복 요청의 효과를 구분한다. 계약 변경 시 영향을 받는 호출자와 전환 절차를 기록한다.
