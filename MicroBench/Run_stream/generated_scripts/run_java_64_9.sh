#!/bin/bash
java -cp . ParallelStreamBenchmark -size 90000000 -nthreads 64 -ntimes 10
