-- Tạo sequence roles_id_seq
CREATE SEQUENCE roles_id_seq
    START WITH 1
    INCREMENT BY 1;

-- Tạo bảng roles
CREATE TABLE roles
(
    id           int       DEFAULT nextval('roles_id_seq') NOT NULL UNIQUE PRIMARY KEY,
    name         varchar(50),
    display_name varchar(50),
    created_at   timestamp DEFAULT current_timestamp,
    updated_at   timestamp DEFAULT current_timestamp
);

-- Tạo trigger cho bảng roles
CREATE TRIGGER trigger_update_updated_at
    BEFORE UPDATE
    ON roles
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trigger_update_created_at
    BEFORE INSERT
    ON roles
    FOR EACH ROW
    EXECUTE FUNCTION update_created_at_column();

-- Tạo sequence role_permission_id_seq
CREATE SEQUENCE role_permission_id_seq
    START WITH 1
    INCREMENT BY 1;

-- Tạo bảng role_permission
CREATE TABLE role_permission
(
    id            int DEFAULT nextval('role_permission_id_seq') NOT NULL UNIQUE PRIMARY KEY,
    role_id       int                                           NOT NULL,
    permission_id int                                           NOT NULL
);

-- Thêm foreign key constraint cho bảng role_permission
ALTER TABLE role_permission
    ADD CONSTRAINT fk_role_id FOREIGN KEY (role_id)
        REFERENCES roles (id)
        ON DELETE CASCADE
        ON UPDATE CASCADE;

ALTER TABLE role_permission
    ADD CONSTRAINT fk_permission_id FOREIGN KEY (permission_id)
        REFERENCES permissions (id)
        ON DELETE CASCADE
        ON UPDATE CASCADE;
