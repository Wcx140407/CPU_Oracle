#!/bin/bash
#./matmul_gcc <matrix size> <thread> <replicate> <block size>
java ParallelMatrixMultiplication.java 1024 16 3 64
