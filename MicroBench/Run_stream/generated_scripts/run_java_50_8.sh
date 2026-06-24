#!/bin/bash
java -cp . ParallelStreamBenchmark -size 80000000 -nthreads 50 -ntimes 10
