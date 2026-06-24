#!/bin/bash
java -cp . ParallelStreamBenchmark -size 90000000 -nthreads 32 -ntimes 10
