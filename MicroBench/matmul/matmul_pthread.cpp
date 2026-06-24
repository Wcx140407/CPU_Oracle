#include <iostream>
#include <pthread.h>
#include <cstdlib>
#include <cstring>
#include <cmath>
#include <sys/time.h>
#include <vector>
#include <algorithm>

// 全局变量
int n;                 // 矩阵大小 n x n
int num_threads;       // 线程数
int blockSize;         // 分块大小
double **A, **B, **C;  // 矩阵指针
int numreps;           // 重复次数

// 线程参数结构体
struct ThreadData {
    int thread_id;
    int start_block_i;
    int end_block_i;
    int start_block_j;
    int end_block_j;
    int start_block_k;
    int end_block_k;
};

// 分块矩阵乘法函数（线程版本）
void* MultiplyThread(void* arg) {
    ThreadData* data = (ThreadData*)arg;
    
    for (int bi = data->start_block_i; bi < data->end_block_i; bi += blockSize) {
        for (int bj = data->start_block_j; bj < data->end_block_j; bj += blockSize) {
            for (int bk = data->start_block_k; bk < data->end_block_k; bk += blockSize) {
                int i_limit = std::min(bi + blockSize, n);
                int j_limit = std::min(bj + blockSize, n);
                int k_limit = std::min(bk + blockSize, n);
                
                for (int i = bi; i < i_limit; i++) {
                    for (int j = bj; j < j_limit; j++) {
                        double sum = C[i][j];
                        for (int k = bk; k < k_limit; k++) {
                            sum += A[i][k] * B[k][j];
                        }
                        C[i][j] = sum;
                    }
                }
            }
        }
    }
    
    pthread_exit(NULL);
}

// 并行的分块矩阵乘法
void ParallelMultiply() {
    pthread_t* threads = new pthread_t[num_threads];
    ThreadData* thread_data = new ThreadData[num_threads];
    
    // 计算每个线程处理的行块范围
    int blocks_per_dim = (n + blockSize - 1) / blockSize;
    int blocks_per_thread = blocks_per_dim / num_threads;
    
    // 创建并启动线程
    for (int t = 0; t < num_threads; t++) {
        thread_data[t].thread_id = t;
        
        // 分配工作负载 - 按行块分配
        int start_block = t * blocks_per_thread;
        int end_block = (t == num_threads - 1) ? blocks_per_dim : (t + 1) * blocks_per_thread;
        
        thread_data[t].start_block_i = start_block * blockSize;
        thread_data[t].end_block_i = std::min(end_block * blockSize, n);
        thread_data[t].start_block_j = 0;
        thread_data[t].end_block_j = n;
        thread_data[t].start_block_k = 0;
        thread_data[t].end_block_k = n;
        
        pthread_create(&threads[t], NULL, MultiplyThread, &thread_data[t]);
    }
    
    // 等待所有线程完成
    for (int t = 0; t < num_threads; t++) {
        pthread_join(threads[t], NULL);
    }
    
    delete[] threads;
    delete[] thread_data;
}

// 初始化矩阵
void InitializeMatrices() {
    A = new double*[n];
    B = new double*[n];
    C = new double*[n];
    
    // 分配连续的内存空间
    A[0] = new double[n * n];
    B[0] = new double[n * n];
    C[0] = new double[n * n];
    
    for (int i = 1; i < n; i++) {
        A[i] = A[0] + i * n;
        B[i] = B[0] + i * n;
        C[i] = C[0] + i * n;
    }
    
    // 初始化矩阵
    for (int i = 0; i < n; i++) {
        for (int j = 0; j < n; j++) {
            A[i][j] = 1.0 * rand() / RAND_MAX;
            B[i][j] = 1.0 * rand() / RAND_MAX;
            C[i][j] = 0.0;
        }
    }
}

// 清理矩阵内存
void CleanupMatrices() {
    delete[] A[0];
    delete[] B[0];
    delete[] C[0];
    delete[] A;
    delete[] B;
    delete[] C;
}

// 验证结果（可选，用于调试）
void VerifyResult() {
    std::cout << "验证结果（前5x5区域）:" << std::endl;
    for (int i = 0; i < std::min(5, n); i++) {
        for (int j = 0; j < std::min(5, n); j++) {
            double sum = 0.0;
            for (int k = 0; k < n; k++) {
                sum += A[i][k] * B[k][j];
            }
            std::cout << "C[" << i << "][" << j << "] = " << C[i][j] 
                     << ", expected = " << sum << std::endl;
        }
    }
}

int main(int argc, char* argv[]) {
    // 默认参数
    n = 32 * 32;
    num_threads = 4;
    numreps = 10;
    
    // 命令行参数解析
    // 用法: ./program [矩阵大小] [线程数] [重复次数] [分块大小]
    // 示例: ./program 1024 8 5 64
    if (argc >= 2) {
        n = atoi(argv[1]);
    }
    if (argc >= 3) {
        num_threads = atoi(argv[2]);
    }
    if (argc >= 4) {
        numreps = atoi(argv[3]);
    }
    
    // 设置分块大小（可选的第四个参数）
    if (argc >= 5) {
        blockSize = atoi(argv[4]);
    } else {
        // 默认分块大小，基于缓存友好性考虑
        blockSize = std::min(64, n);
    }
    
    // 打印配置信息
    std::cout << "矩阵乘法配置:" << std::endl;
    std::cout << "  矩阵大小: " << n << " x " << n << std::endl;
    std::cout << "  线程数: " << num_threads << std::endl;
    std::cout << "  重复次数: " << numreps << std::endl;
    std::cout << "  分块大小: " << blockSize << std::endl;
    
    // 初始化矩阵
    InitializeMatrices();
    
    // 执行矩阵乘法
    struct timeval tv1, tv2;
    double total_elapsed = 0.0;
    
    std::cout << "开始并行矩阵乘法..." << std::endl;
    
    for (int rep = 0; rep < numreps; rep++) {
        // 重置C矩阵
        memset(C[0], 0, n * n * sizeof(double));
        
        gettimeofday(&tv1, NULL);
        ParallelMultiply();
        gettimeofday(&tv2, NULL);
        
        double elapsed = (tv2.tv_sec - tv1.tv_sec) + 
                        (tv2.tv_usec - tv1.tv_usec) * 1e-6;
        
        std::cout << "第 " << rep + 1 << " 次运行时间: " << elapsed << " 秒" << std::endl;
        total_elapsed += elapsed;
    }
    
    // 打印统计信息
    std::cout << "\n统计结果:" << std::endl;
    std::cout << "  总时间: " << total_elapsed << " 秒" << std::endl;
    std::cout << "  平均时间: " << total_elapsed / numreps << " 秒" << std::endl;
    std::cout << "  性能: " << (2.0 * n * n * n * numreps / total_elapsed / 1e9) 
              << " GFLOPs" << std::endl;
    
    // 验证结果（可选，对于大矩阵可能会很慢）
    if (n <= 256) {
        VerifyResult();
    }
    
    // 清理内存
    CleanupMatrices();
    
    return 0;
}
