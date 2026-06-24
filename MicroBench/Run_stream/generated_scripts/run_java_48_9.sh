#!/bin/bash
java -cp . ParallelStreamBenchmark -size 90000000 -nthreads 48 -ntimes 10
