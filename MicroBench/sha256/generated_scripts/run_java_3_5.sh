#!/bin/bash
java -cp . ParallelSHA256 -size 52428800 -nthreads 3 -nreps 10
