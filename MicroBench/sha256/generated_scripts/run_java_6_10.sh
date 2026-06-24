#!/bin/bash
java -cp . ParallelSHA256 -size 104857600 -nthreads 6 -nreps 10
