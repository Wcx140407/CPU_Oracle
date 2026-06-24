#!/bin/bash
java -cp . ParallelStreamBenchmark -size 40000000 -nthreads 9 -ntimes 10
