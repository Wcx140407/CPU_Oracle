#!/bin/bash
java -cp . ParallelSHA256 -size 52428800 -nthreads 48 -nreps 10
