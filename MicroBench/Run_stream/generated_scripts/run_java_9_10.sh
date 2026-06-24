#!/bin/bash
java -cp . ParallelStreamBenchmark -size 100000000 -nthreads 9 -ntimes 10
