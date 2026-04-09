CREATE TABLE advisors (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    phone VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'active',
    api_key VARCHAR(255) NOT NULL UNIQUE,
    suspension_reason TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_advisors_email ON advisors (email);
CREATE INDEX idx_advisors_api_key ON advisors (api_key);
CREATE INDEX idx_advisors_status ON advisors (status);
