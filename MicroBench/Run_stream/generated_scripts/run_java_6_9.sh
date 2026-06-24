#!/bin/bash
java -cp . ParallelStreamBenchmark -size 90000000 -nthreads 6 -ntimes 10
