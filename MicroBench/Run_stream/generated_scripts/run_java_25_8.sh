#!/bin/bash
java -cp . ParallelStreamBenchmark -size 80000000 -nthreads 25 -ntimes 10
