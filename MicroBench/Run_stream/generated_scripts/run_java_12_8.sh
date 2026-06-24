#!/bin/bash
java -cp . ParallelStreamBenchmark -size 80000000 -nthreads 12 -ntimes 10
