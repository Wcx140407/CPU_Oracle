#!/bin/bash
java -cp . CustomSHA256Parallel -size 10485760 -nthreads 60 -nreps 10
