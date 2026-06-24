#!/bin/bash
java -cp . ParallelStreamBenchmark -size 90000000 -nthreads 16 -ntimes 10
