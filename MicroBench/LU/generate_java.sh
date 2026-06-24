#!/bin/bash

# 生成所有脚本
for thread in {1..64}; do
    for dataset in {1..10}; do
        # 生成脚本文件名
        script_name="run_java_${thread}_${dataset}.sh"
        
        # 开始构建脚本内容
        echo "#!/bin/bash" > "$script_name"
        echo "java -cp . ParallelLU --datasets=${dataset} --threads=${thread} \\" >> "$script_name"
        
        # 添加size参数
        for ((d=0; d<dataset; d++)); do
            size=$((500 + d * 200))
            if [ $d -eq $((dataset-1)) ]; then
                echo "    --size${d}=${size}" >> "$script_name"
            else
                echo "    --size${d}=${size} \\" >> "$script_name"
            fi
        done
        
        # 给脚本添加执行权限
        chmod +x "$script_name"
        
        #echo "已生成: $script_name"
    done
done

echo "生成完成！共生成 640 个脚本文件"
