#!/bin/bash
java -cp . ParallelStreamBenchmark -size 20000000 -nthreads 40 -ntimes 10
