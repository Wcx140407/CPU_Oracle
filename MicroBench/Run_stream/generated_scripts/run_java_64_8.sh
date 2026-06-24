#!/bin/bash
java -cp . ParallelStreamBenchmark -size 80000000 -nthreads 64 -ntimes 10
