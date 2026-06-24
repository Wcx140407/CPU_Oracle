#!/bin/bash
java -cp . ParallelSHA256 -size 52428800 -nthreads 8 -nreps 10
