#!/bin/bash
java -cp . ParallelStreamBenchmark -size 80000000 -nthreads 8 -ntimes 10
