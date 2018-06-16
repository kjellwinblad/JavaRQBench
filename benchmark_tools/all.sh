#!/bin/bash


SIZE=$2

DATASTRUCTURES=se.uu.collection.LockFreeImmTreapCATreeMapSTDR
#"se.uu.collection.KiWiRangeQueryMap se.uu.collection.ImmTreapCATreeMapSTDR se.uu.collection.FatCATreeMapSTDR se.uu.collection.CATreeMapSTDR se.uu.collection.NonAtomicRangeUpdateConcurrentSkipListMap se.uu.collection.RangeUpdateSnapTree algorithms.published.LockFreeKSTRQ se.uu.collection.LockFreeRangeCollectorSkipList se.uu.collection.ImmTreapCoarseMap se.uu.collection.LockFreeImmTreapCATreeMapSTDR"

for DATASTRUCTURE in $DATASTRUCTURES
do
    python3 bench_separate_update_and_range_queries.py $1 $DATASTRUCTURE $SIZE
done

for DATASTRUCTURE in $DATASTRUCTURES
do
    python3 bench.py $1 $DATASTRUCTURE $SIZE
done


for DATASTRUCTURE in $DATASTRUCTURES
do
    python3 bench_separate_update_and_range_queries_vary_range_size.py $1 $DATASTRUCTURE $SIZE
done

