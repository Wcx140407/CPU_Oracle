#!/bin/bash
java -cp . ParallelStreamBenchmark -size 10000000 -nthreads 64 -ntimes 10
