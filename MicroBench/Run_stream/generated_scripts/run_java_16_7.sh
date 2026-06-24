#!/bin/bash
java -cp . ParallelStreamBenchmark -size 70000000 -nthreads 16 -ntimes 10
