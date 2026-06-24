#!/bin/bash
java -cp . ParallelStreamBenchmark -size 40000000 -nthreads 15 -ntimes 10
