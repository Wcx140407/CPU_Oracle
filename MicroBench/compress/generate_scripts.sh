#!/bin/bash

mkdir -p generated_scripts

for compiler in gcc clang; do
    for O in O1 O2 O3; do
        for thread in {1..64}; do
            for dataset in {1..10}; do
                cat > "generated_scripts/run_${compiler}_${thread}_${dataset}_${O}.sh" << EOF
#!/bin/bash
./compress_${compiler}_${O} ${thread} dataset${dataset}.conf 3
rm dataset/*.Z
EOF
                chmod +x "generated_scripts/run_${compiler}_${thread}_${dataset}_${O}.sh"
            done
        done
    done
done

echo "生成完成！共生成 3840 个脚本文件"
