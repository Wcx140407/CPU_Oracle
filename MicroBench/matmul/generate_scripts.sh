#!/bin/bash

mkdir -p generated_scripts

for compiler in gcc clang; do
    for O in O1 O2 O3; do
        for thread in {1..64}; do
            for dataset in {1..10}; do
                # 计算matrix_size
                #n=$(( (dataset + 1) * 8 ))
                n=$(( (dataset + 31) * 1 ))
		size=$(( n * n ))
                
                cat > "generated_scripts/run_${compiler}_${thread}_${dataset}_${O}.sh" << EOF
#!/bin/bash
./matmul_${compiler}_${O} ${size} ${thread} 3 64
EOF
                chmod +x "generated_scripts/run_${compiler}_${thread}_${dataset}_${O}.sh"
            done
        done
    done
done

echo "生成完成！共生成 3840 个脚本文件"
