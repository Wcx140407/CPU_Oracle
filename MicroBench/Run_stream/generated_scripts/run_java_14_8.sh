#!/bin/bash
java -cp . ParallelStreamBenchmark -size 80000000 -nthreads 14 -ntimes 10
