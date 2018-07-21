Separate Threads Benchmark
==========================


There are two ways to run the separate threads benchmark. The first
way is intended to produce graphs where the number of threads is fixed
and size of the range queries varies. The second way is interned to
produce graphs where the size of range queries is fixed and the number
of threads varies. See [Vary Range Query Size](#vary-range-query-size)
and [Vary Thread Count](#vary-thread-count) below for information
about these two different ways of running the benchmark.


Vary Range Query Size
---------------------

The benchmark can be configured by modifying:

`benchmark_tools/bench_separate_update_and_range_queries_vary_range_size.py`

The data structures that should be benchmarked can be configured by
modifying:

`benchmark_tools/all_ds_bench_separate_update_and_range_queries_vary_range_size.sh`

### Running the Benchmark

```
cd benchmark_tools
./all_ds_bench_separate_update_and_range_queries_vary_range_size.sh 1 1000000
```

The fist parameter is a suffix that will be appended to the names of
the output files. The second parameter is the size of the item range.

### Producing Graphs From the Result Files

```
cd benchmark_tools
python3 plot_sep_threads_vary_range_size.py
```

Vary Thread Count
-----------------

The benchmark can be configured by modifying:

`benchmark_tools/bench_separate_update_and_range_queries_vary_thread_count.py`

The data structures that should be benchmarked can be configured by
modifying:

`benchmark_tools/all_ds_bench_separate_update_and_range_queries_vary_thread_count.sh`

### Running the Benchmark

```
cd benchmark_tools
./all_ds_bench_separate_update_and_range_queries_vary_thread_count.sh 1 1000000
```

The fist parameter is a suffix that will be appended to the names of
the output files. The second parameter is the size of the item range.

### Producing Graphs From the Result Files

```
cd benchmark_tools
python3 plot_sep_threads_vary_thread_count.py
```

