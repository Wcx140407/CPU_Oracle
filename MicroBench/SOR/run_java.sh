#!/bin/bash
#./SOR_gcc <size> <thread> <min_measure_time> <random_seed>
java ConcurrentSOR -s 1000 -t 32 -m 5 -r 2024
