#!/bin/bash
java -cp . ParallelStreamBenchmark -size 100000000 -nthreads 6 -ntimes 10
