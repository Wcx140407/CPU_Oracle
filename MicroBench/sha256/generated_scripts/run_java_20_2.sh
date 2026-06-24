#!/bin/bash
java -cp . ParallelSHA256 -size 20971520 -nthreads 20 -nreps 10
