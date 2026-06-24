#!/bin/bash
java -cp . ParallelStreamBenchmark -size 50000000 -nthreads 33 -ntimes 10
