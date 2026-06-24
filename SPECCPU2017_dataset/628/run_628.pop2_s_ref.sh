#!/bin/bash
export OMP_NUM_THREADS=56
#export OMP_THREAD_LIMIT=1
export OMP_STACKSIZE=1G
../run_base_refspeed_mytest-m64.0000/speed_pop2 > pop2_s.out 2>> pop2_s.err
