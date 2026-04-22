create table if not exists chat_sessions_new (
    chat_id text primary key,
    last_response_id text,
    updated_at bigint not null
);

insert into chat_sessions_new (chat_id, last_response_id, updated_at)
select chat_id, last_response_id, updated_at
from chat_sessions;

drop table chat_sessions;

alter table chat_sessions_new rename to chat_sessions;

create table if not exists chat_memories_new (
    chat_id text primary key,
    memory_text text,
    updated_at bigint not null
);

insert into chat_memories_new (chat_id, memory_text, updated_at)
select chat_id, memory_text, updated_at
from chat_memories;

drop table chat_memories;

alter table chat_memories_new rename to chat_memories;

create table if not exists processed_updates_new (
    update_id bigint primary key,
    chat_id text not null,
    message_id bigint not null,
    processed_at bigint not null,
    reply_text text,
    conversation_state text,
    suggested_replies text,
    sent_at bigint
);

insert into processed_updates_new (
    update_id,
    chat_id,
    message_id,
    processed_at,
    reply_text,
    conversation_state,
    suggested_replies,
    sent_at
)
select
    update_id,
    chat_id,
    message_id,
    processed_at,
    reply_text,
    conversation_state,
    suggested_replies,
    sent_at
from processed_updates;

drop table processed_updates;

alter table processed_updates_new rename to processed_updates;

create table if not exists media_group_buffers_new (
    key text primary key,
    deadline_at bigint not null
);

create table if not exists media_group_messages_new (
    update_id bigint primary key,
    media_group_key text not null,
    message_id bigint not null,
    payload text not null,
    constraint fk_media_group_key foreign key (media_group_key)
        references media_group_buffers (key)
        on delete cascade
);

insert into media_group_messages_new (update_id, media_group_key, message_id, payload)
select update_id, media_group_key, message_id, payload
from media_group_messages;

drop table media_group_messages;

insert into media_group_buffers_new (key, deadline_at)
select key, deadline_at
from media_group_buffers;

drop table media_group_buffers;

alter table media_group_buffers_new rename to media_group_buffers;

alter table media_group_messages_new rename to media_group_messages;

create index if not exists idx_processed_updates_chat_id on processed_updates(chat_id);
create index if not exists idx_media_group_messages_media_group_key on media_group_messages(media_group_key);
