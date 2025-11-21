CREATE TABLE subscription_plan (
    id UUID PRIMARY KEY,
    plan_code VARCHAR(50) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    daily_quota INTEGER,
    monthly_quota INTEGER,
    burst_quota JSONB,
    allow_queue_overflow BOOLEAN NOT NULL DEFAULT FALSE,
    metadata JSONB,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_subscription_plan_code ON subscription_plan(plan_code);

CREATE TABLE user_subscription (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    plan_code VARCHAR(50) NOT NULL,
    current_period_start TIMESTAMPTZ NOT NULL,
    current_period_end TIMESTAMPTZ NOT NULL,
    status VARCHAR(32) NOT NULL,
    trial_end TIMESTAMPTZ,
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_user_subscription_user_status ON user_subscription(user_id, status);
CREATE INDEX idx_user_subscription_window ON user_subscription(user_id, current_period_end DESC);

CREATE TABLE usage_record (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    user_id UUID NOT NULL,
    workspace_id UUID,
    plan_code VARCHAR(50),
    tier VARCHAR(16),
    route_id VARCHAR(100),
    action VARCHAR(64),
    used_at TIMESTAMPTZ NOT NULL,
    payload JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_usage_record_event ON usage_record(event_id);
CREATE INDEX idx_usage_record_user_time ON usage_record(user_id, used_at);
CREATE INDEX idx_usage_record_route ON usage_record(route_id);

CREATE TABLE usage_daily_aggregate (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL,
    usage_date DATE NOT NULL,
    metric VARCHAR(64) NOT NULL,
    count BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_usage_daily_user_metric ON usage_daily_aggregate(user_id, usage_date, metric);
CREATE INDEX idx_usage_daily_metric_date ON usage_daily_aggregate(metric, usage_date);

CREATE TABLE usage_monthly_aggregate (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL,
    year_month CHAR(7) NOT NULL,
    metric VARCHAR(64) NOT NULL,
    count BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_usage_monthly_user_metric ON usage_monthly_aggregate(user_id, year_month, metric);
CREATE INDEX idx_usage_monthly_metric_month ON usage_monthly_aggregate(metric, year_month);

CREATE TABLE queue_item (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    workspace_id UUID,
    tier VARCHAR(16) NOT NULL,
    route_id VARCHAR(100) NOT NULL,
    action VARCHAR(64) NOT NULL,
    payload_ref VARCHAR(512) NOT NULL,
    status VARCHAR(32) NOT NULL,
    priority INTEGER NOT NULL DEFAULT 0,
    queued_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    last_error JSONB
);

CREATE INDEX idx_queue_item_status_priority ON queue_item(status, priority, queued_at);
CREATE INDEX idx_queue_item_user_status ON queue_item(user_id, status);
