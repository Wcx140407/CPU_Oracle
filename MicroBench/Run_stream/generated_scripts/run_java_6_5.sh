#!/bin/bash
java -cp . ParallelStreamBenchmark -size 50000000 -nthreads 6 -ntimes 10
