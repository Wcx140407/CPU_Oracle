#!/bin/bash
java -cp . ParallelStreamBenchmark -size 80000000 -nthreads 9 -ntimes 10
