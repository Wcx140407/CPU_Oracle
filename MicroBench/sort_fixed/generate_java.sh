#!/bin/bash

#mkdir -p generated_scripts

for thread in {1..64}; do
    for dataset in {1..10}; do
        cat > "generated_scripts/run_java_${thread}_${dataset}.sh" << EOF
#!/bin/bash
java -cp . ParallelMergeSortFixed -i input_data${dataset}.in -t ${thread}
EOF
        chmod +x "generated_scripts/run_java_${thread}_${dataset}.sh"
    done
done

echo "生成完成！共生成 640 个脚本文件"
