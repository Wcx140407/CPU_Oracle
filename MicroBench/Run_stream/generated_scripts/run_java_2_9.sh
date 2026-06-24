#!/bin/bash
java -cp . ParallelStreamBenchmark -size 90000000 -nthreads 2 -ntimes 10
