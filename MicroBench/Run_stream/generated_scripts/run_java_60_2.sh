#!/bin/bash
java -cp . ParallelStreamBenchmark -size 20000000 -nthreads 60 -ntimes 10
