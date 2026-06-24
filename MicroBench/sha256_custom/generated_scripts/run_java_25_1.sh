#!/bin/bash
java -cp . CustomSHA256Parallel -size 10485760 -nthreads 25 -nreps 10
