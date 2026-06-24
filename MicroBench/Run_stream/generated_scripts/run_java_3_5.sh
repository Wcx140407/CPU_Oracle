#!/bin/bash
java -cp . ParallelStreamBenchmark -size 50000000 -nthreads 3 -ntimes 10
