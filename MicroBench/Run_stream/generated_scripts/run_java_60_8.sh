#!/bin/bash
java -cp . ParallelStreamBenchmark -size 80000000 -nthreads 60 -ntimes 10
