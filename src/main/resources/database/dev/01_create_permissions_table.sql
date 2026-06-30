-- Tạo sequence
CREATE SEQUENCE permissions_id_seq
    START WITH 1
    INCREMENT BY 1;

-- Tạo bảng permissions
CREATE TABLE permissions (
                             id int DEFAULT nextval('permissions_id_seq') NOT NULL UNIQUE PRIMARY KEY,
                             name varchar(50),
                             display_name varchar(50),
                             created_at timestamp DEFAULT current_timestamp,
                             updated_at timestamp DEFAULT current_timestamp
);


-- Tạo function update_updated_at_column
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS '
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
' LANGUAGE plpgsql;

-- Tạo function update_created_at_column
CREATE OR REPLACE FUNCTION update_created_at_column()
RETURNS TRIGGER AS '
BEGIN
    NEW.created_at = CURRENT_TIMESTAMP;
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
' LANGUAGE plpgsql;

-- Tạo trigger cho bảng permissions
CREATE TRIGGER trigger_update_updated_at_permissions
    BEFORE UPDATE ON permissions
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trigger_update_created_at_permissions
    BEFORE INSERT ON permissions
    FOR EACH ROW
    EXECUTE FUNCTION update_created_at_column();
