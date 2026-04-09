from src.advisor.domain.value_objects import AdvisorId
from src.conversation.domain.entities import Conversation, Message
from src.conversation.domain.ports import ConversationRepository
from src.conversation.domain.value_objects import ConversationId, MessageSender
from src.shared.infrastructure.database import Database


class PostgresConversationRepository(ConversationRepository):
    def __init__(self, db: Database):
        self._db = db

    async def save(self, conversation: Conversation) -> None:
        async with self._db.pool.acquire() as conn:
            async with conn.transaction():
                # Upsert conversación
                await conn.execute(
                    """
                    INSERT INTO conversations (id, advisor_id, advisor_name, client_name, created_at)
                    VALUES ($1, $2, $3, $4, $5)
                    ON CONFLICT (id) DO UPDATE SET
                        advisor_name = EXCLUDED.advisor_name,
                        client_name = EXCLUDED.client_name
                    """,
                    conversation.id.value,
                    conversation.advisor_id.value,
                    conversation.advisor_name,
                    conversation.client_name,
                    conversation.created_at,
                )

                # Reemplazar mensajes (delete + insert para simplicidad)
                await conn.execute(
                    "DELETE FROM messages WHERE conversation_id = $1",
                    conversation.id.value,
                )

                for i, msg in enumerate(conversation.messages):
                    await conn.execute(
                        """
                        INSERT INTO messages (conversation_id, sender_name, is_advisor, text, timestamp, position)
                        VALUES ($1, $2, $3, $4, $5, $6)
                        """,
                        conversation.id.value,
                        msg.sender.name,
                        msg.sender.is_advisor,
                        msg.text,
                        msg.timestamp,
                        i,
                    )

    async def find_by_id(self, conversation_id: ConversationId) -> Conversation | None:
        row = await self._db.fetchrow("SELECT * FROM conversations WHERE id = $1", conversation_id.value)
        if not row:
            return None
        return await self._build_conversation(row)

    async def find_by_id_and_advisor(
        self, conversation_id: ConversationId, advisor_id: AdvisorId
    ) -> Conversation | None:
        row = await self._db.fetchrow(
            "SELECT * FROM conversations WHERE id = $1 AND advisor_id = $2",
            conversation_id.value,
            advisor_id.value,
        )
        if not row:
            return None
        return await self._build_conversation(row)

    async def find_all_by_advisor(self, advisor_id: AdvisorId) -> list[Conversation]:
        rows = await self._db.fetch(
            "SELECT * FROM conversations WHERE advisor_id = $1 ORDER BY created_at DESC",
            advisor_id.value,
        )
        conversations = []
        for row in rows:
            conv = await self._build_conversation(row)
            conversations.append(conv)
        return conversations

    async def _build_conversation(self, row) -> Conversation:
        message_rows = await self._db.fetch(
            "SELECT * FROM messages WHERE conversation_id = $1 ORDER BY position",
            row["id"],
        )

        messages = []
        for m in message_rows:
            sender = (
                MessageSender.advisor(m["sender_name"]) if m["is_advisor"] else MessageSender.client(m["sender_name"])
            )
            messages.append(Message(sender=sender, text=m["text"], timestamp=m["timestamp"]))

        return Conversation(
            id=ConversationId(row["id"]),
            advisor_id=AdvisorId(row["advisor_id"]),
            advisor_name=row["advisor_name"],
            client_name=row["client_name"],
            messages=messages,
            created_at=row["created_at"],
        )
