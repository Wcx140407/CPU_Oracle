#!/bin/bash

# 创建脚本目录
mkdir -p generated_scripts

# 生成所有脚本
for compiler in gcc clang; do
    for O in O1 O2 O3; do
        for thread in {1..64}; do
            for dataset in {1..10}; do
                # 生成脚本文件名
                script_name="generated_scripts/run_${compiler}_${thread}_${dataset}_${O}.sh"
                
                # 计算per-dataset参数
                per_dataset=$(( thread / dataset ))
                if [ $per_dataset -lt 1 ]; then
                    per_dataset=1
                fi
                
                # 开始构建脚本内容
                script_content="#!/bin/bash\n"
                script_content+="./fio_${compiler}_${O} --datasets=${dataset} --threads=${thread} --per-dataset=${per_dataset} \\\n"
                
                # 根据dataset的值生成不同的读写参数
                # dataset为1-10，需要生成对应数量的参数块
                file_counter=1
                operation_counter=0
                base_filesize=100
                
                for ((d=1; d<=dataset; d++)); do
                    # 确定读写模式：奇数为写入(w)，偶数为读取(r)
                    if [ $((d % 2)) -eq 1 ]; then
                        rw_mode="w"
                        # 对于写入操作，需要指定文件名
                        filename="file${file_counter}.dat"
                        filesize="${base_filesize}M"
                        file_counter=$((file_counter + 1))
                        #base_filesize=$((base_filesize + 100))
                    else
                        rw_mode="r"
                        # 对于读取操作，读取上一次写入的文件
                        filename="file$((file_counter-1)).dat"
                        filesize="${base_filesize}M"
			base_filesize=$((base_filesize + 100))
                    fi
                    
                    # 确定模式：写入为seq，读取为seq；但题目要求pattern按seq和rand循环
                    # 根据操作次数确定pattern
                    if [ $((operation_counter % 2)) -eq 0 ]; then
                        pattern="seq"
                    else
                        pattern="rand"
                    fi
                    
                    # 添加参数块
                    script_content+="    --name${operation_counter}=test$((operation_counter+1)) --filename${operation_counter}=${filename} --directory${operation_counter}=/tmp \\\n"
                    script_content+="    --filesize${operation_counter}=${filesize} --rw${operation_counter}=${rw_mode} --pattern${operation_counter}=${pattern} --runtime${operation_counter}=5"
                    
                    # 如果不是最后一个参数块，添加换行和续行符
                    if [ $d -lt $dataset ]; then
                        script_content+=" \\\n"
                    fi
                    
                    operation_counter=$((operation_counter + 1))
                done
                
                # 写入脚本文件
                echo -e "$script_content" > "$script_name"
                
                # 给脚本添加执行权限
                chmod +x "$script_name"
                
                #echo "已生成: $script_name"
            done
        done
    done
done

echo "生成完成！共生成 $((2 * 3 * 64 * 10)) = 3840 个脚本文件"
