#!/bin/bash
java -cp . ParallelStreamBenchmark -size 60000000 -nthreads 64 -ntimes 10
