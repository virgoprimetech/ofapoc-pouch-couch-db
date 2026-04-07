-- liquibase formatted sql

-- changeset identity:006-seed-permissions splitStatements:false

INSERT INTO permissions (id, code, resource, action, display_name, description, created_at) VALUES
('00000000-0000-0000-0000-000000000101', 'property:read', 'property', 'read', 'Read Properties', 'View property details', NOW()),
('00000000-0000-0000-0000-000000000102', 'property:create', 'property', 'create', 'Create Properties', 'Create new properties', NOW()),
('00000000-0000-0000-0000-000000000103', 'property:update', 'property', 'update', 'Update Properties', 'Update property details', NOW()),
('00000000-0000-0000-0000-000000000104', 'property:delete', 'property', 'delete', 'Delete Properties', 'Delete properties', NOW()),

('00000000-0000-0000-0000-000000000201', 'reservation:read', 'reservation', 'read', 'Read Reservations', 'View reservations', NOW()),
('00000000-0000-0000-0000-000000000202', 'reservation:create', 'reservation', 'create', 'Create Reservations', 'Create new reservations', NOW()),
('00000000-0000-0000-0000-000000000203', 'reservation:update', 'reservation', 'update', 'Update Reservations', 'Update reservation details', NOW()),
('00000000-0000-0000-0000-000000000204', 'reservation:delete', 'reservation', 'delete', 'Delete Reservations', 'Cancel/delete reservations', NOW()),

('00000000-0000-0000-0000-000000000301', 'guest:read', 'guest', 'read', 'Read Guests', 'View guest profiles', NOW()),
('00000000-0000-0000-0000-000000000302', 'guest:create', 'guest', 'create', 'Create Guests', 'Create new guest profiles', NOW()),
('00000000-0000-0000-0000-000000000303', 'guest:update', 'guest', 'update', 'Update Guests', 'Update guest profiles', NOW()),
('00000000-0000-0000-0000-000000000304', 'guest:delete', 'guest', 'delete', 'Delete Guests', 'Delete guest profiles', NOW()),

('00000000-0000-0000-0000-000000000401', 'room:read', 'room', 'read', 'Read Rooms', 'View room details', NOW()),
('00000000-0000-0000-0000-000000000402', 'room:create', 'room', 'create', 'Create Rooms', 'Create new rooms', NOW()),
('00000000-0000-0000-0000-000000000403', 'room:update', 'room', 'update', 'Update Rooms', 'Update room details', NOW()),
('00000000-0000-0000-0000-000000000404', 'room:delete', 'room', 'delete', 'Delete Rooms', 'Delete rooms', NOW()),

('00000000-0000-0000-0000-000000000501', 'housekeeping:read', 'housekeeping', 'read', 'Read Housekeeping', 'View housekeeping tasks', NOW()),
('00000000-0000-0000-0000-000000000502', 'housekeeping:create', 'housekeeping', 'create', 'Create Housekeeping Tasks', 'Create housekeeping tasks', NOW()),
('00000000-0000-0000-0000-000000000503', 'housekeeping:update', 'housekeeping', 'update', 'Update Housekeeping', 'Update housekeeping tasks', NOW()),
('00000000-0000-0000-0000-000000000504', 'housekeeping:delete', 'housekeeping', 'delete', 'Delete Housekeeping', 'Delete housekeeping tasks', NOW()),

('00000000-0000-0000-0000-000000000601', 'user:read', 'user', 'read', 'Read Users', 'View user profiles', NOW()),
('00000000-0000-0000-0000-000000000602', 'user:create', 'user', 'create', 'Create Users', 'Create new users', NOW()),
('00000000-0000-0000-0000-000000000603', 'user:update', 'user', 'update', 'Update Users', 'Update user profiles', NOW()),
('00000000-0000-0000-0000-000000000604', 'user:delete', 'user', 'delete', 'Delete Users', 'Delete users', NOW()),

('00000000-0000-0000-0000-000000000701', 'role:read', 'role', 'read', 'Read Roles', 'View roles', NOW()),
('00000000-0000-0000-0000-000000000702', 'role:create', 'role', 'create', 'Create Roles', 'Create new roles', NOW()),
('00000000-0000-0000-0000-000000000703', 'role:update', 'role', 'update', 'Update Roles', 'Update roles', NOW()),
('00000000-0000-0000-0000-000000000704', 'role:delete', 'role', 'delete', 'Delete Roles', 'Delete roles', NOW()),

('00000000-0000-0000-0000-000000000801', 'assignment:read', 'assignment', 'read', 'Read Assignments', 'View role assignments', NOW()),
('00000000-0000-0000-0000-000000000802', 'assignment:create', 'assignment', 'create', 'Create Assignments', 'Assign roles to users', NOW()),
('00000000-0000-0000-0000-000000000803', 'assignment:delete', 'assignment', 'delete', 'Delete Assignments', 'Revoke role assignments', NOW()),

('00000000-0000-0000-0000-000000000901', 'report:read', 'report', 'read', 'Read Reports', 'View reports', NOW()),
('00000000-0000-0000-0000-000000000902', 'report:export', 'report', 'export', 'Export Reports', 'Export reports', NOW()),

('00000000-0000-0000-0000-000000001001', 'settings:read', 'settings', 'read', 'Read Settings', 'View settings', NOW()),
('00000000-0000-0000-0000-000000001002', 'settings:update', 'settings', 'update', 'Update Settings', 'Update settings', NOW());

-- changeset identity:006-seed-roles

INSERT INTO roles (id, code, display_name, scope, is_system, description, created_at, updated_at) VALUES
('10000000-0000-0000-0000-000000000001', 'SYSTEM_ADMIN', 'System Administrator', 'GLOBAL', TRUE, 'Full system access', NOW(), NOW()),
('10000000-0000-0000-0000-000000000002', 'TENANT_ADMIN', 'Tenant Administrator', 'CHAIN', TRUE, 'Chain-level administration', NOW(), NOW()),
('10000000-0000-0000-0000-000000000003', 'PROPERTY_MANAGER', 'Property Manager', 'PROPERTY', TRUE, 'Property-level administration', NOW(), NOW()),
('10000000-0000-0000-0000-000000000004', 'FRONT_DESK_AGENT', 'Front Desk Agent', 'PROPERTY', TRUE, 'Front desk operations', NOW(), NOW()),
('10000000-0000-0000-0000-000000000005', 'HOUSEKEEPING_SUPERVISOR', 'Housekeeping Supervisor', 'PROPERTY', TRUE, 'Manage housekeeping', NOW(), NOW()),
('10000000-0000-0000-0000-000000000006', 'HOUSEKEEPER', 'Housekeeper', 'PROPERTY', TRUE, 'Basic housekeeping', NOW(), NOW());

-- changeset identity:006-seed-role-permissions

INSERT INTO role_permissions (id, role_id, permission_id, created_at)
SELECT uuidv7(), '10000000-0000-0000-0000-000000000001', id, NOW()
FROM permissions;

INSERT INTO role_permissions (id, role_id, permission_id, created_at)
SELECT uuidv7(), '10000000-0000-0000-0000-000000000002', id, NOW()
FROM permissions
WHERE code NOT IN ('settings:update', 'role:delete', 'user:delete');

INSERT INTO role_permissions (id, role_id, permission_id, created_at)
SELECT uuidv7(), '10000000-0000-0000-0000-000000000003', id, NOW()
FROM permissions
WHERE resource IN ('property', 'reservation', 'guest', 'room', 'housekeeping', 'user', 'assignment', 'report')
  AND action IN ('read', 'create', 'update', 'delete', 'export');

INSERT INTO role_permissions (id, role_id, permission_id, created_at)
SELECT uuidv7(), '10000000-0000-0000-0000-000000000004', id, NOW()
FROM permissions
WHERE code IN (
               'property:read','reservation:read','reservation:create','reservation:update',
               'guest:read','guest:create','guest:update','room:read','report:read'
    );

INSERT INTO role_permissions (id, role_id, permission_id, created_at)
SELECT uuidv7(), '10000000-0000-0000-0000-000000000005', id, NOW()
FROM permissions
WHERE code IN (
               'property:read','room:read','room:update',
               'housekeeping:read','housekeeping:create','housekeeping:update','housekeeping:delete',
               'report:read'
    );

INSERT INTO role_permissions (id, role_id, permission_id, created_at)
SELECT uuidv7(), '10000000-0000-0000-0000-000000000006', id, NOW()
FROM permissions
WHERE code IN (
               'room:read','housekeeping:read','housekeeping:update'
    );