#!/bin/bash
java -cp . ParallelSHA256 -size 20971520 -nthreads 24 -nreps 10
