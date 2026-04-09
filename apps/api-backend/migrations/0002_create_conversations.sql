CREATE TABLE conversations (
    id UUID PRIMARY KEY,
    advisor_id UUID NOT NULL REFERENCES advisors(id),
    advisor_name VARCHAR(255) NOT NULL,
    client_name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_conversations_advisor_id ON conversations (advisor_id);

CREATE TABLE messages (
    id SERIAL PRIMARY KEY,
    conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    sender_name VARCHAR(255) NOT NULL,
    is_advisor BOOLEAN NOT NULL,
    text TEXT NOT NULL,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    position INTEGER NOT NULL
);

CREATE INDEX idx_messages_conversation_id ON messages (conversation_id);
