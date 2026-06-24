#!/bin/bash
java -cp . ParallelStreamBenchmark -size 90000000 -nthreads 12 -ntimes 10
