JavaRQBench: Java Benchmarks for Concurrent Maps with Support for Range Queries
===============================================================================

This repository contains code for benchmarking and testing Java
implementations of concurrent maps with support for range queries and
state-of-the-art implementations of such maps.

Benchmarks
----------

*Work in progress*

A description of the available benchmarks will be added soon...

Included Data Structures
------------------------

*LFCA tee* - the lock-free contention adapting search tree

Publication:

Kjell Winblad, Konstantinos Sagonas, and Bengt Jonsson. Lock-free
contention adapting search trees. In Proceedings of the 30th ACM
Symposium on Parallelism in Algorithms and Architectures, SPAA ’18,
2018. To appear.

*Im-Tr-CA* - the contention adapting search tree with an optimization enabled by an immutable treap

Publication:

Kjell Winblad. Faster Concurrent Range Queries with Contention
Adapting Search Trees Using Immutable Data. In 2017 Imperial College
Computing Student Workshop (ICCSW 2017)

Code located in: src/main/java/se/uu/collection/ImmTreapCATreeMapSTDR.java

*AVL-CA* - the contention adapting search tree tree using an AVL tree as the sequential data structure

Publications:

Konstantinos Sagonas and Kjell Winblad. A contention adapting approach
to concurrent ordered sets. Journal of Parallel and Distributed
Computing, 2018.

Konstantinos Sagonas and Kjell Winblad. Contention adapting search
trees. In 14th International Symposium on Parallel and Distributed
Computing, ISPDC, 2015

Code located in: src/main/java/se/uu/collection/CATreeMapSTDR.java

*SL-CA* - the contention adapting search tree using a skip list with fat nodes as the sequential data structure

Publications:

Konstantinos Sagonas and Kjell Winblad. A contention adapting approach
to concurrent ordered sets. Journal of Parallel and Distributed
Computing, 2018.

Konstantinos Sagonas and Kjell Winblad. Efficient support for range
queries and range updates using contention adapting search trees. In
Languages and Compilers for Parallel Computing - 28th International
Workshop, LCPC, 2016.

Code located in: src/main/java/se/uu/collection/FatCATreeMapSTDR.java

*SnapTree* - A Practical Concurrent Binary Search Tree

Publication:

Nathan G. Bronson, Jared Casper, Hassan Chafi, and Kunle Olukotun. A
practical concurrent binary search tree. In Proceedings of the 15th ACM
SIGPLAN Symposium on Principles and Practice of Parallel Programming,
PPoPP ’10

Code located in: src/main/java/edu/stanford/ppl/concurrent/SnapTreeMap.java

*k-ary* - The lock-free k-ary search tree

Publications:

Trevor BrownJoanna Helga. Non-blocking k-ary Search Trees. In
Principles of Distributed Systems: 15th International Conference,
OPODIS 2011.

Trevor Brown and Hillel Avni. Range queries in non-blocking k-ary
search trees. In Principles of Distributed Systems: 16th International
Conference, OPODIS 2012.

Code located in: src/main/java/algorithms/published/LockFreeKSTRQ.java

License
-------

GNU General Public License (GPL) Version 3


