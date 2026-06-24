#!/bin/bash
java -cp . ParallelStreamBenchmark -size 10000000 -nthreads 15 -ntimes 10
