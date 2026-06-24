#!/bin/bash
java -cp . ParallelStreamBenchmark -size 20000000 -nthreads 48 -ntimes 10
