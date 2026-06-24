#!/bin/bash
java -cp . ParallelStreamBenchmark -size 30000000 -nthreads 6 -ntimes 10
