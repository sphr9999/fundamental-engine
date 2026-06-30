-- Xóa foreign key constraints từ bảng role_permission
ALTER TABLE role_permission DROP CONSTRAINT IF EXISTS fk_role_id;
ALTER TABLE role_permission DROP CONSTRAINT IF EXISTS fk_permission_id;

-- Xóa bảng role_permission nếu tồn tại
DROP TABLE IF EXISTS role_permission;

-- Xóa sequence role_permission_id_seq nếu tồn tại
DROP SEQUENCE IF EXISTS role_permission_id_seq;

-- Xóa bảng roles nếu tồn tại
DROP TABLE IF EXISTS roles;

-- Xóa sequence roles_id_seq nếu tồn tại
DROP SEQUENCE IF EXISTS roles_id_seq;
