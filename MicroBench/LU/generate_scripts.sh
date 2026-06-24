#!/bin/bash

mkdir -p generated_scripts

for compiler in gcc clang; do
    for O in O1 O2 O3; do
        for thread in {1..64}; do
            for dataset in {1..10}; do
                script_name="generated_scripts/run_${compiler}_${thread}_${dataset}_${O}.sh"
                
                # 构建命令的第一部分
                echo "#!/bin/bash" > "$script_name"
                echo "./LU_${compiler}_${O} --datasets=${dataset} --threads=${thread} \\" >> "$script_name"
                
                # 添加size参数
                for ((d=0; d<dataset; d++)); do
                    size=$((500 + d * 200))
                    if [ $d -eq $((dataset-1)) ]; then
                        echo "    --size${d}=${size}" >> "$script_name"
                    else
                        echo "    --size${d}=${size} \\" >> "$script_name"
                    fi
                done
                
                chmod +x "$script_name"
            done
        done
    done
done

echo "生成完成！共生成 3840 个脚本文件"
