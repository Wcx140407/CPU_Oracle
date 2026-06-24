#!/bin/bash
java -cp . ParallelStreamBenchmark -size 10000000 -nthreads 48 -ntimes 10
