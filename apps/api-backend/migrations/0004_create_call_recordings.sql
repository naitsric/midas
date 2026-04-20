CREATE TABLE call_recordings (
    id UUID PRIMARY KEY,
    advisor_id UUID NOT NULL REFERENCES advisors(id),
    client_name VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'recording',
    transcript TEXT NOT NULL DEFAULT '',
    duration_seconds INTEGER,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_call_recordings_advisor_id ON call_recordings (advisor_id);
CREATE INDEX idx_call_recordings_status ON call_recordings (status);
