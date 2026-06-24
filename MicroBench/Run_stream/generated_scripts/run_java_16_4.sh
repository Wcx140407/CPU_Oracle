#!/bin/bash
java -cp . ParallelStreamBenchmark -size 40000000 -nthreads 16 -ntimes 10
