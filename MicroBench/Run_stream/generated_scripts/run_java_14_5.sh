#!/bin/bash
java -cp . ParallelStreamBenchmark -size 50000000 -nthreads 14 -ntimes 10
