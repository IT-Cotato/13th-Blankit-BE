# Firebase FID Web Push

## 발송 흐름

프론트엔드는 Firebase Web SDK에서 얻은 Firebase Installation ID(FID)를 인증 후 등록합니다.
서버는 예약 작업을 짧은 비관적 락 트랜잭션에서 `PROCESSING`으로 선점하고 커밋한 뒤,
사용자 알림 설정과 활성 구독을 조회합니다. 실제 FCM 호출 직전에 설정을 한 번 더 확인하고
`notification`과 문자열 `data`가 함께 든 메시지를 최대 500 FID씩 전송합니다.

`NotificationService`는 가입 시 기본 알림 설정 생성 책임을 그대로 유지합니다.
설정 판정은 `NotificationPreferenceService`, 구독 상태 반영과 발송 정책은
`PushNotificationService`, Firebase SDK 호출은 `FcmPushGateway`가 담당합니다.

## 프론트엔드 API

모든 응답은 `{ "code": "SUCCESS", "message": "요청이 성공했습니다.", "data": ... }` 형식입니다.

### 등록/갱신

```http
POST /api/v1/push-subscriptions
Authorization: Bearer {JWT}
Content-Type: application/json

{
  "installationId": "firebase-installation-id",
  "deviceName": "MacBook Air",
  "browser": "Chrome"
}
```

```json
{"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"subscriptionId":1,"active":true}}
```

동일 FID는 새 행을 만들지 않고 현재 인증 사용자에게 재연결합니다. `installationId`는 필수이며
최대 255자, 기기명과 브라우저는 각각 최대 100자입니다.

### 해제

```bash
curl -X DELETE 'http://localhost:8080/api/v1/push-subscriptions/1' \
  -H 'Authorization: Bearer YOUR_JWT'
```

현재 사용자 소유 구독만 `active=false`가 됩니다.

## FID 및 payload 계약

`installationId`에는 registration token이 아니라 Firebase Installations SDK의 FID를 전달합니다.
서버 data payload는 모두 문자열이며 기본 키는 `type`, `referenceId`, `clickUrl`,
예약 작업의 `referenceType`입니다. 현재 유형과 설정 매핑은 다음과 같습니다.

| type | 사용자 설정 |
|---|---|
| `SERVICE` | `isServiceAlarmEnabled` |
| `THIRTY_MIN_PACK` | `thirtyMinPackAlarmEnabled` |

## 환경과 실행

```dotenv
FIREBASE_ENABLED=false
FIREBASE_PROJECT_ID=
GOOGLE_APPLICATION_CREDENTIALS=
```

서비스 계정 JSON은 절대 저장소나 classpath에 넣지 않습니다. 로컬에서는 파일을 저장소 밖에 두고
`GOOGLE_APPLICATION_CREDENTIALS=/absolute/path/service-account.json`을 설정합니다.
운영에서는 Secret Manager/Kubernetes Secret 등을 읽기 전용 파일로 mount하고 같은 환경변수가
그 mount 경로를 가리키게 합니다. Firebase Console에서 프로젝트 생성, Web App 등록,
Cloud Messaging API 활성화, Web Push 인증서(VAPID) 설정, 서비스 계정에 FCM 발송 권한 부여가 필요합니다.

`FIREBASE_ENABLED=false`이면 credentials 없이도 정상 기동하고 발송 adapter만 비활성 구현을 사용합니다.
test 프로필은 이 값과 scheduler를 기본 비활성화합니다.

## 실패 정책

| 분류 | 코드 | 처리 |
|---|---|---|
| 영구 대상 오류 | `UNREGISTERED`, 정상 payload의 `INVALID_ARGUMENT` | 해당 구독 비활성화, 실패 횟수 증가 |
| 일시 오류 | `UNAVAILABLE`, `INTERNAL`, `QUOTA_EXCEEDED` | 1분, 5분, 15분, 1시간 뒤 재시도 후 `FAILED` |
| 설정 오류 | sender/project/auth/permission 계열 | 구독 유지, 구조화 로그, 작업 재시도 후보 |

FID 원문은 로그에 기록하지 않고 구독 ID와 오류 분류만 기록합니다. 부분 성공은 응답 인덱스를 입력
FID 인덱스에 대응시켜 개별 반영합니다.

## 예약 API(내부 서비스)

운영 공개 테스트 발송 API는 없습니다. 도메인 코드는
`PushNotificationJobService.schedule(...)`, `cancel(dedupeKey)`, `reschedule(...)`를 호출합니다.
`dedupeKey`는 유형·대상·발생 시각을 포함해야 합니다.

현재 `Task.deadline`은 날짜만 있고 과업 시작 시각이 없으므로 30분 전 예약을 Task CRUD에 연결하지
않았습니다. 연결하려면 발생 회차별 기준 `LocalDateTime`과 클릭 URL 계약이 필요합니다.
