#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>
#include <time.h>
#include <pthread.h>
#include <unistd.h>

// 参数结构体，用于传递线程参数
typedef struct {
    int thread_id;          // 线程ID
    int num_threads;        // 线程总数
    int N;                  // 矩阵大小N×N
    double omega;           // SOR参数
    int cycles;             // 迭代次数
    double **G;             // 矩阵数据
    double *partial_result; // 部分结果
    int start_row;          // 起始行
    int end_row;            // 结束行（不包含）
} ThreadData;

// Random结构体（保持不变）
typedef struct {
    int m[17];                        
    int seed;                             
    int i;                                
    int j;                                
    int haveRange;            
    double left;                          
    double right;                         
    double width;                         
} Random_struct, *Random;

// Stopwatch结构体（保持不变）
typedef struct {
    int running;        
    double last_time;
    double total;
} *Stopwatch, Stopwatch_struct;

#ifndef NULL
#define NULL 0
#endif

#define MDIG 32
#define ONE 1
static const int m1 = (ONE << (MDIG-2)) + ((ONE << (MDIG-2) )-ONE);
static const int m2 = ONE << MDIG/2;
static double dm1;

// 互斥锁，用于保护共享资源
pthread_mutex_t mutex = PTHREAD_MUTEX_INITIALIZER;

// 屏障，用于同步所有线程
pthread_barrier_t barrier;

// Random函数（保持不变）
static void initialize(Random R, int seed);
Random new_Random_seed(int seed);
Random new_Random(int seed, double left, double right);
void Random_delete(Random R);
double Random_nextDouble(Random R);
double *RandomVector(int N, Random R);
double **RandomMatrix(int M, int N, Random R);
double** new_Array2D_double(int M, int N);
void Array2D_double_delete(int M, int N, double **A);
void Array2D_double_copy(int M, int N, double **B, double **A);

// Stopwatch函数（保持不变）
double seconds();
void Stopwtach_reset(Stopwatch Q);
Stopwatch new_Stopwatch(void);
void Stopwatch_delete(Stopwatch S);
void Stopwatch_start(Stopwatch Q);
void Stopwatch_resume(Stopwatch Q);
void Stopwatch_stop(Stopwatch Q);
double Stopwatch_read(Stopwatch Q);

// SOR函数（修改为支持并发）
double SOR_num_flops(int M, int N, int num_iterations) {
    double Md = (double) M;
    double Nd = (double) N;
    double num_iterD = (double) num_iterations;
    return (Md-1)*(Nd-1)*num_iterD*6.0;
}

// 线程函数：执行SOR计算的部分
void* SOR_thread_execute(void* arg) {
    ThreadData* data = (ThreadData*)arg;
    int N = data->N;
    double omega = data->omega;
    int cycles = data->cycles;
    int start_row = data->start_row;
    int end_row = data->end_row;
    
    double omega_over_four = omega * 0.25;
    double one_minus_omega = 1.0 - omega;
    int Nm1 = N-1;
    
    // 执行指定次数的迭代
    for (int p = 0; p < cycles; p++) {
        // 等待所有线程完成当前迭代
        pthread_barrier_wait(&barrier);
        
        // 更新分配给当前线程的行
        for (int i = start_row; i < end_row; i++) {
            if (i <= 0 || i >= N-1) continue; // 跳过边界
            
            double* Gi = data->G[i];
            double* Gim1 = data->G[i-1];
            double* Gip1 = data->G[i+1];
            
            for (int j = 1; j < Nm1; j++) {
                Gi[j] = omega_over_four * (Gim1[j] + Gip1[j] + Gi[j-1] + Gi[j+1]) 
                        + one_minus_omega * Gi[j];
            }
        }
    }
    
    return NULL;
}

// 并发SOR执行函数
void SOR_execute_concurrent(int M, int N, double omega, double **G, 
                           int num_iterations, int num_threads) {
    pthread_t threads[num_threads];
    ThreadData thread_data[num_threads];
    
    // 初始化屏障
    pthread_barrier_init(&barrier, NULL, num_threads);
    
    // 计算每个线程处理的行数
    int rows_per_thread = M / num_threads;
    int extra_rows = M % num_threads;
    
    // 创建并启动线程
    for (int t = 0; t < num_threads; t++) {
        thread_data[t].thread_id = t;
        thread_data[t].num_threads = num_threads;
        thread_data[t].N = N;
        thread_data[t].omega = omega;
        thread_data[t].cycles = num_iterations;
        thread_data[t].G = G;
        
        // 分配行范围
        thread_data[t].start_row = t * rows_per_thread;
        thread_data[t].end_row = (t + 1) * rows_per_thread;
        
        // 处理额外的行
        if (t == num_threads - 1) {
            thread_data[t].end_row += extra_rows;
        }
        
        pthread_create(&threads[t], NULL, SOR_thread_execute, &thread_data[t]);
    }
    
    // 等待所有线程完成
    for (int t = 0; t < num_threads; t++) {
        pthread_join(threads[t], NULL);
    }
    
    // 销毁屏障
    pthread_barrier_destroy(&barrier);
}

// 测量并发SOR性能
double kernel_measureSOR_concurrent(int N, double min_time, Random R, int num_threads) {
    double **G = RandomMatrix(N, N, R);
    double result = 0.0;
    
    Stopwatch Q = new_Stopwatch();
    int cycles = 1;
    
    // 预热一次
    SOR_execute_concurrent(N, N, 1.25, G, 1, num_threads);
    
    while(1) {
        Stopwatch_start(Q);
        SOR_execute_concurrent(N, N, 1.25, G, cycles, num_threads);
        Stopwatch_stop(Q);
        
        if (Stopwatch_read(Q) >= min_time) break;
        cycles *= 2;
    }
    
    // 计算Mflops
    result = SOR_num_flops(N, N, cycles) / Stopwatch_read(Q) * 1.0e-6;
    
    Stopwatch_delete(Q);
    Array2D_double_delete(N, N, G);
    return result;
}

// 打印使用说明
void print_usage(const char* program_name) {
    printf("Usage: %s [OPTIONS]\n", program_name);
    printf("Options:\n");
    printf("  -s SIZE      Set SOR matrix size (N x N), default: 1000\n");
    printf("  -t THREADS   Set number of threads, default: 4\n");
    printf("  -i ITER      Set base iteration count, default: 1\n");
    printf("  -o OMEGA     Set SOR omega parameter, default: 1.25\n");
    printf("  -m TIME      Set minimum measurement time (seconds), default: 2.0\n");
    printf("  -r SEED      Set random seed, default: 101010\n");
    printf("  -h           Show this help message\n");
    printf("\n");
    printf("Example: %s -s 500 -t 8 -m 5.0\n", program_name);
}

int main(int argc, char *argv[]) {
    // 默认参数
    int SOR_size = 1000;
    int num_threads = 4;
    int base_iterations = 1;
    double omega = 1.25;
    double min_time = 2.0;
    int random_seed = 101010;
    
    // 解析命令行参数
    int opt;
    while ((opt = getopt(argc, argv, "s:t:i:o:m:r:h")) != -1) {
        switch (opt) {
            case 's':
                SOR_size = atoi(optarg);
                if (SOR_size <= 0) {
                    fprintf(stderr, "Error: Size must be positive\n");
                    return 1;
                }
                break;
            case 't':
                num_threads = atoi(optarg);
                if (num_threads <= 0) {
                    fprintf(stderr, "Error: Thread count must be positive\n");
                    return 1;
                }
                break;
            case 'i':
                base_iterations = atoi(optarg);
                if (base_iterations <= 0) {
                    fprintf(stderr, "Error: Iteration count must be positive\n");
                    return 1;
                }
                break;
            case 'o':
                omega = atof(optarg);
                if (omega <= 0 || omega >= 2) {
                    fprintf(stderr, "Warning: Omega parameter should be between 0 and 2\n");
                }
                break;
            case 'm':
                min_time = atof(optarg);
                if (min_time <= 0) {
                    fprintf(stderr, "Error: Measurement time must be positive\n");
                    return 1;
                }
                break;
            case 'r':
                random_seed = atoi(optarg);
                break;
            case 'h':
                print_usage(argv[0]);
                return 0;
            default:
                fprintf(stderr, "Unknown option: %c\n", opt);
                print_usage(argv[0]);
                return 1;
        }
    }
    
    // 检查逻辑核心数
    int max_threads = sysconf(_SC_NPROCESSORS_ONLN);
    /*if (num_threads > max_threads) {
        printf("Warning: Requested %d threads but system has only %d logical cores\n",
               num_threads, max_threads);
        printf("Setting threads to %d\n", max_threads);
        num_threads = max_threads;
    }*/
    
    printf("========================================\n");
    printf("Concurrent SOR Benchmark\n");
    printf("========================================\n");
    printf("Matrix size:      %d x %d\n", SOR_size, SOR_size);
    printf("Number of threads: %d\n", num_threads);
    printf("Omega parameter:   %.2f\n", omega);
    printf("Random seed:       %d\n", random_seed);
    printf("Min measurement time: %.1f seconds\n", min_time);
    printf("System logical cores: %d\n", max_threads);
    printf("========================================\n");
    
    // 初始化随机数生成器
    Random R = new_Random_seed(random_seed);
    
    // 运行并发基准测试
    double start_time = seconds();
    double mflops = kernel_measureSOR_concurrent(SOR_size, min_time, R, num_threads);
    double end_time = seconds();
    
    printf("SOR Mflops:       %8.2f\n", mflops);
    printf("Total time:       %8.2f seconds\n", end_time - start_time);
    
    // 清理
    Random_delete(R);
    pthread_mutex_destroy(&mutex);
    
    return 0;
}

// 以下是未修改的辅助函数实现（与原始代码相同）

static void initialize(Random R, int seed) {
    int jseed, k0, k1, j0, j1, iloop;
    dm1 = 1.0 / (double) m1; 
    R->seed = seed;
    if (seed < 0) seed = -seed;
    jseed = (seed < m1 ? seed : m1);
    if (jseed % 2 == 0) --jseed;
    k0 = 9069 % m2;
    k1 = 9069 / m2;
    j0 = jseed % m2;
    j1 = jseed / m2;
    for (iloop = 0; iloop < 17; ++iloop) {
        jseed = j0 * k0;
        j1 = (jseed / m2 + j0 * k1 + j1 * k0) % (m2 / 2);
        j0 = jseed % m2;
        R->m[iloop] = j0 + m2 * j1;
    }
    R->i = 4;
    R->j = 16;
}

Random new_Random_seed(int seed) {
    Random R = (Random) malloc(sizeof(Random_struct));
    initialize(R, seed);
    R->left = 0.0;
    R->right = 1.0;
    R->width = 1.0;
    R->haveRange = 0;
    return R;
}

Random new_Random(int seed, double left, double right) {
    Random R = (Random) malloc(sizeof(Random_struct));
    initialize(R, seed);
    R->left = left;
    R->right = right;
    R->width = right - left;
    R->haveRange = 1;
    return R;
}

void Random_delete(Random R) {
    free(R);
}

double Random_nextDouble(Random R) {
    int k;
    int I = R->i;
    int J = R->j;
    int *m = R->m;
    k = m[I] - m[J];
    if (k < 0) k += m1;
    R->m[J] = k;
    if (I == 0) I = 16;
    else I--;
    R->i = I;
    if (J == 0) J = 16;
    else J--;
    R->j = J;
    if (R->haveRange) 
        return R->left + dm1 * (double) k * R->width;
    else
        return dm1 * (double) k;
}

double *RandomVector(int N, Random R) {
    int i;
    double *x = (double *) malloc(sizeof(double)*N);
    for (i=0; i<N; i++)
        x[i] = Random_nextDouble(R);
    return x;
}

double **RandomMatrix(int M, int N, Random R) {
    int i, j;
    double **A = (double **) malloc(sizeof(double*)*M);
    if (A == NULL) return NULL;
    for (i=0; i<M; i++) {
        A[i] = (double *) malloc(sizeof(double)*N);
        if (A[i] == NULL) {
            free(A);
            return NULL;
        }
        for (j=0; j<N; j++)
            A[i][j] = Random_nextDouble(R);
    }
    return A;
}

double** new_Array2D_double(int M, int N) {
    int i=0;
    int failed = 0;
    double **A = (double**) malloc(sizeof(double*)*M);
    if (A == NULL) return NULL;
    for (i=0; i<M; i++) {
        A[i] = (double*) malloc(N * sizeof(double));
        if (A[i] == NULL) {
            failed = 1;
            break;
        }
    }
    if (failed) {
        i--;
        for (; i>=0; i--)
            free(A[i]);
        free(A);
        return NULL;
    }
    else
        return A;
}

void Array2D_double_delete(int M, int N, double **A) {
    int i;
    if (A == NULL) return;
    for (i=0; i<M; i++)
        free(A[i]);
    free(A);
}

void Array2D_double_copy(int M, int N, double **B, double **A) {
    int remainder = N & 3;
    int i=0;
    int j=0;
    for (i=0; i<M; i++) {
        double *Bi = B[i];
        double *Ai = A[i];
        for (j=0; j<remainder; j++)
            Bi[j] = Ai[j];
        for (j=remainder; j<N; j+=4) {
            Bi[j] = Ai[j];
            Bi[j+1] = Ai[j+1];
            Bi[j+2] = Ai[j+2];
            Bi[j+3] = Ai[j+3];
        }
    }
}

double seconds() {
    return ((double) clock()) / (double) CLOCKS_PER_SEC; 
}

void Stopwtach_reset(Stopwatch Q) {
    Q->running = 0;
    Q->last_time = 0.0;
    Q->total = 0.0;
}

Stopwatch new_Stopwatch(void) {
    Stopwatch S = (Stopwatch) malloc(sizeof(Stopwatch_struct));
    if (S == NULL) return NULL;
    Stopwtach_reset(S);
    return S;
}

void Stopwatch_delete(Stopwatch S) {
    if (S != NULL) free(S);
}

void Stopwatch_start(Stopwatch Q) {
    if (!(Q->running)) {
        Q->running = 1;
        Q->total = 0.0;
        Q->last_time = seconds();
    }
}

void Stopwatch_resume(Stopwatch Q) {
    if (!(Q->running)) {
        Q->last_time = seconds();
        Q->running = 1;
    }
}

void Stopwatch_stop(Stopwatch Q) {
    if (Q->running) {
        Q->total += seconds() - Q->last_time;
        Q->running = 0;
    }
}

double Stopwatch_read(Stopwatch Q) {
    if (Q->running) {
        double t = seconds();
        Q->total += t - Q->last_time;
        Q->last_time = t;
    }
    return Q->total;
}
