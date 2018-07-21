Configuration
=============

The benchmark can be configured by modifying:

`benchmark_tools/mix_ops_bench.py`

The data structures that should be benchmarked can be configured by
modifing:

`benchmark_tools/all_ds_mix_ops_bench.sh`

Running the Benchmark
=====================

    cd benchmark_tools
    ./all_ds_mix_ops_bench.sh 1 1000000

The fist parameter to `all_ds_mix_ops_bench.sh` is a suffix that will
be appended to the names of the output files.

Producing Graphs From the Result Files
======================================

   cd benchmark_tools
   python3 plot_mix_ops_bench.py