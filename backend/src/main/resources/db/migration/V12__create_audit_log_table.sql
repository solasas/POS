CREATE TABLE audit_log (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id    UUID         NOT NULL,
    action      VARCHAR(64)  NOT NULL,
    entity      VARCHAR(64)  NOT NULL,
    entity_id   UUID         NOT NULL,
    reason_code VARCHAR(64),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_log_actor_id ON audit_log (actor_id);
CREATE INDEX idx_audit_log_entity_entity_id ON audit_log (entity, entity_id);
