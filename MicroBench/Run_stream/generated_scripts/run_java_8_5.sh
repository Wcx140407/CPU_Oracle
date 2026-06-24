#!/bin/bash
java -cp . ParallelStreamBenchmark -size 50000000 -nthreads 8 -ntimes 10
