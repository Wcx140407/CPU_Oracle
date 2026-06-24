#!/bin/bash
java -cp . ParallelStreamBenchmark -size 10000000 -nthreads 24 -ntimes 10
