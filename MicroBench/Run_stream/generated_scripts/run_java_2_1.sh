#!/bin/bash
java -cp . ParallelStreamBenchmark -size 10000000 -nthreads 2 -ntimes 10
