#!/bin/bash
java -cp . ParallelStreamBenchmark -size 90000000 -nthreads 40 -ntimes 10
