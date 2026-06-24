#!/bin/bash
java -cp . ParallelStreamBenchmark -size 40000000 -nthreads 2 -ntimes 10
