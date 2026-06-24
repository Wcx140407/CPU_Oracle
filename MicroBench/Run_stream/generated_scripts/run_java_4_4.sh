#!/bin/bash
java -cp . ParallelStreamBenchmark -size 40000000 -nthreads 4 -ntimes 10
