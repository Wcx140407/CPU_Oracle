#!/bin/bash
java -cp . ParallelSHA256 -size 10485760 -nthreads 4 -nreps 10
