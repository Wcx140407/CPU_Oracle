#!/bin/bash

#mkdir -p generated_scripts

for thread in {1..64}; do
    for dataset in {1..10}; do
        # 计算m_size (10MB * dataset)
        m_size=$(( 10485760 * dataset ))
        
        cat > "generated_scripts/run_java_${thread}_${dataset}.sh" << EOF
#!/bin/bash
java -cp . CustomSHA256Parallel -size ${m_size} -nthreads ${thread} -nreps 10
EOF
        chmod +x "generated_scripts/run_java_${thread}_${dataset}.sh"
    done
done

echo "生成完成！共生成 640 个脚本文件"
