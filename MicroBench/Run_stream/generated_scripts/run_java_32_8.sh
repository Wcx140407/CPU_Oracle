#!/bin/bash
java -cp . ParallelStreamBenchmark -size 80000000 -nthreads 32 -ntimes 10
