CREATE TABLE search_events (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    keyword VARCHAR(80) NOT NULL,
    result_count INT NOT NULL DEFAULT 0,
    searched_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_search_event_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT ck_search_event_result CHECK (result_count >= 0)
);
CREATE INDEX idx_search_event_keyword_time ON search_events(keyword, searched_at);
CREATE INDEX idx_search_event_time ON search_events(searched_at);

CREATE TABLE user_search_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    keyword VARCHAR(80) NOT NULL,
    result_count INT NOT NULL DEFAULT 0,
    searched_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_search_keyword UNIQUE (user_id, keyword),
    CONSTRAINT fk_search_history_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT ck_search_history_result CHECK (result_count >= 0)
);
CREATE INDEX idx_search_history_user_time ON user_search_history(user_id, searched_at);
