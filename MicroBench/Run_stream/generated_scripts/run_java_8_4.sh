#!/bin/bash
java -cp . ParallelStreamBenchmark -size 40000000 -nthreads 8 -ntimes 10
