#!/bin/bash
java -cp . ParallelStreamBenchmark -size 90000000 -nthreads 8 -ntimes 10
