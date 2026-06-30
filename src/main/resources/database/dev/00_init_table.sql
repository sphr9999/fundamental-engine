-- ------------------------------- --
-- account
-- ------------------------------- --

CREATE SEQUENCE IF NOT EXISTS ACCOUNT_ACCT_ID_SEQ
    increment 1
    minvalue 1
    maxvalue 9223372036854775807
    start 1
    cache 1


CREATE TABLE test
(
    id                   bigint  NOT NULL            DEFAULT nextval('account_acct_id_seq'::regclass),
    name             varchar(200) unique,
    business_partner_num varchar(200) unique,                          -- Business Partner number
    full_name            text,                                         -- user full name

    -- primary key (id)
    CONSTRAINT account_id_base_pkey PRIMARY KEY (id)
);
