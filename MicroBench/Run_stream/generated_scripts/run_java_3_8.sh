#!/bin/bash
java -cp . ParallelStreamBenchmark -size 80000000 -nthreads 3 -ntimes 10
