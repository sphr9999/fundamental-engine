-- Tạo sequence users_id_seq
CREATE SEQUENCE users_id_seq
    START WITH 1
    INCREMENT BY 1;

-- Tạo bảng users
CREATE TABLE users (
                       id int DEFAULT nextval('users_id_seq') NOT NULL UNIQUE PRIMARY KEY,
                       username varchar(64),
                       full_name varchar(64),
                       email varchar(128),
                       password varchar(128),
                       created_at timestamp DEFAULT current_timestamp,
                       updated_at timestamp DEFAULT current_timestamp
);

-- Tạo trigger cho bảng users
CREATE TRIGGER trigger_update_updated_at_users
    BEFORE UPDATE ON users
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trigger_update_created_at_users
    BEFORE INSERT ON users
    FOR EACH ROW
    EXECUTE FUNCTION update_created_at_column();

-- Tạo sequence user_permission_id_seq
CREATE SEQUENCE user_permission_id_seq
    START WITH 1
    INCREMENT BY 1;

-- Tạo bảng user_permission
CREATE TABLE user_permission (
                                 id int DEFAULT nextval('user_permission_id_seq') NOT NULL UNIQUE PRIMARY KEY,
                                 user_id int NOT NULL,
                                 permission_id int NOT NULL
);

-- Thêm foreign key constraint cho bảng user_permission
ALTER TABLE user_permission
    ADD CONSTRAINT fk_user_permission_user_id FOREIGN KEY (user_id)
        REFERENCES users (id)
        ON DELETE CASCADE
        ON UPDATE CASCADE;

ALTER TABLE user_permission
    ADD CONSTRAINT fk_user_permission_permission_id FOREIGN KEY (permission_id)
        REFERENCES permissions (id)
        ON DELETE CASCADE
        ON UPDATE CASCADE;

-- Tạo sequence user_role_id_seq
CREATE SEQUENCE user_role_id_seq
    START WITH 1
    INCREMENT BY 1;

-- Tạo bảng user_role
CREATE TABLE user_role (
                           id int DEFAULT nextval('user_role_id_seq') NOT NULL UNIQUE PRIMARY KEY,
                           user_id int NOT NULL,
                           role_id int NOT NULL
);

-- Thêm foreign key constraint cho bảng user_role
ALTER TABLE user_role
    ADD CONSTRAINT fk_user_role_user_id FOREIGN KEY (user_id)
        REFERENCES users (id)
        ON DELETE CASCADE
        ON UPDATE CASCADE;

ALTER TABLE user_role
    ADD CONSTRAINT fk_user_role_role_id FOREIGN KEY (role_id)
        REFERENCES roles (id)
        ON DELETE CASCADE
        ON UPDATE CASCADE;
