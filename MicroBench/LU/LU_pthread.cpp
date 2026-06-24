#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>
#include <time.h>
#include <pthread.h>
#include <semaphore.h>
#include <getopt.h>

// ==================== 随机数生成器 (Random) ====================
typedef struct {
    int m[17];                        
    int seed;                             
    int i;                                // 原本 = 4
    int j;                                // 原本 = 16
    int haveRange;                        // 布尔值
    double left;                          // = 0.0
    double right;                         // = 1.0
    double width;                         // = 1.0
} Random_struct, *Random;

#define MDIG 32
#define ONE 1
static const int m1 = (ONE << (MDIG-2)) + ((ONE << (MDIG-2)) - ONE);
static const int m2 = ONE << MDIG/2;
static double dm1;  // = 1.0 / (double) m1;

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

double** RandomMatrix(int M, int N, Random R) {
    int i, j;
    double **A = (double **) malloc(sizeof(double*) * M);
    if (A == NULL) return NULL;
    for (i = 0; i < M; i++) {
        A[i] = (double *) malloc(sizeof(double) * N);
        if (A[i] == NULL) {
            for (int k = 0; k < i; k++) free(A[k]);
            free(A);
            return NULL;
        }
        for (j = 0; j < N; j++)
            A[i][j] = Random_nextDouble(R);
    }
    return A;
}

// ==================== 数据集配置 ====================
typedef struct {
    int dataset_id;
    int matrix_size;      // 矩阵大小 NxN
    int random_seed;      // 随机种子
    char name[64];        // 数据集名称
    int num_threads;      // 用于此数据集的线程数
} DatasetConfig;

typedef struct {
    int num_datasets;           // 数据集总数
    DatasetConfig *datasets;    // 数据集数组
    int total_threads;          // 总线程数
    double min_time;            // 最小运行时间
    int verbose;                // 详细输出
} GlobalConfig;

// ==================== 线程池和任务队列 ====================
typedef struct {
    void (*task_function)(void*);   // 任务函数
    void *task_data;                // 任务数据
    int dataset_id;                 // 所属数据集ID
    int task_id;                    // 任务ID
} Task;

typedef struct {
    Task *tasks;                    // 任务数组
    int capacity;                   // 队列容量
    int size;                       // 当前大小
    int front;                      // 队首
    int rear;                       // 队尾
    pthread_mutex_t lock;           // 互斥锁
    sem_t tasks_available;          // 任务可用信号量
    sem_t slots_available;          // 空位可用信号量
    int shutdown;                   // 关闭标志
} TaskQueue;

typedef struct {
    pthread_t thread;               // 线程句柄
    int thread_id;                  // 线程ID
    TaskQueue *queue;               // 任务队列
    int busy;                       // 忙碌标志
} WorkerThread;

typedef struct {
    WorkerThread *workers;          // 工作线程数组
    int num_workers;                // 工作线程数
    TaskQueue queue;                // 任务队列
} ThreadPool;

// ==================== LU分解并行数据结构 ====================
typedef struct {
    int dataset_id;                 // 数据集ID
    int matrix_size;                // 矩阵大小
    double **A;                     // 原始矩阵
    double **lu;                    // LU分解矩阵
    int *pivot;                     // 主元数组
    int *pivot_flag;                // 主元更新标志
    pthread_mutex_t *row_locks;     // 行锁数组
    pthread_barrier_t *barrier;     // 屏障
    int num_threads;                // 使用的线程数
    int thread_id;                  // 线程ID
    int start_row;                  // 起始行
    int end_row;                    // 结束行
    int start_col;                  // 起始列
    int end_col;                    // 结束列
    int current_column;             // 当前处理的列
    volatile int *current_col_ptr;  // 当前列指针
    volatile int *task_completed;   // 任务完成标志
    double mflops_result;           // 性能结果
    double total_time;              // 总时间
    int cycles;                     // 循环次数
} LUThreadData;

typedef struct {
    int dataset_id;                 // 数据集ID
    DatasetConfig *config;          // 数据集配置
    double **A;                     // 原始矩阵
    double **lu;                    // LU分解矩阵
    int *pivot;                     // 主元数组
    pthread_mutex_t *row_locks;     // 行锁数组
    pthread_barrier_t barrier;      // 屏障
    int num_threads;                // 使用的线程数
    volatile int current_column;    // 当前处理的列
    volatile int tasks_completed;   // 任务完成计数
    double mflops;                  // 性能结果(Mflops)
    double total_time;              // 总时间
    int cycles;                     // 循环次数
} LUDecomposition;

// ==================== 任务队列实现 ====================
TaskQueue* task_queue_create(int capacity) {
    TaskQueue *queue = (TaskQueue*) malloc(sizeof(TaskQueue));
    queue->capacity = capacity;
    queue->size = 0;
    queue->front = 0;
    queue->rear = 0;
    queue->shutdown = 0;
    queue->tasks = (Task*) malloc(sizeof(Task) * capacity);
    
    pthread_mutex_init(&queue->lock, NULL);
    sem_init(&queue->tasks_available, 0, 0);
    sem_init(&queue->slots_available, 0, capacity);
    
    return queue;
}

void task_queue_destroy(TaskQueue *queue) {
    if (!queue) return;
    pthread_mutex_destroy(&queue->lock);
    sem_destroy(&queue->tasks_available);
    sem_destroy(&queue->slots_available);
    free(queue->tasks);
    free(queue);
}

int task_queue_push(TaskQueue *queue, Task task) {
    sem_wait(&queue->slots_available);
    pthread_mutex_lock(&queue->lock);
    
    if (queue->shutdown) {
        pthread_mutex_unlock(&queue->lock);
        sem_post(&queue->slots_available);
        return -1;
    }
    
    queue->tasks[queue->rear] = task;
    queue->rear = (queue->rear + 1) % queue->capacity;
    queue->size++;
    
    pthread_mutex_unlock(&queue->lock);
    sem_post(&queue->tasks_available);
    return 0;
}

int task_queue_pop(TaskQueue *queue, Task *task) {
    sem_wait(&queue->tasks_available);
    pthread_mutex_lock(&queue->lock);
    
    if (queue->size == 0 && queue->shutdown) {
        pthread_mutex_unlock(&queue->lock);
        sem_post(&queue->tasks_available);
        return -1;
    }
    
    *task = queue->tasks[queue->front];
    queue->front = (queue->front + 1) % queue->capacity;
    queue->size--;
    
    pthread_mutex_unlock(&queue->lock);
    sem_post(&queue->slots_available);
    return 0;
}

void task_queue_shutdown(TaskQueue *queue) {
    pthread_mutex_lock(&queue->lock);
    queue->shutdown = 1;
    pthread_mutex_unlock(&queue->lock);
    
    // 唤醒所有等待的线程
    for (int i = 0; i < queue->capacity; i++) {
        sem_post(&queue->tasks_available);
        sem_post(&queue->slots_available);
    }
}

// ==================== 线程池实现 ====================
void* worker_thread_func(void *arg) {
    WorkerThread *worker = (WorkerThread*) arg;
    TaskQueue *queue = worker->queue;
    Task task;
    
    while (1) {
        if (task_queue_pop(queue, &task) == -1) {
            break;  // 队列关闭
        }
        
        worker->busy = 1;
        task.task_function(task.task_data);
        worker->busy = 0;
        
        free(task.task_data);
    }
    
    pthread_exit(NULL);
}

ThreadPool* thread_pool_create(int num_workers, int queue_capacity) {
    ThreadPool *pool = (ThreadPool*) malloc(sizeof(ThreadPool));
    pool->num_workers = num_workers;
    pool->workers = (WorkerThread*) malloc(sizeof(WorkerThread) * num_workers);
    pool->queue = *task_queue_create(queue_capacity);
    
    for (int i = 0; i < num_workers; i++) {
        pool->workers[i].thread_id = i;
        pool->workers[i].queue = &pool->queue;
        pool->workers[i].busy = 0;
        pthread_create(&pool->workers[i].thread, NULL, worker_thread_func, &pool->workers[i]);
    }
    
    return pool;
}

void thread_pool_destroy(ThreadPool *pool) {
    if (!pool) return;
    
    task_queue_shutdown(&pool->queue);
    
    for (int i = 0; i < pool->num_workers; i++) {
        pthread_join(pool->workers[i].thread, NULL);
    }
    
    task_queue_destroy(&pool->queue);
    free(pool->workers);
    free(pool);
}

void thread_pool_submit(ThreadPool *pool, void (*task_func)(void*), void *task_data, 
                        int dataset_id, int task_id) {
    Task task;
    task.task_function = task_func;
    task.task_data = task_data;
    task.dataset_id = dataset_id;
    task.task_id = task_id;
    
    task_queue_push(&pool->queue, task);
}

// ==================== 并行LU分解实现 ====================
double LU_num_flops(int N) {
    double Nd = (double) N;
    return (2.0 * Nd * Nd * Nd / 3.0);
}

void copy_matrix(int M, int N, double **dest, double **src) {
    for (int i = 0; i < M; i++)
        for (int j = 0; j < N; j++)
            dest[i][j] = src[i][j];
}

// 并行LU分解的线程函数
void* parallel_lu_thread_func(void *arg) {
    LUThreadData *data = (LUThreadData*) arg;
    int N = data->matrix_size;
    double **A = data->lu;
    int *pivot = data->pivot;
    
    int start_row = data->start_row;
    int end_row = data->end_row;
    int start_col = data->start_col;
    int end_col = data->end_col;
    
    for (int j = 0; j < N; j++) {
        // 等待所有线程到达这一列
        pthread_barrier_wait(data->barrier);
        
        // 只有主线程(thread_id=0)进行主元查找和行交换
        if (data->thread_id == 0) {
            // 查找主元
            int jp = j;
            double t = fabs(A[j][j]);
            for (int i = j + 1; i < N; i++) {
                double ab = fabs(A[i][j]);
                if (ab > t) {
                    jp = i;
                    t = ab;
                }
            }
            
            pivot[j] = jp;
            
            // 执行行交换
            if (jp != j) {
                double *temp = A[j];
                A[j] = A[jp];
                A[jp] = temp;
            }
            
            // 计算缩放因子
            if (A[j][j] != 0.0 && j < N - 1) {
                double recp = 1.0 / A[j][j];
                for (int i = j + 1; i < N; i++) {
                    A[i][j] *= recp;
                }
            }
        }
        
        // 等待主线程完成行交换和缩放
        pthread_barrier_wait(data->barrier);
        
        // 并行更新子矩阵
        if (j < N - 1) {
            for (int i = start_row; i < end_row; i++) {
                if (i > j) {  // 只处理对角线以下的元素
                    double multiplier = A[i][j];
                    for (int k = start_col; k < end_col; k++) {
                        if (k > j) {  // 只处理对角线右侧的元素
                            A[i][k] -= multiplier * A[j][k];
                        }
                    }
                }
            }
        }
        
        // 等待所有线程完成这一列的更新
        pthread_barrier_wait(data->barrier);
    }
    
    pthread_exit(NULL);
}

// 执行LU分解任务
void lu_decomposition_task(void *arg) {
    LUThreadData *data = (LUThreadData*) arg;
    int N = data->matrix_size;
    
    struct timespec start, end;
    clock_gettime(CLOCK_MONOTONIC, &start);
    
    // 执行并行LU分解
    parallel_lu_thread_func(data);
    
    clock_gettime(CLOCK_MONOTONIC, &end);
    double elapsed = (end.tv_sec - start.tv_sec) + 
                    (end.tv_nsec - start.tv_nsec) / 1e9;
    
    data->total_time = elapsed;
    
    // 计算性能(Mflops)
    if (data->thread_id == 0) {
        double flops = LU_num_flops(N);
        data->mflops_result = flops / elapsed / 1e6;
    }
}

// 测量LU分解性能
double measure_lu_performance(DatasetConfig *config, int num_threads, 
                              double min_time, GlobalConfig *global) {
    int N = config->matrix_size;
    Random R = new_Random_seed(config->random_seed);
    
    // 创建矩阵
    double **A = RandomMatrix(N, N, R);
    double **lu = (double**) malloc(sizeof(double*) * N);
    for (int i = 0; i < N; i++) {
        lu[i] = (double*) malloc(sizeof(double) * N);
        memcpy(lu[i], A[i], sizeof(double) * N);
    }
    
    int *pivot = (int*) malloc(sizeof(int) * N);
    
    // 创建线程数据
    pthread_t *threads = (pthread_t*) malloc(sizeof(pthread_t) * num_threads);
    LUThreadData *thread_data = (LUThreadData*) malloc(sizeof(LUThreadData) * num_threads);
    pthread_barrier_t barrier;
    pthread_barrier_init(&barrier, NULL, num_threads);
    
    // 计算每个线程的工作范围
    int rows_per_thread = N / num_threads;
    int cols_per_thread = N / num_threads;
    
    double total_time = 0.0;
    int cycles = 1;
    
    struct timespec total_start, total_end;
    clock_gettime(CLOCK_MONOTONIC, &total_start);
    
    // 性能测量循环
    while (total_time < min_time) {
        struct timespec start, end;
        clock_gettime(CLOCK_MONOTONIC, &start);
        
        // 重置矩阵
        for (int i = 0; i < N; i++)
            memcpy(lu[i], A[i], sizeof(double) * N);
        
        // 创建并启动线程
        for (int t = 0; t < num_threads; t++) {
            thread_data[t].dataset_id = config->dataset_id;
            thread_data[t].matrix_size = N;
            thread_data[t].lu = lu;
            thread_data[t].pivot = pivot;
            thread_data[t].barrier = &barrier;
            thread_data[t].num_threads = num_threads;
            thread_data[t].thread_id = t;
            
            // 计算工作范围
            thread_data[t].start_row = t * rows_per_thread;
            thread_data[t].end_row = (t == num_threads - 1) ? N : (t + 1) * rows_per_thread;
            thread_data[t].start_col = t * cols_per_thread;
            thread_data[t].end_col = (t == num_threads - 1) ? N : (t + 1) * cols_per_thread;
            
            pthread_create(&threads[t], NULL, parallel_lu_thread_func, &thread_data[t]);
        }
        
        // 等待所有线程完成
        for (int t = 0; t < num_threads; t++) {
            pthread_join(threads[t], NULL);
        }
        
        clock_gettime(CLOCK_MONOTONIC, &end);
        double elapsed = (end.tv_sec - start.tv_sec) + 
                        (end.tv_nsec - start.tv_nsec) / 1e9;
        total_time += elapsed;
        
        if (total_time < min_time) {
            cycles *= 2;
        }
    }
    
    clock_gettime(CLOCK_MONOTONIC, &total_end);
    double total_elapsed = (total_end.tv_sec - total_start.tv_sec) + 
                          (total_end.tv_nsec - total_start.tv_nsec) / 1e9;
    
    // 计算平均性能
    double flops_per_cycle = LU_num_flops(N);
    double total_flops = flops_per_cycle * cycles;
    double mflops = total_flops / total_elapsed / 1e6;
    
    // 清理
    pthread_barrier_destroy(&barrier);
    free(threads);
    free(thread_data);
    Random_delete(R);
    for (int i = 0; i < N; i++) {
        free(A[i]);
        free(lu[i]);
    }
    free(A);
    free(lu);
    free(pivot);
    
    return mflops;
}

// ==================== 参数解析和配置 ====================
void print_help() {
    printf("并行LU分解性能测试程序\n");
    printf("用法: ./parallel_lu [选项]\n\n");
    printf("选项:\n");
    printf("  -d, --datasets=NUM       数据集数量 (默认: 1)\n");
    printf("  -t, --threads=NUM        总线程数 (默认: 1)\n");
    printf("  -m, --min-time=SEC       最小测量时间(秒) (默认: 2.0)\n");
    printf("  -v, --verbose            详细输出模式\n");
    printf("  -h, --help               显示此帮助信息\n\n");
    printf("数据集选项 (对于每个数据集 i=0..N-1):\n");
    printf("  --size[i]=NUM            矩阵大小 NxN (默认: 1000)\n");
    printf("  --seed[i]=NUM            随机种子 (默认: 101010+i)\n");
    printf("  --name[i]=NAME           数据集名称\n");
    printf("  --threads-per-dataset[i]=NUM  每个数据集的线程数\n\n");
    printf("示例:\n");
    printf("  ./parallel_lu --datasets=3 --threads=6 \\\n");
    printf("    --size0=500 --seed0=12345 --name0=small --threads-per-dataset0=2 \\\n");
    printf("    --size1=1000 --seed1=23456 --name1=medium --threads-per-dataset1=3 \\\n");
    printf("    --size2=2000 --seed2=34567 --name2=large --threads-per-dataset2=1\n");
}

void parse_arguments(int argc, char *argv[], GlobalConfig *config) {
    // 设置默认值
    config->num_datasets = 1;
    config->total_threads = 1;
    config->min_time = 2.0;
    config->verbose = 0;
    
    // 第一次扫描：获取数据集数量
    for (int i = 1; i < argc; i++) {
        if (strstr(argv[i], "--datasets=")) {
            config->num_datasets = atoi(strchr(argv[i], '=') + 1);
        } else if (strcmp(argv[i], "-d") == 0 && i + 1 < argc) {
            config->num_datasets = atoi(argv[++i]);
        }
    }
    
    // 分配数据集内存
    config->datasets = (DatasetConfig*) calloc(config->num_datasets, sizeof(DatasetConfig));
    for (int i = 0; i < config->num_datasets; i++) {
        config->datasets[i].dataset_id = i;
        config->datasets[i].matrix_size = 1000;
        config->datasets[i].random_seed = 101010 + i;
        snprintf(config->datasets[i].name, 64, "dataset%d", i);
        config->datasets[i].num_threads = 1;
    }
    
    // 第二次扫描：解析所有参数
    for (int i = 1; i < argc; i++) {
        // 总线程数
        if (strstr(argv[i], "--threads=")) {
            config->total_threads = atoi(strchr(argv[i], '=') + 1);
        } else if (strcmp(argv[i], "-t") == 0 && i + 1 < argc) {
            config->total_threads = atoi(argv[++i]);
        }
        // 最小时间
        else if (strstr(argv[i], "--min-time=")) {
            config->min_time = atof(strchr(argv[i], '=') + 1);
        } else if (strcmp(argv[i], "-m") == 0 && i + 1 < argc) {
            config->min_time = atof(argv[++i]);
        }
        // 详细模式
        else if (strcmp(argv[i], "--verbose") == 0 || strcmp(argv[i], "-v") == 0) {
            config->verbose = 1;
        }
        // 帮助
        else if (strcmp(argv[i], "--help") == 0 || strcmp(argv[i], "-h") == 0) {
            print_help();
            exit(0);
        }
        // 数据集参数
        else if (strstr(argv[i], "--size")) {
            int idx = atoi(argv[i] + 6);
            if (idx >= 0 && idx < config->num_datasets) {
                config->datasets[idx].matrix_size = atoi(strchr(argv[i], '=') + 1);
            }
        }
        else if (strstr(argv[i], "--seed")) {
            int idx = atoi(argv[i] + 6);
            if (idx >= 0 && idx < config->num_datasets) {
                config->datasets[idx].random_seed = atoi(strchr(argv[i], '=') + 1);
            }
        }
        else if (strstr(argv[i], "--name")) {
            int idx = atoi(argv[i] + 6);
            if (idx >= 0 && idx < config->num_datasets) {
                strncpy(config->datasets[idx].name, strchr(argv[i], '=') + 1, 63);
            }
        }
        else if (strstr(argv[i], "--threads-per-dataset")) {
            int idx = atoi(argv[i] + 22);
            if (idx >= 0 && idx < config->num_datasets) {
                config->datasets[idx].num_threads = atoi(strchr(argv[i], '=') + 1);
            }
        }
    }
    
    // 如果没有为数据集指定线程数，则平均分配
    int total_dataset_threads = 0;
    for (int i = 0; i < config->num_datasets; i++) {
        total_dataset_threads += config->datasets[i].num_threads;
    }
    
    if (total_dataset_threads == config->num_datasets) {
        // 如果所有数据集都使用默认的1线程，则重新分配
        int threads_per_dataset = config->total_threads / config->num_datasets;
        int remaining = config->total_threads % config->num_datasets;
        
        for (int i = 0; i < config->num_datasets; i++) {
            config->datasets[i].num_threads = threads_per_dataset;
            if (i < remaining) config->datasets[i].num_threads++;
            
            if (config->datasets[i].num_threads < 1) 
                config->datasets[i].num_threads = 1;
        }
    }
}

// ==================== 主程序 ====================
int main(int argc, char *argv[]) {
    GlobalConfig config;
    parse_arguments(argc, argv, &config);
    
    printf("=== 并行LU分解性能测试 ===\n");
    printf("配置:\n");
    printf("  数据集数量: %d\n", config.num_datasets);
    printf("  总线程数: %d\n", config.total_threads);
    printf("  最小测量时间: %.1f 秒\n", config.min_time);
    printf("\n数据集配置:\n");
    
    for (int i = 0; i < config.num_datasets; i++) {
        printf("  数据集 %d: %s\n", i, config.datasets[i].name);
        printf("    矩阵大小: %dx%d\n", config.datasets[i].matrix_size, 
               config.datasets[i].matrix_size);
        printf("    随机种子: %d\n", config.datasets[i].random_seed);
        printf("    使用线程数: %d\n", config.datasets[i].num_threads);
    }
    printf("\n");
    
    // 创建线程池
    ThreadPool *pool = thread_pool_create(config.total_threads, 100);
    
    // 执行所有数据集的测试
    double *results = (double*) malloc(sizeof(double) * config.num_datasets);
    
    printf("开始测试...\n");
    struct timespec total_start, total_end;
    clock_gettime(CLOCK_MONOTONIC, &total_start);
    
    // 为每个数据集创建任务
    for (int i = 0; i < config.num_datasets; i++) {
        if (config.verbose) {
            printf("处理数据集 %d: %s (使用 %d 线程)\n", 
                   i, config.datasets[i].name, config.datasets[i].num_threads);
        }
        
        // 为数据集分配线程
        int actual_threads = config.datasets[i].num_threads;
        if (actual_threads > config.datasets[i].matrix_size) {
            actual_threads = config.datasets[i].matrix_size;
            if (config.verbose) {
                printf("  警告: 线程数超过矩阵大小，调整为 %d 线程\n", actual_threads);
            }
        }
        
        // 执行LU分解测试
        results[i] = measure_lu_performance(&config.datasets[i], actual_threads, 
                                           config.min_time, &config);
        
        printf("数据集 %d (%s): %.2f Mflops\n", 
               i, config.datasets[i].name, results[i]);
    }
    
    clock_gettime(CLOCK_MONOTONIC, &total_end);
    double total_time = (total_end.tv_sec - total_start.tv_sec) + 
                       (total_end.tv_nsec - total_start.tv_nsec) / 1e9;
    
    // 输出汇总结果
    printf("\n=== 测试完成 ===\n");
    printf("总运行时间: %.2f 秒\n", total_time);
    printf("\n性能汇总:\n");
    
    double avg_mflops = 0.0;
    for (int i = 0; i < config.num_datasets; i++) {
        printf("  数据集 %d (%s): %8.2f Mflops (矩阵大小: %d, 线程数: %d)\n",
               i, config.datasets[i].name, results[i],
               config.datasets[i].matrix_size, config.datasets[i].num_threads);
        avg_mflops += results[i];
    }
    
    if (config.num_datasets > 0) {
        avg_mflops /= config.num_datasets;
        printf("\n平均性能: %.2f Mflops\n", avg_mflops);
    }
    
    // 清理
    thread_pool_destroy(pool);
    free(results);
    free(config.datasets);
    
    return 0;
}
