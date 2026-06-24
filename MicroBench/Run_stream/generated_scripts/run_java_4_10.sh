#!/bin/bash
java -cp . ParallelStreamBenchmark -size 100000000 -nthreads 4 -ntimes 10
