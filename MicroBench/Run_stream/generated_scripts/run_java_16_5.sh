#!/bin/bash
java -cp . ParallelStreamBenchmark -size 50000000 -nthreads 16 -ntimes 10
