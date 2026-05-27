-- Схема БД для MySQL (курсовой проект UAS Simulator)

CREATE TABLE drones (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    callsign        VARCHAR(32)  NOT NULL,
    model           VARCHAR(64)  NOT NULL,
    max_speed_ms    DOUBLE       NOT NULL,
    max_altitude_m  DOUBLE       NOT NULL,
    battery_capacity_mah DOUBLE  NOT NULL,
    status          VARCHAR(32)  NOT NULL,
    latitude        DOUBLE       NOT NULL DEFAULT 0,
    longitude       DOUBLE       NOT NULL DEFAULT 0,
    altitude_m      DOUBLE       NOT NULL DEFAULT 0,
    battery_percent DOUBLE       NOT NULL DEFAULT 100,
    created_at      TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    source_type     VARCHAR(32)  NOT NULL DEFAULT 'SIMULATOR',
    connection_protocol VARCHAR(32) NOT NULL DEFAULT 'NONE',
    endpoint        VARCHAR(512),
    api_key         VARCHAR(64),
    external_device_id VARCHAR(128),
    connected       BOOLEAN      NOT NULL DEFAULT FALSE,
    last_telemetry_at TIMESTAMP(6) NULL,
    CONSTRAINT uk_drones_callsign UNIQUE (callsign)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE simulation_sessions (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    name         VARCHAR(128) NOT NULL,
    drone_id     BIGINT       NOT NULL,
    status       VARCHAR(32)  NOT NULL,
    created_at   TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    started_at   TIMESTAMP(6) NULL,
    finished_at  TIMESTAMP(6) NULL,
    description  VARCHAR(512),
    CONSTRAINT fk_sessions_drone FOREIGN KEY (drone_id) REFERENCES drones (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE waypoints (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id     BIGINT NOT NULL,
    sequence_order INT    NOT NULL,
    latitude       DOUBLE NOT NULL,
    longitude      DOUBLE NOT NULL,
    altitude_m     DOUBLE NOT NULL,
    speed_ms       DOUBLE NOT NULL,
    CONSTRAINT fk_waypoints_session FOREIGN KEY (session_id)
        REFERENCES simulation_sessions (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE telemetry_records (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id       BIGINT       NOT NULL,
    drone_id         BIGINT       NOT NULL,
    timestamp        TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    latitude         DOUBLE,
    longitude        DOUBLE,
    altitude_m       DOUBLE,
    speed_ms         DOUBLE,
    heading_deg      DOUBLE,
    pitch_deg        DOUBLE,
    roll_deg         DOUBLE,
    battery_percent  DOUBLE,
    waypoint_index   INT,
    progress_percent DOUBLE,
    INDEX idx_telemetry_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE alerts (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id    BIGINT NULL,
    drone_id      BIGINT NULL,
    level         VARCHAR(32)  NOT NULL,
    message       VARCHAR(512) NOT NULL,
    created_at    TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    acknowledged  BOOLEAN      NOT NULL DEFAULT FALSE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE app_users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(128) NOT NULL,
    full_name VARCHAR(128) NOT NULL,
    role VARCHAR(32) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    last_login_at TIMESTAMP(6) NULL,
    CONSTRAINT uk_app_users_username UNIQUE (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE user_actions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NULL,
    method VARCHAR(32) NOT NULL,
    path VARCHAR(512) NOT NULL,
    action_type VARCHAR(128) NOT NULL,
    status INT NOT NULL,
    details VARCHAR(1024),
    ip_address VARCHAR(64),
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    reviewed BOOLEAN NOT NULL DEFAULT FALSE,
    admin_comment VARCHAR(512),
    INDEX idx_user_actions_created_at (created_at),
    CONSTRAINT fk_user_actions_user FOREIGN KEY (user_id) REFERENCES app_users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
