#!/bin/bash
java -cp . ParallelStreamBenchmark -size 10000000 -nthreads 40 -ntimes 10
