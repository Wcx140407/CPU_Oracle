#!/bin/bash
java -cp . ParallelStreamBenchmark -size 50000000 -nthreads 30 -ntimes 10
