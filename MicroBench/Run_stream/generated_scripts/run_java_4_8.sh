#!/bin/bash
java -cp . ParallelStreamBenchmark -size 80000000 -nthreads 4 -ntimes 10
