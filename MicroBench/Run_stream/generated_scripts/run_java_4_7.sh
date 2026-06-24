#!/bin/bash
java -cp . ParallelStreamBenchmark -size 70000000 -nthreads 4 -ntimes 10
