#!/bin/bash
java -cp . CustomSHA256Parallel -size 52428800 -nthreads 8 -nreps 10
