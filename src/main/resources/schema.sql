create table if not exists processed_events (
    event_id varchar(80) primary key,
    order_id varchar(80) not null,
    status varchar(30) not null,
    first_seen_at timestamp not null,
    processed_at timestamp null,
    updated_at timestamp not null,
    last_error varchar(1000) null
);

create index if not exists idx_processed_events_order_id
    on processed_events(order_id);

create index if not exists idx_processed_events_status
    on processed_events(status);
