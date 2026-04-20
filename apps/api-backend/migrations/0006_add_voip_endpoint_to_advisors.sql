ALTER TABLE advisors
    ADD COLUMN voip_endpoint_arn VARCHAR(512),
    ADD COLUMN voip_device_token VARCHAR(128);

CREATE INDEX idx_advisors_voip_endpoint ON advisors (voip_endpoint_arn) WHERE voip_endpoint_arn IS NOT NULL;
