# Firebase Cloud Messaging Web Push

## 발송 흐름

프론트엔드는 Firebase Messaging SDK의 `getToken()`으로 얻은 FCM registration token을 인증 후 등록합니다.
서버는 예약 작업을 짧은 비관적 락 트랜잭션에서 `PROCESSING`으로 선점하고 커밋한 뒤,
사용자 알림 설정과 활성 구독을 조회합니다. 실제 FCM 호출 직전에 설정을 한 번 더 확인하고
`notification`과 문자열 `data`가 함께 든 메시지를 최대 500개 FCM token씩 전송합니다.
`PROCESSING` 작업은 기본 5분 lease를 가지며, 서버 종료 등으로 완료되지 못한 작업은 lease 만료 후
다시 선점됩니다. lease는 `PUSH_PROCESSING_LEASE_MILLIS`로 조정할 수 있습니다.

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
  "fcmToken": "fcm-registration-token",
  "deviceName": "MacBook Air",
  "browser": "Chrome"
}
```

```json
{"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"subscriptionId":1,"active":true}}
```

동일 FID는 새 행을 만들지 않고 현재 인증 사용자에게 재연결하며 최신 FCM token으로 갱신합니다.
`installationId`에는 최대 255자의 Firebase Installation ID를, `fcmToken`에는 최대 512자의
Firebase Messaging `getToken()` 결과를 전달합니다. 기기명과 브라우저는 각각 최대 100자입니다.
두 식별자는 모두 필수이며, 누락하거나 빈 문자열을 전달하면 `INVALID_INPUT`으로 거절합니다.
동일 `installationId`를 다시 등록하면 최신 token으로 갱신하고 구독을 `active=true`로 재활성화합니다.
다른 설치가 이미 사용 중인 `fcmToken`은 `PUSH_SUBSCRIPTION_CONFLICT`로 거절합니다.

### 해제

```bash
curl -X DELETE 'http://localhost:8080/api/v1/push-subscriptions/1' \
  -H 'Authorization: Bearer YOUR_JWT'
```

현재 사용자 소유 구독만 `active=false`가 됩니다.

## FCM token 및 payload 계약

`installationId`는 설치 식별과 token 갱신에 사용하고, 실제 발송 대상에는 `fcmToken`을 사용합니다.
서버 data payload는 모두 문자열이며 기본 키는 `type`, `referenceId`, `clickUrl`,
예약 작업의 `referenceType`입니다. 현재 유형과 설정 매핑은 다음과 같습니다.

| type | 사용자 설정 |
|---|---|
| `SERVICE` | `isServiceAlarmEnabled` |
| `TASK_DEADLINE` | `isServiceAlarmEnabled` + 과업별 `notification_setting.is_enabled` |
| `THIRTY_MIN_PACK` | `thirtyMinPackAlarmEnabled` |

## 환경과 실행

```dotenv
FIREBASE_ENABLED=false
FIREBASE_PROJECT_ID=
GOOGLE_APPLICATION_CREDENTIALS=
PUSH_PROCESSING_LEASE_MILLIS=300000
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
| 영구 대상 오류 | `UNREGISTERED` | 해당 구독 비활성화, 실패 횟수 증가 |
| 일시 오류 | `UNAVAILABLE`, `INTERNAL`, `QUOTA_EXCEEDED` | 1분, 5분, 15분, 1시간 뒤 재시도 후 `FAILED` |
| 설정 오류 | `INVALID_ARGUMENT`, sender/project/auth/permission 계열 | 구독 유지, 구조화 로그, 작업 재시도 후보 |

FCM token 원문은 로그에 기록하지 않고 구독 ID와 오류 분류만 기록합니다. 부분 성공은 응답 인덱스를 입력
token 인덱스에 대응시켜 개별 반영하고, 다음 시도에는 실패한 token만 포함하여 이미 성공한 브라우저의
중복 수신을 방지합니다. 실패 결과는 발송 당시 구독 ID와 FCM token이 모두 일치할 때만 반영하므로,
동시에 새 token이 등록된 경우 이전 token의 실패로 최신 구독을 비활성화하지 않습니다.

## 예약 API(내부 서비스)

운영 공개 테스트 발송 API는 없습니다. 도메인 코드는
`PushNotificationJobService.schedule(...)`와 알림 유형별 동기화·취소 메서드를 호출합니다.
`dedupeKey`는 유형·대상·발생 시각을 포함해야 합니다.

## 30분 Pack

30분 Pack은 `Task.deadline`이 아니라 같은 요일의 연속된 두 `Timetable` 블록 사이 공백을 사용합니다.
공백이 정확히 30분이고 공백 시작 시각이 미래인 경우에만 `THIRTY_MIN_PACK` 작업을 예약합니다.
첫 시간표 전과 마지막 시간표 후의 빈 시간은 대상이 아닙니다.

시간표 추가·수정·삭제와 `thirtyMinPackAlarmEnabled` 변경 시 미래 예약을 전부 재계산합니다.
매일 00:10에도 활성 사용자의 미래 예약 범위를 보충하며 기본 범위는 14일입니다.

알림 클릭 화면은 `availableMinutes`로 아래 API를 호출합니다.

```http
GET /api/recommendations/pack30?availableMinutes=30
Authorization: Bearer {JWT}
```

서버는 `(100 - 현재 진행률) / 남은 예상 시간`으로 분당 진행률을 계산하고 상위 3개 과업과
`min(분당 진행률 × 공백 시간, 남은 진행률)`을 반환합니다. 완료 과업과 남은 예상 시간이 없는
과업은 제외합니다.

브라우저 알림 권한 팝업은 프론트엔드 책임입니다. 시간표 등록 UI 완료 후 권한을 요청하고,
권한이 `granted`인 경우에만 FCM token 등록 및 30분 Pack 설정 ON 요청을 보냅니다. 브라우저 권한이
`denied`이면 토글을 OFF로 유지하고 브라우저 설정 변경을 안내해야 합니다.

## 과업 마감 캘린더 알림

과업 생성·수정 시 사용자 기본 알림과 과업별 알림이 모두 ON이면 `TASK_DEADLINE` 작업을 예약합니다.
과업 알림 선택지는 1일 전(1440), 3일 전(4320), 1주일 전(10080)입니다.

현재 과업은 마감 날짜만 저장하므로 마감일 기준 시각은 기본 09:00으로 설정합니다.
예를 들어 마감일이 8월 10일이고 1일 전을 선택하면 8월 9일 09:00에 발송합니다.
기준 시각은 `TASK_DEADLINE_TIME`으로 변경할 수 있습니다.

마감일·알림 오프셋·알림 ON/OFF·과업 상태가 변경되면 기존 PENDING 작업을 취소하고 재계산합니다.

### 반복 과업 회차 생성과 알림

- 반복 과업은 직전 회차의 마감일 00:05(KST)에 다음 회차를 미리 생성합니다.
- 예를 들어 매주 반복 과업의 직전 마감일이 8월 2일이면 8월 2일 스케줄러 실행 시
  마감일이 8월 9일인 다음 회차를 생성합니다.
- 서버가 중단되어 실행 시점을 놓쳤다면 마지막 생성 회차 다음부터 누락 회차를 복구하고,
  오늘보다 뒤인 회차가 하나 확보될 때까지 생성합니다.
- 같은 `source_task_id`와 `deadline` 조합에는 DB unique 제약을 적용하고 반복 규칙 행을
  비관적 락으로 선점하여 다중 인스턴스에서도 중복 생성을 방지합니다.
- 원본 반복 과업의 알림 시점 또는 알림 ON/OFF가 변경되면 이미 생성된 미래 회차에도
  동일한 설정을 반영하고 마감 푸시 예약을 다시 계산합니다.
- 반복 규칙을 변경하거나 해제하면 기존 미래 회차와 해당 회차의 PENDING 푸시 예약을
  제거한 뒤 변경된 규칙의 최초 회차를 다시 계산합니다. 과거 및 당일 회차는 이력 보존을
  위해 유지합니다.
과업 완료·삭제 또는 사용자 기본 알림 OFF 시 미래 작업을 취소합니다. 매일 00:20에도 기본 알림이
활성화된 사용자의 예약을 복구합니다.
