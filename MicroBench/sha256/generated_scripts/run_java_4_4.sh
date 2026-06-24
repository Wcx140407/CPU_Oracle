#!/bin/bash
java -cp . ParallelSHA256 -size 41943040 -nthreads 4 -nreps 10
