#!/bin/bash
root_dir=`pwd`
# source ${root_dir}/../env.sh
bench_lang=cpp
bench_type=perf

#if [ -d $log_dir ]; then
#    echo -e "WARN: $log_dir will removed after 5 seconds *******"
#    sleep 5
#fi
#rm -rf $log_dir && mkdir -p $log_dir/   
#set -x
log_dir=$root_dir/../results/d1
mkdir -p ${log_dir}
cd $root_dir
#cp speed_bwaves $log_dir
time perf stat -r 3 \
    -e 'inst_retired, br_retired, br_mis_pred_retired, l1i_cache_refill, l1i_cache, l2d_cache_refill, ll_cache_miss' \
    -e 'r75, r74, r06, r07' \
    -e 'r70,r71,r21,r73,r8' \
    -e 'r4,r14,r16,r37,r25,r26' \
    -e 'L1-dcache-load-misses,L1-dcache-loads,L1-dcache-misses,L1-dcache' \
    -e 'LLC' \
    -e 'dTLB-load-misses,dTLB-loads,dTLB,dTLB-misses' \
    -e 'iTLB-load-misses,iTLB-loads,iTLB,iTLB-misses' \
    -e 'branch-load-misses,branch-load,branch-misses,branch-instructions' \
    -e 'instructions,cache-misses,cycles' \
    -o $log_dir/clang-O2-7-perf.out \
    ./run_clang_7_1_O2.sh 

