#!/bin/bash
java -cp . ParallelStreamBenchmark -size 100000000 -nthreads 3 -ntimes 10
