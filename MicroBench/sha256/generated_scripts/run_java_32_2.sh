#!/bin/bash
java -cp . ParallelSHA256 -size 20971520 -nthreads 32 -nreps 10
