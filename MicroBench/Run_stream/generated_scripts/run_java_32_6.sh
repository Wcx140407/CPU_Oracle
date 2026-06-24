#!/bin/bash
java -cp . ParallelStreamBenchmark -size 60000000 -nthreads 32 -ntimes 10
