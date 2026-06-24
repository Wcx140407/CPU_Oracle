#!/bin/bash
java -cp . ParallelStreamBenchmark -size 80000000 -nthreads 30 -ntimes 10
