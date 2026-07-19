CREATE TABLE activity_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID,
    user_name VARCHAR(100),
    action_type VARCHAR(50) NOT NULL,
    description TEXT,
    metadata TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_activity_log_created_at ON activity_log(created_at DESC);
CREATE INDEX idx_activity_log_action_type ON activity_log(action_type);
CREATE INDEX idx_activity_log_user_id ON activity_log(user_id);

ALTER TABLE activity_log ADD CONSTRAINT fk_activity_log_user 
    FOREIGN KEY (user_id) REFERENCES account(id) ON DELETE SET NULL;
