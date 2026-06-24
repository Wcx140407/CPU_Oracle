#!/bin/bash
java -cp . ParallelSHA256 -size 20971520 -nthreads 50 -nreps 10
