#!/bin/bash
java -cp . ParallelStreamBenchmark -size 100000000 -nthreads 2 -ntimes 10
