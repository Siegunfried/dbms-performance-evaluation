-- 初始用户(opengauss)禁止远程连接,需另建可远程连接的用户 gaussdb
CREATE USER gaussdb WITH PASSWORD 'OpenGauss@2026';
ALTER TABLE movies OWNER TO gaussdb;
ALTER TABLE people OWNER TO gaussdb;
