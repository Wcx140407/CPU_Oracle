#!/bin/bash
java -cp . ParallelStreamBenchmark -size 100000000 -nthreads 8 -ntimes 10
