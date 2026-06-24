#!/bin/bash
java -cp . ParallelStreamBenchmark -size 90000000 -nthreads 3 -ntimes 10
