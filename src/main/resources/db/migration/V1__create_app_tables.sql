create table if not exists chat_sessions (
    chat_id text primary key,
    last_response_id text,
    updated_at bigint not null
);

create table if not exists chat_memories (
    chat_id text primary key,
    memory_text text,
    updated_at bigint not null
);

create table if not exists processed_updates (
    update_id bigint primary key,
    chat_id text not null,
    message_id bigint not null,
    processed_at bigint not null,
    reply_text text,
    conversation_state text,
    suggested_replies text,
    sent_at bigint
);

create table if not exists media_group_buffers (
    key text primary key,
    deadline_at bigint not null
);

create table if not exists media_group_messages (
    update_id bigint primary key,
    media_group_key text not null,
    message_id bigint not null,
    payload text not null,
    constraint fk_media_group_key foreign key (media_group_key)
        references media_group_buffers (key)
        on delete cascade
);

create index if not exists idx_processed_updates_chat_id on processed_updates(chat_id);
create index if not exists idx_media_group_messages_media_group_key on media_group_messages(media_group_key);
