#!/bin/bash
java -cp . ParallelStreamBenchmark -size 100000000 -nthreads 40 -ntimes 10
