CREATE TABLE credit_applications (
    id UUID PRIMARY KEY,
    advisor_id UUID NOT NULL REFERENCES advisors(id),
    status VARCHAR(20) NOT NULL DEFAULT 'draft',

    -- Applicant data
    applicant_full_name VARCHAR(255) NOT NULL,
    applicant_phone VARCHAR(50),
    applicant_estimated_income VARCHAR(100),
    applicant_employment_type VARCHAR(100),

    -- Product request
    product_type VARCHAR(30) NOT NULL,
    product_amount VARCHAR(100),
    product_term VARCHAR(100),
    product_location VARCHAR(255),

    conversation_summary TEXT NOT NULL,
    rejection_reason TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_applications_advisor_id ON credit_applications (advisor_id);
CREATE INDEX idx_applications_status ON credit_applications (status);
