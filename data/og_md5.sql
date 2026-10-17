-- 把 openGauss 认证改成 md5,使标准 PostgreSQL JDBC 驱动也能连接
ALTER SYSTEM SET password_encryption_type = 0;
ALTER USER gaussdb PASSWORD 'OpenGauss@2026';
