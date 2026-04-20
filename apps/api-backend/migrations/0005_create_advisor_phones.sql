-- Mapeo de números telefónicos (Telnyx/VoIP) a asesores
CREATE TABLE advisor_phones (
    phone_number VARCHAR(20) PRIMARY KEY,
    advisor_id UUID NOT NULL REFERENCES advisors(id),
    provider VARCHAR(20) NOT NULL DEFAULT 'telnyx',
    assigned_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_advisor_phones_advisor_id ON advisor_phones (advisor_id);

-- Campos VoIP en call_recordings
ALTER TABLE call_recordings
    ADD COLUMN voip_call_id VARCHAR(100),
    ADD COLUMN recording_key VARCHAR(500),
    ADD COLUMN recording_url TEXT;

CREATE INDEX idx_call_recordings_voip_call_id ON call_recordings (voip_call_id);
