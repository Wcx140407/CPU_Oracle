#!/bin/bash
java -cp . ParallelSHA256 -size 104857600 -nthreads 32 -nreps 10
