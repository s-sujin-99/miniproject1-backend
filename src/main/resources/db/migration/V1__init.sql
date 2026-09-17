-- 초기 스키마. com.pharmaprice 도메인 패키지의 엔티티(Task009)를 기준으로 작성했다 — DATABASE.md 원문과 다른 지점(§2):
--   refresh_token 제외, app_user.provider 추가, app_user/price_report.deleted_at(Soft Delete).
-- 약국명·약품명 부분검색용. PostgreSQL 기본 contrib라 별도 설치 불필요.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- ── region ──────────────────────────────────────────────
CREATE TABLE region (
    code       VARCHAR(10) PRIMARY KEY,
    sido       VARCHAR(20) NOT NULL,
    sigungu    VARCHAR(30) NOT NULL,
    center_lat DOUBLE PRECISION NOT NULL,
    center_lng DOUBLE PRECISION NOT NULL
);

-- ── app_user ────────────────────────────────────────────
-- password_hash는 NULL 허용 — GOOGLE 로그인 사용자는 비밀번호가 없다(shrimp-rules.md §5.3).
CREATE TABLE app_user (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(100),
    nickname      VARCHAR(30) NOT NULL,
    role          VARCHAR(20) NOT NULL,
    status        VARCHAR(20) NOT NULL,
    provider      VARCHAR(20) NOT NULL,
    report_count  INTEGER NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL,
    deleted_at    TIMESTAMPTZ
);

-- ── pharmacy ────────────────────────────────────────────
CREATE TABLE pharmacy (
    id              BIGSERIAL PRIMARY KEY,
    hira_code       VARCHAR(30) UNIQUE,
    name            VARCHAR(100) NOT NULL,
    address_road    VARCHAR(255),
    address_jibun   VARCHAR(255),
    region_code     VARCHAR(10) REFERENCES region (code),
    lat             DOUBLE PRECISION NOT NULL,
    lng             DOUBLE PRECISION NOT NULL,
    phone           VARCHAR(20),
    business_hours  JSONB,
    is_active       BOOLEAN NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL
);

-- ── drug ────────────────────────────────────────────────
CREATE TABLE drug (
    id            BIGSERIAL PRIMARY KEY,
    item_seq      VARCHAR(20) UNIQUE,
    name          VARCHAR(200) NOT NULL,
    display_name  VARCHAR(100) NOT NULL,
    maker         VARCHAR(100),
    category      VARCHAR(50) NOT NULL,
    form          VARCHAR(50),
    package_unit  VARCHAR(50) NOT NULL,
    otc_flag      BOOLEAN NOT NULL,
    base_price    INTEGER,
    image_url     VARCHAR(500),
    created_at    TIMESTAMPTZ NOT NULL
);

-- ── uploaded_file ───────────────────────────────────────
-- price_report의 receipt_file_id가 참조하므로 price_report보다 먼저 만든다.
CREATE TABLE uploaded_file (
    id            BIGSERIAL PRIMARY KEY,
    original_name VARCHAR(255) NOT NULL,
    stored_path   VARCHAR(500) NOT NULL,
    content_type  VARCHAR(100) NOT NULL,
    size_bytes    BIGINT NOT NULL,
    uploaded_by   BIGINT REFERENCES app_user (id),
    created_at    TIMESTAMPTZ NOT NULL
);

-- ── price_report (핵심 테이블) ──────────────────────────
-- status(ACTIVE/HIDDEN)는 노출 상태, 삭제는 deleted_at으로 한다(shrimp-rules.md §2·§4.3).
CREATE TABLE price_report (
    id               BIGSERIAL PRIMARY KEY,
    pharmacy_id      BIGINT NOT NULL REFERENCES pharmacy (id),
    drug_id          BIGINT NOT NULL REFERENCES drug (id),
    user_id          BIGINT REFERENCES app_user (id),
    price            INTEGER NOT NULL CHECK (price BETWEEN 100 AND 200000),
    purchased_at     DATE NOT NULL,
    source           VARCHAR(20) NOT NULL,
    status           VARCHAR(20) NOT NULL,
    flagged          BOOLEAN NOT NULL,
    flag_reason      VARCHAR(100),
    receipt_file_id  BIGINT REFERENCES uploaded_file (id),
    memo             VARCHAR(200),
    created_at       TIMESTAMPTZ NOT NULL,
    updated_at       TIMESTAMPTZ NOT NULL,
    deleted_at       TIMESTAMPTZ
);

-- ── pharmacy_drug_price_stat ────────────────────────────
CREATE TABLE pharmacy_drug_price_stat (
    id                BIGSERIAL PRIMARY KEY,
    pharmacy_id       BIGINT NOT NULL REFERENCES pharmacy (id),
    drug_id           BIGINT NOT NULL REFERENCES drug (id),
    rep_price         INTEGER NOT NULL,
    min_price         INTEGER NOT NULL,
    max_price         INTEGER NOT NULL,
    avg_price         INTEGER NOT NULL,
    report_count      INTEGER NOT NULL,
    last_reported_at  DATE NOT NULL,
    window_days       SMALLINT NOT NULL,
    calculated_at     TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_stat_pair UNIQUE (pharmacy_id, drug_id)
);

-- ── 인덱스 ──────────────────────────────────────────────
CREATE INDEX idx_pharmacy_lat_lng   ON pharmacy (lat, lng);
CREATE INDEX idx_pharmacy_region    ON pharmacy (region_code);
-- 연산자 클래스를 public으로 명시한다 — 앱 연결의 search_path가 pharmaprice(_test) 하나만으로
-- 좁혀져 있어(currentSchema JDBC 옵션), public에 설치된 pg_trgm 연산자 클래스가 안 보일 수 있다.
CREATE INDEX idx_pharmacy_name_trgm ON pharmacy USING gin (name public.gin_trgm_ops);

CREATE INDEX idx_drug_display_name_trgm ON drug USING gin (display_name public.gin_trgm_ops);
CREATE INDEX idx_drug_category          ON drug (category);
CREATE INDEX idx_drug_otc               ON drug (otc_flag) WHERE otc_flag = true;

-- 통계 재계산 시 가장 많이 타는 경로
CREATE INDEX idx_report_pair_active
    ON price_report (pharmacy_id, drug_id, purchased_at DESC)
    WHERE status = 'ACTIVE' AND flagged = false AND deleted_at IS NULL;

CREATE INDEX idx_report_drug    ON price_report (drug_id, purchased_at DESC);
CREATE INDEX idx_report_user    ON price_report (user_id, created_at DESC);
CREATE INDEX idx_report_flagged ON price_report (flagged) WHERE flagged = true;

CREATE INDEX idx_stat_drug_price ON pharmacy_drug_price_stat (drug_id, rep_price);
CREATE INDEX idx_stat_pharmacy   ON pharmacy_drug_price_stat (pharmacy_id);

-- 같은 사용자가 같은 (약국, 약품)에 같은 날 중복 제보하는 것을 막는다(DATABASE.md §3.5, F2-8).
-- (created_at::date)는 세션 TimeZone에 의존해 STABLE이라 인덱스 표현식으로 못 쓴다.
-- AT TIME ZONE 'Asia/Seoul'로 고정하면 timestamp가 되어 IMMUTABLE 캐스트가 된다.
-- deleted_at IS NULL 조건은 shrimp-rules.md §4.3 요구(소프트 삭제된 행은 중복 판정에서 제외).
CREATE UNIQUE INDEX uq_report_user_pair_day
    ON price_report (
        user_id, pharmacy_id, drug_id,
        ((created_at AT TIME ZONE 'Asia/Seoul')::date)
    )
    WHERE user_id IS NOT NULL AND status = 'ACTIVE' AND deleted_at IS NULL;
