#!/bin/bash
java -cp . ParallelStreamBenchmark -size 80000000 -nthreads 24 -ntimes 10
