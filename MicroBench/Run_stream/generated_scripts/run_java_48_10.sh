#!/bin/bash
java -cp . ParallelStreamBenchmark -size 100000000 -nthreads 48 -ntimes 10
