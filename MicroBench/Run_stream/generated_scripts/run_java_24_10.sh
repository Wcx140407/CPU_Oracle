#!/bin/bash
java -cp . ParallelStreamBenchmark -size 100000000 -nthreads 24 -ntimes 10
