# Project 1：DBMS 性能评测

## 核心问题

1. DBMS 相比「直接读写文件」有哪些文件操作给不了的独特优势？
2. PostgreSQL 与 openGauss 哪个更好，该按什么标准衡量？

## 实验方案

- **数据**：电影数据集程序化放大至 movies 100 万行、people 50 万行（IMDb 风格 schema）。
- **数据库**：PostgreSQL 16.15（端口 5432，库 dbperf）与 openGauss 5.0.0（端口 5433，库 postgres），均以 Docker 运行。
- **文件侧**：同一份数据存为 TSV 文本，由 Java 程序直接读写。
- **客户端**：Java 21 + JDBC，用同一个 PostgreSQL 驱动连接两个库，保证除数据库本身外变量一致。
- **计时方法**：预热 1 次 + 运行 5 次，取中位数（热缓存）。

## 目录结构

```
project1/
├── README.md              项目概览
├── report.pdf             PDF 报告
├── report.docx            Word 报告
├── ConcurrencyTest.java   并发吞吐独立测试
├── src/
│   ├── Evaluation.java    实验主程序(E1~E6b)
│   └── Verify.java        环境验证(两库连通 + 行数)
├── lib/
│   └── postgresql-42.7.4.jar  JDBC 驱动
├── data/
│   ├── movies.tsv         100 万行电影
│   ├── people.tsv         50 万行人物
│   ├── generate_data.py   数据放大脚本
│   ├── schema.sql         建表脚本
│   └── og_md5.sql / openGauss_user.sql  openGauss 初始化脚本
├── docs/
│   ├── project1.pdf       题目
│   ├── references.md      文献调研
│   └── papers/            论文原文
├── figures/               实验图表与运行截图(PNG)
└── bin/                   编译产物(自动生成)
```

## 构建与运行

依赖：JDK 21，两个容器数据库已启动并导入数据。运行时会在 `results/` 下生成输出与更新产物。

```bash
# 编译
javac -encoding UTF-8 -cp "lib/postgresql-42.7.4.jar" -d bin src/Evaluation.java

# 全量运行(一次跑完所有实验)
java -cp "bin;lib/postgresql-42.7.4.jar" Evaluation

# 单项实验
java -cp "bin;lib/postgresql-42.7.4.jar" Evaluation --env          # 环境信息
java -cp "bin;lib/postgresql-42.7.4.jar" Evaluation --e3           # E3 索引(含查询计划)
java -cp "bin;lib/postgresql-42.7.4.jar" Evaluation --e5           # E5 ACID 演示
java -cp "bin;lib/postgresql-42.7.4.jar" Evaluation --throughput   # E6b 高并发吞吐
java -cp "bin;lib/postgresql-42.7.4.jar" Evaluation --masking      # openGauss 动态脱敏(补充)
```

## 实验清单

| 编号  | 实验内容                         | 对应问题 |
| --- | ---------------------------- | ---- |
| E1  | 检索对比：文件线性扫描 vs 数据库           | Q1   |
| E2  | 更新对比：文件改写 vs 数据库             | Q1   |
| E3  | 索引对比：B-tree 为何对 `%word%` 失效  | Q1   |
| E3b | 手工倒排索引（聪明文件对照）               | Q1   |
| E4  | 规模缩放：耗时 vs 数据量               | Q1   |
| E5  | ACID / 完整性演示                 | Q1   |
| E6  | PostgreSQL vs openGauss（同负载） | Q2   |
| E6b | 高并发吞吐：线程池 vs 进程模型            | Q2   |

## 交付物

- `report.pdf`（报告）与 `src/Evaluation.java`（源码）分开提交、不压缩。

