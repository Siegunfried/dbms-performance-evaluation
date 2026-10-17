-- ============================================================
-- 放大数据集的建表 + 导入语句(性能实验用,去掉约束以加快导入)
-- 列顺序与 TSV 文件完全一致。
--
-- movies.tsv 列:movieid, title, country, year_released, runtime
-- people.tsv 列:peopleid, first_name, surname, born, died, gender
--
-- 导入(psql 客户端里执行 \copy,避免服务器端文件路径问题):
--   \copy movies FROM '/path/to/movies.tsv';
--   \copy people FROM '/path/to/people.tsv';
-- (COPY 默认就是 tab 分隔,所以无需 FORMAT/HEADER 选项)
-- ============================================================

DROP TABLE IF EXISTS movies;
CREATE TABLE movies (
    movieid       integer PRIMARY KEY,
    title         varchar(300),
    country       char(2),
    year_released int,
    runtime       int
);

DROP TABLE IF EXISTS people;
CREATE TABLE people (
    peopleid   integer PRIMARY KEY,
    first_name varchar(50),
    surname    varchar(50),
    born       int,
    died       int,
    gender     char(1)
);
