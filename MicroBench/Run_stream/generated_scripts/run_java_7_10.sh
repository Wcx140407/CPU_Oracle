#!/bin/bash
java -cp . ParallelStreamBenchmark -size 100000000 -nthreads 7 -ntimes 10
