#!/bin/bash
java -cp . ParallelStreamBenchmark -size 50000000 -nthreads 64 -ntimes 10
