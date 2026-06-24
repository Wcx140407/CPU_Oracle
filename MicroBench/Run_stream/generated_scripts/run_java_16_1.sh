#!/bin/bash
java -cp . ParallelStreamBenchmark -size 10000000 -nthreads 16 -ntimes 10
