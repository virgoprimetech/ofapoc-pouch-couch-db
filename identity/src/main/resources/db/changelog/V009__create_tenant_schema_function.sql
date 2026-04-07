-- liquibase formatted sql

-- changeset identity:009-create-tenant-schema-function splitStatements:false
-- PostgreSQL function to create a complete tenant schema with all tables

CREATE OR REPLACE FUNCTION public.create_tenant_schema(p_schema_name VARCHAR)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
    -- Validate schema name format (must start with 'tenant_')
    IF p_schema_name NOT LIKE 'tenant_%' THEN
        RAISE EXCEPTION 'Schema name must start with "tenant_"';
    END IF;

    -- Create the schema
    EXECUTE format('CREATE SCHEMA IF NOT EXISTS %I', p_schema_name);

    -- Create users table
    EXECUTE format('
        CREATE TABLE IF NOT EXISTS %I.users (
            id              UUID PRIMARY KEY DEFAULT uuidv7(),
            username        VARCHAR(50) NOT NULL UNIQUE,
            email           VARCHAR(255) NOT NULL UNIQUE,
            password_hash   VARCHAR(255) NOT NULL,
            status          VARCHAR(20) NOT NULL DEFAULT ''PENDING'',
            first_name      VARCHAR(100),
            last_name       VARCHAR(100),
            phone           VARCHAR(50),
            preferred_language VARCHAR(10) DEFAULT ''en'',
            timezone        VARCHAR(50) DEFAULT ''UTC'',
            created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
            updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
            deleted_at      TIMESTAMP WITH TIME ZONE
        )', p_schema_name);

    -- Create indexes for users table
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_users_username ON %I.users(username) WHERE deleted_at IS NULL', p_schema_name);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_users_email ON %I.users(email) WHERE deleted_at IS NULL', p_schema_name);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_users_status ON %I.users(status) WHERE deleted_at IS NULL', p_schema_name);

    -- Create roles table
    EXECUTE format('
        CREATE TABLE IF NOT EXISTS %I.roles (
            id              UUID PRIMARY KEY DEFAULT uuidv7(),
            code            VARCHAR(50) NOT NULL UNIQUE,
            display_name    VARCHAR(100) NOT NULL,
            scope           VARCHAR(20) NOT NULL,
            is_system       BOOLEAN NOT NULL DEFAULT FALSE,
            description     TEXT,
            created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
            updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
            deleted_at      TIMESTAMP WITH TIME ZONE
        )', p_schema_name);

    -- Create indexes for roles table
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_roles_code ON %I.roles(code) WHERE deleted_at IS NULL', p_schema_name);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_roles_scope ON %I.roles(scope) WHERE deleted_at IS NULL', p_schema_name);

    -- Create permissions table
    EXECUTE format('
        CREATE TABLE IF NOT EXISTS %I.permissions (
            id              UUID PRIMARY KEY DEFAULT uuidv7(),
            code            VARCHAR(100) NOT NULL UNIQUE,
            resource        VARCHAR(50) NOT NULL,
            action          VARCHAR(50) NOT NULL,
            display_name    VARCHAR(100) NOT NULL,
            description     TEXT,
            created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
            deleted_at      TIMESTAMP WITH TIME ZONE
        )', p_schema_name);

    -- Create indexes for permissions table
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_permissions_code ON %I.permissions(code) WHERE deleted_at IS NULL', p_schema_name);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_permissions_resource ON %I.permissions(resource) WHERE deleted_at IS NULL', p_schema_name);

    -- Create role_permissions table
    EXECUTE format('
        CREATE TABLE IF NOT EXISTS %I.role_permissions (
            id              UUID PRIMARY KEY DEFAULT uuidv7(),
            role_id         UUID NOT NULL,
            permission_id   UUID NOT NULL,
            created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
            deleted_at      TIMESTAMP WITH TIME ZONE,
            CONSTRAINT fk_role_permissions_role FOREIGN KEY (role_id) REFERENCES %I.roles(id),
            CONSTRAINT fk_role_permissions_permission FOREIGN KEY (permission_id) REFERENCES %I.permissions(id),
            CONSTRAINT uq_role_permission UNIQUE (role_id, permission_id)
        )', p_schema_name, p_schema_name, p_schema_name);

    -- Create user_role_assignments table
    EXECUTE format('
        CREATE TABLE IF NOT EXISTS %I.user_role_assignments (
            id              UUID PRIMARY KEY DEFAULT uuidv7(),
            user_id         UUID NOT NULL,
            role_id         UUID NOT NULL,
            property_id     UUID,
            chain_id        UUID,
            valid_from      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
            valid_until     TIMESTAMP WITH TIME ZONE,
            created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
            updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
            deleted_at      TIMESTAMP WITH TIME ZONE,
            CONSTRAINT fk_user_role_assignments_user FOREIGN KEY (user_id) REFERENCES %I.users(id),
            CONSTRAINT fk_user_role_assignments_role FOREIGN KEY (role_id) REFERENCES %I.roles(id)
        )', p_schema_name, p_schema_name, p_schema_name);

    -- Create indexes for user_role_assignments table
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_user_role_assignments_user ON %I.user_role_assignments(user_id) WHERE deleted_at IS NULL', p_schema_name);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_user_role_assignments_role ON %I.user_role_assignments(role_id) WHERE deleted_at IS NULL', p_schema_name);
    -- Create partial unique index for active role assignments only
    EXECUTE format('CREATE UNIQUE INDEX IF NOT EXISTS idx_user_role_assignments_unique_active ON %I.user_role_assignments(user_id, role_id, property_id) WHERE deleted_at IS NULL', p_schema_name);

    -- Seed default roles
    EXECUTE format('
        INSERT INTO %I.roles (id, code, display_name, scope, is_system, description, created_at, updated_at) VALUES
        (''10000000-0000-0000-0000-000000000001'', ''SYSTEM_ADMIN'', ''System Administrator'', ''GLOBAL'', TRUE, ''Full system access'', NOW(), NOW()),
        (''10000000-0000-0000-0000-000000000002'', ''TENANT_ADMIN'', ''Tenant Administrator'', ''CHAIN'', TRUE, ''Chain-level administration'', NOW(), NOW()),
        (''10000000-0000-0000-0000-000000000003'', ''PROPERTY_MANAGER'', ''Property Manager'', ''PROPERTY'', TRUE, ''Property-level administration'', NOW(), NOW()),
        (''10000000-0000-0000-0000-000000000004'', ''FRONT_DESK_AGENT'', ''Front Desk Agent'', ''PROPERTY'', TRUE, ''Front desk operations'', NOW(), NOW()),
        (''10000000-0000-0000-0000-000000000005'', ''HOUSEKEEPING_SUPERVISOR'', ''Housekeeping Supervisor'', ''PROPERTY'', TRUE, ''Manage housekeeping'', NOW(), NOW()),
        (''10000000-0000-0000-0000-000000000006'', ''HOUSEKEEPER'', ''Housekeeper'', ''PROPERTY'', TRUE, ''Basic housekeeping'', NOW(), NOW())
        ON CONFLICT (code) DO NOTHING', p_schema_name);

    -- Seed default permissions
    EXECUTE format('
        INSERT INTO %I.permissions (id, code, resource, action, display_name, description, created_at) VALUES
        (''00000000-0000-0000-0000-000000000101'', ''property:read'', ''property'', ''read'', ''Read Properties'', ''View property details'', NOW()),
        (''00000000-0000-0000-0000-000000000102'', ''property:create'', ''property'', ''create'', ''Create Properties'', ''Create new properties'', NOW()),
        (''00000000-0000-0000-0000-000000000103'', ''property:update'', ''property'', ''update'', ''Update Properties'', ''Update property details'', NOW()),
        (''00000000-0000-0000-0000-000000000104'', ''property:delete'', ''property'', ''delete'', ''Delete Properties'', ''Delete properties'', NOW()),
        (''00000000-0000-0000-0000-000000000201'', ''reservation:read'', ''reservation'', ''read'', ''Read Reservations'', ''View reservations'', NOW()),
        (''00000000-0000-0000-0000-000000000202'', ''reservation:create'', ''reservation'', ''create'', ''Create Reservations'', ''Create new reservations'', NOW()),
        (''00000000-0000-0000-0000-000000000203'', ''reservation:update'', ''reservation'', ''update'', ''Update Reservations'', ''Update reservation details'', NOW()),
        (''00000000-0000-0000-0000-000000000204'', ''reservation:delete'', ''reservation'', ''delete'', ''Delete Reservations'', ''Cancel/delete reservations'', NOW()),
        (''00000000-0000-0000-0000-000000000301'', ''guest:read'', ''guest'', ''read'', ''Read Guests'', ''View guest profiles'', NOW()),
        (''00000000-0000-0000-0000-000000000302'', ''guest:create'', ''guest'', ''create'', ''Create Guests'', ''Create new guest profiles'', NOW()),
        (''00000000-0000-0000-0000-000000000303'', ''guest:update'', ''guest'', ''update'', ''Update Guests'', ''Update guest profiles'', NOW()),
        (''00000000-0000-0000-0000-000000000304'', ''guest:delete'', ''guest'', ''delete'', ''Delete Guests'', ''Delete guest profiles'', NOW()),
        (''00000000-0000-0000-0000-000000000401'', ''room:read'', ''room'', ''read'', ''Read Rooms'', ''View room details'', NOW()),
        (''00000000-0000-0000-0000-000000000402'', ''room:create'', ''room'', ''create'', ''Create Rooms'', ''Create new rooms'', NOW()),
        (''00000000-0000-0000-0000-000000000403'', ''room:update'', ''room'', ''update'', ''Update Rooms'', ''Update room details'', NOW()),
        (''00000000-0000-0000-0000-000000000404'', ''room:delete'', ''room'', ''delete'', ''Delete Rooms'', ''Delete rooms'', NOW()),
        (''00000000-0000-0000-0000-000000000501'', ''housekeeping:read'', ''housekeeping'', ''read'', ''Read Housekeeping'', ''View housekeeping tasks'', NOW()),
        (''00000000-0000-0000-0000-000000000502'', ''housekeeping:create'', ''housekeeping'', ''create'', ''Create Housekeeping Tasks'', ''Create housekeeping tasks'', NOW()),
        (''00000000-0000-0000-0000-000000000503'', ''housekeeping:update'', ''housekeeping'', ''update'', ''Update Housekeeping'', ''Update housekeeping tasks'', NOW()),
        (''00000000-0000-0000-0000-000000000504'', ''housekeeping:delete'', ''housekeeping'', ''delete'', ''Delete Housekeeping'', ''Delete housekeeping tasks'', NOW()),
        (''00000000-0000-0000-0000-000000000601'', ''user:read'', ''user'', ''read'', ''Read Users'', ''View user profiles'', NOW()),
        (''00000000-0000-0000-0000-000000000602'', ''user:create'', ''user'', ''create'', ''Create Users'', ''Create new users'', NOW()),
        (''00000000-0000-0000-0000-000000000603'', ''user:update'', ''user'', ''update'', ''Update Users'', ''Update user profiles'', NOW()),
        (''00000000-0000-0000-0000-000000000604'', ''user:delete'', ''user'', ''delete'', ''Delete Users'', ''Delete users'', NOW()),
        (''00000000-0000-0000-0000-000000000701'', ''role:read'', ''role'', ''read'', ''Read Roles'', ''View roles'', NOW()),
        (''00000000-0000-0000-0000-000000000702'', ''role:create'', ''role'', ''create'', ''Create Roles'', ''Create new roles'', NOW()),
        (''00000000-0000-0000-0000-000000000703'', ''role:update'', ''role'', ''update'', ''Update Roles'', ''Update roles'', NOW()),
        (''00000000-0000-0000-0000-000000000704'', ''role:delete'', ''role'', ''delete'', ''Delete Roles'', ''Delete roles'', NOW()),
        (''00000000-0000-0000-0000-000000000801'', ''assignment:read'', ''assignment'', ''read'', ''Read Assignments'', ''View role assignments'', NOW()),
        (''00000000-0000-0000-0000-000000000802'', ''assignment:create'', ''assignment'', ''create'', ''Create Assignments'', ''Assign roles to users'', NOW()),
        (''00000000-0000-0000-0000-000000000803'', ''assignment:delete'', ''assignment'', ''delete'', ''Delete Assignments'', ''Revoke role assignments'', NOW()),
        (''00000000-0000-0000-0000-000000000901'', ''report:read'', ''report'', ''read'', ''Read Reports'', ''View reports'', NOW()),
        (''00000000-0000-0000-0000-000000000902'', ''report:export'', ''report'', ''export'', ''Export Reports'', ''Export reports'', NOW()),
        (''00000000-0000-0000-0000-000000001001'', ''settings:read'', ''settings'', ''read'', ''Read Settings'', ''View settings'', NOW()),
        (''00000000-0000-0000-0000-000000001002'', ''settings:update'', ''settings'', ''update'', ''Update Settings'', ''Update settings'', NOW())
        ON CONFLICT (code) DO NOTHING', p_schema_name);

    -- Seed role_permissions for TENANT_ADMIN (all permissions except settings:update, role:delete, user:delete)
    EXECUTE format('
        INSERT INTO %I.role_permissions (id, role_id, permission_id, created_at)
        SELECT uuidv7(), ''10000000-0000-0000-0000-000000000002'', id, NOW()
        FROM %I.permissions
        WHERE code NOT IN (''settings:update'', ''role:delete'', ''user:delete'')
        ON CONFLICT (role_id, permission_id) DO NOTHING', p_schema_name, p_schema_name);

    -- Seed role_permissions for PROPERTY_MANAGER
    EXECUTE format('
        INSERT INTO %I.role_permissions (id, role_id, permission_id, created_at)
        SELECT uuidv7(), ''10000000-0000-0000-0000-000000000003'', id, NOW()
        FROM %I.permissions
        WHERE resource IN (''property'', ''reservation'', ''guest'', ''room'', ''housekeeping'', ''user'', ''assignment'', ''report'')
          AND action IN (''read'', ''create'', ''update'', ''delete'', ''export'')
        ON CONFLICT (role_id, permission_id) DO NOTHING', p_schema_name, p_schema_name);

    -- Seed role_permissions for FRONT_DESK_AGENT
    EXECUTE format('
        INSERT INTO %I.role_permissions (id, role_id, permission_id, created_at)
        SELECT uuidv7(), ''10000000-0000-0000-0000-000000000004'', id, NOW()
        FROM %I.permissions
        WHERE code IN (''property:read'', ''reservation:read'', ''reservation:create'', ''reservation:update'',
                       ''guest:read'', ''guest:create'', ''guest:update'', ''room:read'', ''report:read'')
        ON CONFLICT (role_id, permission_id) DO NOTHING', p_schema_name, p_schema_name);

    -- Seed role_permissions for HOUSEKEEPING_SUPERVISOR
    EXECUTE format('
        INSERT INTO %I.role_permissions (id, role_id, permission_id, created_at)
        SELECT uuidv7(), ''10000000-0000-0000-0000-000000000005'', id, NOW()
        FROM %I.permissions
        WHERE code IN (''property:read'', ''room:read'', ''room:update'',
                       ''housekeeping:read'', ''housekeeping:create'', ''housekeeping:update'', ''housekeeping:delete'',
                       ''report:read'')
        ON CONFLICT (role_id, permission_id) DO NOTHING', p_schema_name, p_schema_name);

    -- Seed role_permissions for HOUSEKEEPER
    EXECUTE format('
        INSERT INTO %I.role_permissions (id, role_id, permission_id, created_at)
        SELECT uuidv7(), ''10000000-0000-0000-0000-000000000006'', id, NOW()
        FROM %I.permissions
        WHERE code IN (''room:read'', ''housekeeping:read'', ''housekeeping:update'')
        ON CONFLICT (role_id, permission_id) DO NOTHING', p_schema_name, p_schema_name);

END;
$$;

-- changeset identity:009-add-function-comment
COMMENT ON FUNCTION public.create_tenant_schema(VARCHAR) IS 'Creates a complete tenant schema with all tables and seeds default roles/permissions';
