#!/bin/bash
java -cp . ParallelStreamBenchmark -size 80000000 -nthreads 2 -ntimes 10
