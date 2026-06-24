#!/bin/bash
java -cp . ParallelStreamBenchmark -size 80000000 -nthreads 16 -ntimes 10
