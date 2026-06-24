#!/bin/bash
java -cp . ParallelStreamBenchmark -size 20000000 -nthreads 30 -ntimes 10
