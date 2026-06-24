#!/bin/bash

# 创建脚本目录
mkdir -p generated_scripts

# 循环生成所有组合的脚本
for compiler in gcc clang; do
    for O in O1 O2 O3; do
        for thread in {1..64}; do
            for dataset in {1..10}; do
                # 生成脚本文件
                cat > "generated_scripts/run_${compiler}_${thread}_${dataset}_${O}.sh" << EOF
#!/bin/bash
./aes_${compiler}_${O} 3 ${thread} aes_data${dataset}.csv con
EOF
                chmod +x "generated_scripts/run_${compiler}_${thread}_${dataset}_${O}.sh"
            done
        done
    done
done

echo "生成完成！共生成 3840 个脚本文件"
