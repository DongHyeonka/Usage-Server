# Usage Domain Design

## 1. Scope & Goals
- Enforce FREE (10 req/day) and PRO (300 req/month) quotas with possible future policies (monthly for FREE, burst quota).
- Persist every usage event for auditing, billing, and analytics.
- Aggregate daily/monthly counts to evaluate quotas quickly.
- Support multiple enforcement strategies when quotas are exhausted (hard block vs. queue-only mode).
- Keep Gateway stateless: it emits `UsageEvent`s to Kafka; this service processes, persists, and updates quota state (optionally cached in Redis) that Gateway can query.

## 2. High-Level Flow
1. **Gateway** publishes `UsageEvent` (user, workspace, route, action, timestamp, metadata) to Kafka topic `usage.events`.
2. **Usage Service** consumes events, stores append-only `UsageRecord`s, updates aggregates, evaluates quotas, and emits `QuotaStateChanged` events when thresholds change.
3. **Quota state** is cached in Redis (`QuotaStateCache`) to allow Gateway to call a lightweight API before accepting work. Cache contains both daily (FREE) and monthly (PRO) windows.
4. **Admin UI** can manage `SubscriptionPlan` policies and inspect usage metrics.

## 3. Domain Model & Persistence

### 3.1 SubscriptionPlan
Stable configuration describing each tier.

| Column | Type | Notes |
| --- | --- | --- |
| `id` | UUID | Primary key |
| `plan_code` | VARCHAR (FREE, PRO, etc.) | Unique |
| `display_name` | VARCHAR | For admin UI |
| `daily_quota` | INT, nullable | `FREE` = 10 |
| `monthly_quota` | INT, nullable | `PRO` = 300 |
| `burst_quota` | JSON / INT, nullable | Optional per-feature overrides |
| `allow_queue_overflow` | BOOLEAN | Whether to allow queue-only mode |
| `metadata` | JSONB | Extensible attributes |
| `updated_at` | TIMESTAMP | auditing |

Indexes: `plan_code` unique.

### 3.2 UserSubscription
Represents a user’s active plan and billing state.

| Column | Type | Notes |
| --- | --- | --- |
| `id` | UUID | PK |
| `user_id` | UUID | FK to auth/user service |
| `plan_code` | VARCHAR | FK to `SubscriptionPlan.plan_code` |
| `current_period_start` | TIMESTAMP | e.g., 2025-11-01 00:00Z |
| `current_period_end` | TIMESTAMP | inclusive-exclusive window |
| `status` | ENUM (ACTIVE, CANCELED, TRIAL, GRACE) |
| `trial_end` | TIMESTAMP, nullable | for TRIAL |
| `metadata` | JSONB | billing ids |
| `created_at` / `updated_at` | TIMESTAMP | auditing |

Indexes: `(user_id, status)` for quick lookup; `(user_id, current_period_end)` to find current subscription.

### 3.3 UsageRecord (append-only)
Stores every gateway usage event.

| Column | Type | Notes |
| --- | --- | --- |
| `id` | UUID | PK |
| `event_id` | UUID | Source gateway id for idempotency |
| `user_id` | UUID | FK to subscription |
| `workspace_id` | UUID | Optional |
| `plan_code` | VARCHAR | Snapshot at event time |
| `tier` | ENUM | Derived from plan |
| `route_id` | VARCHAR | API route identifier |
| `action` | ENUM (CHAT_COMPLETION, EMBEDDING, etc.) |
| `used_at` | TIMESTAMP | event time |
| `payload` | JSONB | raw gateway metadata |

Indexes: `(user_id, used_at)` for range queries; `event_id` unique for dedupe.

### 3.4 UsageDailyAggregate
Maintains per-user per-day counts for quick FREE quota checks.

| Column | Type | Notes |
| --- | --- | --- |
| `user_id` | UUID | PK part |
| `date` | DATE | PK part |
| `metric` | VARCHAR | e.g., `CHAT_COMPLETION` |
| `count` | INT | incremented atomically |
| `updated_at` | TIMESTAMP | |

Composite PK: `(user_id, date, metric)`.

### 3.5 UsageMonthlyAggregate
Per-user per-month counters for PRO tier.

| Column | Type | Notes |
| --- | --- | --- |
| `user_id` | UUID | PK part |
| `year_month` | CHAR(7) (YYYY-MM) | PK part |
| `metric` | VARCHAR | e.g., `TOTAL` |
| `count` | INT | |
| `updated_at` | TIMESTAMP | |

### 3.6 QuotaStateCache (Redis)
Ephemeral structure for fast enforcement.

Key pattern: `quota:{userId}:{metric}:{periodKey}`

Value example:
```json
{
  "state": "HARD_LIMIT",
  "count": 12,
  "limit": 10,
  "window": "DAILY",
  "periodKey": "2025-11-21",
  "lastUpdated": "2025-11-21T03:00:00Z"
}
```
TTL matches window end (e.g., midnight UTC for daily).

## 4. Quota Evaluation Logic
1. On `UsageEvent` consumption:
   - Load active `UserSubscription` (cached by user).
   - Determine applicable plan quotas (daily/monthly/burst).
   - Increment `UsageRecord` (insert) and update aggregates.
   - Compare aggregate counts vs. quota limits.
2. When a limit is reached:
   - If `allow_queue_overflow` is `false`, publish `QuotaStateChanged` with state `HARD_LIMIT`; Gateway blocks immediately.
   - If `true`, set state `QUEUE_ONLY`, instructing Gateway to route requests to an async queue.
3. Emit metrics via Micrometer for monitoring (rate of limit hits, etc.).

Pseudocode:
```kotlin
fun handle(event: UsageEvent) {
  val subscription = userSubscriptionCache.get(event.userId)
  val plan = planCache.get(subscription.planCode)
  val dayCount = dailyRepo.increment(event, subscription)
  val monthCount = monthlyRepo.increment(event, subscription)
  val quotaState = quotaEvaluator.evaluate(plan, dayCount, monthCount)
  quotaCache.save(event.userId, quotaState)
  if (quotaState.changed) publishQuotaEvent(quotaState)
}
```

## 5. Kafka Contracts
- **Topic:** `usage.events`
- **UsageEvent payload:**
```json
{
  "eventId": "uuid",
  "userId": "uuid",
  "workspaceId": "uuid",
  "routeId": "chat.completions",
  "action": "CHAT_COMPLETION",
  "timestamp": "2025-11-21T02:33:00Z",
  "metadata": { "tokens": 512 }
}
```

- **QuotaStateChanged topic (optional):** downstream consumers (gateway, billing) can subscribe to real-time quota transitions.

## 6. Administrative Considerations
- `SubscriptionPlan` should be editable via admin UI with auditing.
- Plan changes should invalidate caches and possibly recompute quotas within the current period.
- Support future plans by simply inserting new `plan_code` rows.

## 7. Open Questions / Next Steps
1. Define the exact Kafka schema registry strategy (Avro/JSON Schema) for `UsageEvent`.
2. Clarify whether FREE monthly quota will be introduced; design already allows nullable monthly quota.
3. Decide on the queue system when `QUEUE_ONLY` state is enabled (e.g., Kafka dead-letter topic or internal job queue).
4. Determine retention policies for `UsageRecord` (cold storage vs. partitioned tables).

## 8. Queue Domain (2.2)

### 8.1 역할 및 플로우
- **목표:** PRO 사용자가 실시간 쿼터를 초과했을 때 요청을 폐기하지 않고 별도 Queue에 적재한 뒤, 백그라운드 Worker가 저렴한 모델이나 배치로 천천히 처리하도록 한다.
- **Gateway 동작:** 쿼터 상태가 `QUEUE_ONLY`인 경우 즉시 응답 대신 `QueueItem` 생성 요청을 Usage Server로 전달한다.
- **Queue Processor:** Kafka/DB 트리거 기반 Worker가 `PENDING` 항목을 가져와 처리하며, 처리 결과는 후속 스펙에 따라 Webhook/폴링/WebSocket 중 하나로 사용자에게 알린다.
- **정책 유연성:** 월/일 쿼터 정책과 독립적으로 동작하며, 관리자는 Queue 우선순위/처리량을 조정할 수 있다.

### 8.2 QueueItem 모델

| Column | Type | Notes |
| --- | --- | --- |
| `id` | UUID | PK, Queue 항목 식별자 |
| `user_id` | UUID | 요청 사용자 |
| `workspace_id` | UUID | Optional, 멀티 워크스페이스 구분 |
| `tier` | ENUM (FREE, PRO 등) | 당시 구독 등급 스냅샷 |
| `route_id` | VARCHAR | API 라우트 키 (chat.completions 등) |
| `action` | ENUM | CHAT_COMPLETION, EMBEDDING 등 |
| `payload_ref` | VARCHAR | 실제 payload를 저장한 위치(S3, Object Storage 키) |
| `status` | ENUM(PENDING, PROCESSING, COMPLETED, FAILED, CANCELED) | 현재 처리 상태 |
| `priority` | INT | PRO > FREE, 혹은 세분화된 우선순위 |
| `queued_at` | TIMESTAMP | 생성 시각 |
| `started_at` | TIMESTAMP, nullable | Worker가 집어간 시간 |
| `completed_at` | TIMESTAMP, nullable | 완료/실패 처리 시간 |
| `last_error` | JSONB | 실패 상세, optional |

#### 보조 인덱스
- `(status, priority, queued_at)` : Worker가 다음 작업을 가져갈 때 사용.
- `(user_id, status)` : 사용자별 Queue 상태 조회용.

### 8.3 QueueWorkerConfig 모델

| Column | Type | Notes |
| --- | --- | --- |
| `id` | UUID | PK |
| `queue_name` | VARCHAR | 예: `usage-overflow` |
| `max_parallelism` | INT | 동시에 처리 가능한 최대 작업 수 |
| `throttle_per_minute` | INT | 분당 처리량 제한 |
| `retry_policy` | JSONB | 재시도 횟수/백오프 설정 |
| `enabled` | BOOLEAN | 긴급 중단 시 false |
| `updated_by` | VARCHAR | 운영자 아이디 |
| `updated_at` | TIMESTAMP | 변경 시각 |

운영자는 Admin UI나 Config API를 통해 해당 값을 수정할 수 있으며, 변경 시 Worker는 즉시(또는 주기적으로) 설정을 리로드해야 한다.

### 8.4 처리 흐름
1. **Queue 적재:** Gateway → Usage Server API `POST /queue-items`. Usage Server는 `payload_ref`를 Object Storage에 저장 후 `QueueItem` INSERT.
2. **Worker Pull:** Worker(예: Spring Batch or Reactor Scheduler)가 `status=PENDING` 항목을 우선순위 순으로 select & lock 후 `PROCESSING` 상태로 업데이트.
3. **실행:** Worker가 느린 LLM/대체 모델 호출 후 결과를 보존(Storage + Kafka event)하고 `COMPLETED` 또는 `FAILED`로 상태 변경.
4. **결과 통지:** 후속 스펙에 맞춰 Webhook/Polling/WebSocket으로 전달. 실패 시 `retry_policy`에 따라 재시도하거나 `FAILED`로 고정.

### 8.5 Kafka 통합
- 필요 시 `queue.items` 토픽에 상태 변화를 내보내어 모니터링/알림 시스템이 구독 가능하도록 한다.
- Worker 스케일아웃을 Kafka Consumer Group으로 구성하면 자연스럽게 `max_parallelism`을 Consumer 수로 제한할 수 있다.

### 8.6 모니터링 포인트
- Micrometer 지표: Queue 깊이(`queue_items_pending`), 처리율(`queue_items_processed_per_minute`), 실패율.
- Actuator custom endpoint로 현재 Queue 상태/설정을 조회하도록 구현할 수 있다.

### 8.7 향후 과제
1. `payload_ref` 저장소 표준화 (S3, Redis, DB JSON 등).
2. Webhook 시그너처, 재시도 전략, 실패 DLQ 설계.
3. Queue 우선순위 정책을 계획 단위(FREE/PRO) 외에도 사용자 SLA 등으로 확장할지 결정.
