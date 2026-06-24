#!/bin/bash
java -cp . ParallelStreamBenchmark -size 100000000 -nthreads 5 -ntimes 10
