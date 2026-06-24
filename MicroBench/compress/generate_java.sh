#!/bin/bash

# 循环生成所有脚本
for thread in {1..64}; do
    for dataset in {1..10}; do
        # 生成脚本文件
        cat > "run_java_${thread}_${dataset}.sh" << EOF
#!/bin/bash
java -cp . Compress ${thread} dataset${dataset}.conf 3
rm dataset/*.Z
EOF
        chmod +x "run_java_${thread}_${dataset}.sh"
    done
done

echo "生成完成！共生成 640 个脚本文件"
