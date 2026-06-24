#!/bin/bash
java -cp . ParallelStreamBenchmark -size 80000000 -nthreads 6 -ntimes 10
