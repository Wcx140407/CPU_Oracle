#!/bin/bash
java -cp . ParallelStreamBenchmark -size 40000000 -nthreads 32 -ntimes 10
