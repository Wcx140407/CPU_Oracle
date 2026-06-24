#!/bin/bash
java -cp . CustomSHA256Parallel -size 10485760 -nthreads 30 -nreps 10
