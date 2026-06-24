#!/bin/bash
java -cp . ParallelStreamBenchmark -size 100000000 -nthreads 56 -ntimes 10
