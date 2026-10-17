# 参考文献

> 报告里**真正值得列入参考文献的约 6~8 篇**,其余作为理解概念的背景。
> 引用原则:每篇引一句即可,不要大段摘抄(题目规则第 3 条反对注水)。

## 必引(6~8 篇)

### 1. 为什么 DBMS 不直接用操作系统文件(Q1 理论根基)
- Stonebraker, M. "Operating System Support for Database Management." *CACM* 24(7): 412–418, 1981. DOI 10.1145/358699.358703.
  - **用在**:报告引言 / Q1 开篇。论证 OS 的缓冲池、文件系统、调度等服务对 DBMS「太慢或不合适」。
  - PDF: https://www.cs.cmu.edu/~natassa/courses/15-823/S01/lectures/412_OSSupport.pdf

### 2. 索引与存储结构
- Bayer, R., McCreight, E. "Organization and Maintenance of Large Ordered Indexes." *Acta Informatica* 1(3): 173–189, 1972.
  - **用在**:「索引实验」小节。B-tree 原始论文。
- (可选) Comer, D. "The Ubiquitous B-Tree." *ACM Computing Surveys* 11(2): 121–137, 1979.
  - B+-tree 为何成为关系数据库事实标准。

### 3. 崩溃恢复(WAL)
- Mohan, C., Haderle, D., Lindsay, B., Pirahesh, H., Schwarz, P. "ARIES: A Transaction Recovery Method Supporting Fine-Granularity Locking and Partial Rollbacks Using Write-Ahead Logging." *TODS* 17(1): 94–162, 1992. DOI 10.1145/128765.128770.
  - **用在**:「崩溃恢复演示」+ WAL 讨论。

### 4. 并发(MVCC)
- Bernstein, P. A., Goodman, N. "Multiversion Concurrency Control—Theory and Algorithms." *TODS* 8(4): 465–483, 1983. DOI 10.1145/319996.319998.
  - **用在**:「并发演示」+ PostgreSQL 用 MVCC 的说明。

### 5. openGauss(Q2)
- Li, G., et al. "openGauss: An Autonomous Database System." *PVLDB* 14(12): 3028–3042, 2021.
  - **用在**:Q2 章节,openGauss 官方技术画像(自治优化、索引推荐,实验用 TPC-H/TPC-C)。
  - PDF: http://www.vldb.org/pvldb/vol14/p3028-li.pdf

### 6. 技术依据(非论文,但需引用)
- PostgreSQL 文档 `pg_trgm`(GIN 三元组索引)——支撑「普通 B-tree 帮不上 `%XXX%`、要 GIN 才生效」的分析。
  - https://postgrespro.com/docs/postgresql/9.5/pgtrgm.html
- TPC 基准(TPC-C / TPC-H / TPC-DS)——「按什么标准比」的标准本身。 https://www.tpc.org/

## 可选加分(背景)

- Gray, J., Putzolu, F. "The 5 Minute Rule for Trading Memory for Disc Accesses and the 10 Byte Rule for Trading Memory for CPU Time." *SIGMOD* 1987.
  - 内存/磁盘权衡,支撑缓存讨论。1997 续作 PDF: http://arxiv.org/pdf/cs/9809005
- O'Neil, P., Cheng, E., Gawlick, D., O'Neil, E. "The Log-Structured Merge-Tree (LSM-tree)." *Acta Informatica* 33(4): 351–385, 1996. DOI 10.1007/s002360050048.
  - 高写入负载索引结构,支撑「更新成本」的 B-tree vs LSM 取舍。
- 《Readings in Database Systems》(Red Book):数据库精选论文集,背景/相关工作素材来源。 http://www.redbook.io/
- openGauss MOT 文档(内存优化引擎)。 https://docs.opengauss.org/zh/docs/6.0.0/docs/DatabaseAdministrationGuide/MOT%E4%BD%8E%E5%BB%B6%E8%BF%9F.html

## BibTeX(可直接粘进报告)

```bibtex
@article{stonebraker1981os,
  author  = {Stonebraker, Michael},
  title   = {Operating System Support for Database Management},
  journal = {Communications of the ACM},
  volume  = {24}, number = {7}, pages = {412--418}, year = {1981},
  doi     = {10.1145/358699.358703}
}

@article{bayer1972btree,
  author  = {Bayer, Rudolf and McCreight, Edward},
  title   = {Organization and Maintenance of Large Ordered Indexes},
  journal = {Acta Informatica},
  volume  = {1}, number = {3}, pages = {173--189}, year = {1972}
}

@article{comer1979btree,
  author  = {Comer, Douglas},
  title   = {The Ubiquitous B-Tree},
  journal = {ACM Computing Surveys},
  volume  = {11}, number = {2}, pages = {121--137}, year = {1979}
}

@article{mohan1992aries,
  author  = {Mohan, C. and Haderle, Donald and Lindsay, Bruce and Pirahesh, Hamid and Schwarz, Peter},
  title   = {ARIES: A Transaction Recovery Method Supporting Fine-Granularity
             Locking and Partial Rollbacks Using Write-Ahead Logging},
  journal = {ACM Transactions on Database Systems},
  volume  = {17}, number = {1}, pages = {94--162}, year = {1992},
  doi     = {10.1145/128765.128770}
}

@article{bernstein1983mvcc,
  author  = {Bernstein, Philip A. and Goodman, Nathan},
  title   = {Multiversion Concurrency Control---Theory and Algorithms},
  journal = {ACM Transactions on Database Systems},
  volume  = {8}, number = {4}, pages = {465--483}, year = {1983},
  doi     = {10.1145/319996.319998}
}

@article{li2021opengauss,
  author  = {Li, Guoliang and others},
  title   = {openGauss: An Autonomous Database System},
  journal = {Proceedings of the VLDB Endowment},
  volume  = {14}, number = {12}, pages = {3028--3042}, year = {2021}
}

@article{gray1987fivemin,
  author  = {Gray, Jim and Putzolu, Franco},
  title   = {The 5 Minute Rule for Trading Memory for Disc Accesses and the
             10 Byte Rule for Trading Memory for CPU Time},
  journal = {Proceedings of ACM SIGMOD},
  year    = {1987}
}

@article{oneil1996lsm,
  author  = {O'Neil, Patrick and Cheng, Edward and Gawlick, Dieter and O'Neil, Elizabeth},
  title   = {The Log-Structured Merge-Tree (LSM-Tree)},
  journal = {Acta Informatica},
  volume  = {33}, number = {4}, pages = {351--385}, year = {1996},
  doi     = {10.1007/s002360050048}
}
```
