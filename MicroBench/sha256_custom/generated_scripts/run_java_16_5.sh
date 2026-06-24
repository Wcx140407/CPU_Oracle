#!/bin/bash
java -cp . CustomSHA256Parallel -size 52428800 -nthreads 16 -nreps 10
