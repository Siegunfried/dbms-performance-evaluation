#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
数据放大生成器 —— 为 DBMS 性能对比实验生成足够大的数据集。

种子来自 Lab/filmdb.sql(电影数据库),用 Python 内置 sqlite3 解析,
再程序化放大,输出 TSV(制表符分隔,无表头):
  - movies.tsv  (默认 100 万行)
  - people.tsv  (默认 50 万行)
  - schema.sql  (建表 + COPY 导入命令 + 列顺序说明)

设计要点:
  1. 只放大、不造假:标题/人名来自真实种子,追加数字后缀保证唯一;
     后缀不含字母,不污染"把 T 换成 Ttt"的更新实验。
  2. 检索选择性随规模等比缩放:搜索词命中某标题时,会命中它的所有副本,
     结果占比稳定,便于画"时间 vs 规模"曲线。
  3. 用 TSV 而非 CSV:PostgreSQL/openGauss 的 COPY 默认就是 tab 分隔,
     且片名里常见逗号(如 "Ali Zaoua, prince de la rue"),tab 几乎不会出现,
     Java 端 split("\\t") 也最省事。
"""
import sqlite3
import os

SEED_SQL = r"C:\Users\董佳鑫\Desktop\数据库原理\Lab\filmdb.sql"
OUT_DIR = os.path.dirname(os.path.abspath(__file__))

TARGET_MOVIES = 1_000_000
TARGET_PEOPLE = 500_000


def _sanitize(v):
    """把可能破坏 TSV 的 tab/换行/回车换成空格。"""
    return str(v).replace("\t", " ").replace("\n", " ").replace("\r", " ")


def load_seeds():
    """从 filmdb.sql 提取电影与人物种子(截掉 alt_titles 的 E'' 转义段)。"""
    with open(SEED_SQL, encoding="utf-8") as f:
        text = f.read()
    # alt_titles 表里有 PostgreSQL 的 E'' 转义字符串,SQLite 不认,直接截断不用
    text = text.split("CREATE TABLE alt_titles")[0] + "\nCOMMIT;\n"
    conn = sqlite3.connect(":memory:")
    conn.executescript(text)
    movies = conn.execute(
        "SELECT title, country, year_released, runtime FROM movies ORDER BY movieid"
    ).fetchall()
    people = conn.execute(
        "SELECT first_name, surname, born, died, gender FROM people ORDER BY peopleid"
    ).fetchall()
    conn.close()
    return movies, people


def gen_movies(seeds, n):
    m = len(seeds)
    for i in range(n):
        title, country, yr, rt = seeds[i % m]
        variant = i // m
        t = title if variant == 0 else f"{title} [{variant}]"
        yield [i + 1, _sanitize(t), country, yr, "\\N" if rt is None else rt]


def gen_people(seeds, n):
    m = len(seeds)
    for i in range(n):
        fn, sn, born, died, gender = seeds[i % m]
        variant = i // m
        s = sn if variant == 0 else f"{sn}_{variant}"
        yield [i + 1, "\\N" if fn is None else _sanitize(fn), _sanitize(s),
               "\\N" if born is None else born, "\\N" if died is None else died,
               "" if gender is None else gender]


def write_tsv(name, rows, total):
    path = os.path.join(OUT_DIR, name)
    with open(path, "w", encoding="utf-8", newline="") as f:
        for k, row in enumerate(rows, 1):
            f.write("\t".join(str(x) for x in row) + "\n")
            if k % 200_000 == 0:
                print(f"  {name}: {k:,}/{total:,}")
    print(f"  {name}: 完成 {total:,} 行,{os.path.getsize(path)/1e6:.1f} MB")


def write_schema():
    sql = """-- ============================================================
-- 放大数据集的建表 + 导入语句(性能实验用,去掉约束以加快导入)
-- 列顺序与 TSV 文件完全一致。
--
-- movies.tsv 列:movieid, title, country, year_released, runtime
-- people.tsv 列:peopleid, first_name, surname, born, died, gender
--
-- 导入(psql 客户端里执行 \\copy,避免服务器端文件路径问题):
--   \\copy movies FROM '/path/to/movies.tsv';
--   \\copy people FROM '/path/to/people.tsv';
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
"""
    with open(os.path.join(OUT_DIR, "schema.sql"), "w", encoding="utf-8") as f:
        f.write(sql)


def main():
    print("加载种子数据 ...")
    movies_seed, people_seed = load_seeds()
    print(f"种子:movies={len(movies_seed)}  people={len(people_seed)}")

    print(f"生成 movies.tsv ({TARGET_MOVIES:,} 行) ...")
    write_tsv("movies.tsv", gen_movies(movies_seed, TARGET_MOVIES), TARGET_MOVIES)

    print(f"生成 people.tsv ({TARGET_PEOPLE:,} 行) ...")
    write_tsv("people.tsv", gen_people(people_seed, TARGET_PEOPLE), TARGET_PEOPLE)

    write_schema()
    print("schema.sql 已生成。")
    print("完成。")


if __name__ == "__main__":
    main()
