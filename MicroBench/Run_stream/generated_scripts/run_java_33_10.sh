#!/bin/bash
java -cp . ParallelStreamBenchmark -size 100000000 -nthreads 33 -ntimes 10
