-- Xóa foreign key constraints từ bảng user_role
ALTER TABLE user_role DROP CONSTRAINT IF EXISTS fk_user_role_user_id;
ALTER TABLE user_role DROP CONSTRAINT IF EXISTS fk_user_role_role_id;

-- Xóa foreign key constraints từ bảng user_permission
ALTER TABLE user_permission DROP CONSTRAINT IF EXISTS fk_user_permission_user_id;
ALTER TABLE user_permission DROP CONSTRAINT IF EXISTS fk_user_permission_permission_id;

-- Xóa bảng user_role nếu tồn tại
DROP TABLE IF EXISTS user_role;

-- Xóa bảng user_permission nếu tồn tại
DROP TABLE IF EXISTS user_permission;

-- Xóa bảng users nếu tồn tại
DROP TABLE IF EXISTS users;

-- Xóa sequence user_role_id_seq nếu tồn tại
DROP SEQUENCE IF EXISTS user_role_id_seq;

-- Xóa sequence user_permission_id_seq nếu tồn tại
DROP SEQUENCE IF EXISTS user_permission_id_seq;

-- Xóa sequence users_id_seq nếu tồn tại
DROP SEQUENCE IF EXISTS users_id_seq;
