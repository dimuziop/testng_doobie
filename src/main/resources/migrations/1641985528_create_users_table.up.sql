CREATE TABLE users
(
    id         uuid              NOT NULL,
    full_name  character varying NOT NULL,
    name       character varying NOT NULL,
    last_name  character varying NOT NULL,
    created_at timestamp         NOT NULL,
    updated_at timestamp         NULL default NULL,
    deleted_at timestamp         NULL default NULL
);