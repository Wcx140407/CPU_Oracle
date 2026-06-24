#!/bin/bash
java -cp . ParallelStreamBenchmark -size 10000000 -nthreads 32 -ntimes 10
