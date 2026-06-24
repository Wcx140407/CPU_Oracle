#!/bin/bash

#mkdir -p generated_scripts

for thread in {1..64}; do
    for dataset in {1..10}; do
        # 计算matrix_size
        #n=$(( (dataset + 1) * 8 ))
        n=$(( (dataset + 31) * 1 ))
	size=$(( n * n ))
        
        cat > "generated_scripts/run_java_${thread}_${dataset}.sh" << EOF
#!/bin/bash
java -cp . ParallelMatrixMultiplication ${size} ${thread} 3 64
EOF
        chmod +x "generated_scripts/run_java_${thread}_${dataset}.sh"
    done
done

echo "生成完成！共生成 640 个脚本文件"
