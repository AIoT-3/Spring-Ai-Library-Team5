CREATE SEQUENCE IF NOT EXISTS public.book_sequence START WITH 1 INCREMENT BY 1000;

CREATE TABLE IF NOT EXISTS public.books (
    edition_publish_date date,
    first_publish_date date,
    price numeric(10, 2),
    created_at timestamp with time zone NOT NULL,
    id bigint NOT NULL,
    updated_at timestamp with time zone,
    category varchar(20),
    isbn varchar(20) NOT NULL UNIQUE,
    subtitle varchar(500),
    title varchar(500) NOT NULL,
    author_name varchar(1000),
    book_content text,
    image_url text,
    publisher_name varchar(255),
    embedding varbinary,
    PRIMARY KEY (id)
);
