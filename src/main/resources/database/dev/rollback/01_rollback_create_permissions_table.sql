-- Xóa bảng permissions
DROP TABLE IF EXISTS permissions;

-- Xóa sequence permissions_id_seq
DROP SEQUENCE IF EXISTS permissions_id_seq;

-- Xóa function update_updated_at_column
DROP FUNCTION IF EXISTS update_updated_at_column();

-- Xóa function update_created_at_column
DROP FUNCTION IF EXISTS update_created_at_column();

