#!/bin/bash

# 循环生成所有脚本
for thread in {1..64}; do
    for dataset in {1..10}; do
        # 计算p参数
        p=$((thread / dataset))
        if [ $p -lt 1 ]; then
            p=1
        fi
        
        # 生成脚本文件
        cat > "run_java_${thread}_${dataset}.sh" << EOF
#!/bin/bash
java -cp . FIOBenchmark -d dataset${dataset}.csv -p ${p} -t ${thread} -f 5
EOF
        chmod +x "run_java_${thread}_${dataset}.sh"
    done
done

echo "生成完成！共生成 640 个脚本文件"
