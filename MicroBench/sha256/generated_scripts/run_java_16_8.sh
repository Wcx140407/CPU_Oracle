#!/bin/bash
java -cp . ParallelSHA256 -size 83886080 -nthreads 16 -nreps 10
