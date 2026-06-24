#!/bin/bash
java -cp . ParallelStreamBenchmark -size 90000000 -nthreads 4 -ntimes 10
