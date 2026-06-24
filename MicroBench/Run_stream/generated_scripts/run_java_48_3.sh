#!/bin/bash
java -cp . ParallelStreamBenchmark -size 30000000 -nthreads 48 -ntimes 10
