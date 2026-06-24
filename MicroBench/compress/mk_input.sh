#!/bin/bash
for i in {1..10}; do
    dd if=/dev/urandom of=dataset/$i bs=1M count=1 status=none
    echo "创建文件: dataset/$i (1MB)"
done
