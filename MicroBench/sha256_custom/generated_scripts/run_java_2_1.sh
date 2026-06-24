#!/bin/bash
java -cp . CustomSHA256Parallel -size 10485760 -nthreads 2 -nreps 10
