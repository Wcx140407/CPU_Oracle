#!/bin/bash
java -cp . ParallelStreamBenchmark -size 80000000 -nthreads 48 -ntimes 10
