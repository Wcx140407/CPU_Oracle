#!/bin/bash
java -cp . ParallelStreamBenchmark -size 100000000 -nthreads 16 -ntimes 10
