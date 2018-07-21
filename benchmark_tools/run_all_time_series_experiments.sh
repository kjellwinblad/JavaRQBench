#!/bin/bash


for I in 1 2 3 4 5
do
    python3 time_series_experiment.py $I se.uu.collection.LockFreeImmTreapCATreeMapSTDR 1000000 0 1000
    python3 time_series_experiment.py $I se.uu.collection.LockFreeImmTreapCATreeMapSTDR 1000000 1000 10
    python3 time_series_experiment.py $I se.uu.collection.LockFreeImmTreapCATreeMapSTDR 1000000 10 1000
    python3 time_series_experiment.py two$I se.uu.collection.LockFreeImmTreapCATreeMapSTDR 1000000 1000 10
    python3 time_series_experiment.py $I se.uu.collection.LockFreeImmTreapCATreeMapSTDR 1000000 10 100000
done
