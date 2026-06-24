#include <bits/stdc++.h>
#include <sys/time.h>
#include <pthread.h>
#include <unistd.h>
#include <atomic>
#include <algorithm>
#include <vector>

// 全局变量
int n = 0;                  // 数组大小
int numreps = 0;           // 重复次数
int num_threads = 4;       // 默认线程数
int *I = nullptr;          // 原始输入数组
int *A = nullptr;          // 工作数组

// 命令行参数结构
struct Config {
    std::string input_file = "./input/data.in";
    int threads = 4;
    bool help = false;
};

// 线程参数结构
struct ThreadData {
    int id;                     // 线程ID
    int *arr;                   // 数组指针
    int *temp;                  // 临时数组
    int len;                    // 数组长度
    int total_threads;         // 总线程数
    int start_idx;             // 起始索引
    int end_idx;               // 结束索引（不包含）
};

// 屏障同步
pthread_barrier_t barrier;

// 打印使用说明
void print_usage(const char* prog_name) {
    printf("Usage: %s [OPTIONS]\n", prog_name);
    printf("Options:\n");
    printf("  -i FILE     Input file path (default: ./input/data.in)\n");
    printf("  -t NUM      Number of threads (default: 4)\n");
    printf("  -h          Show this help message\n");
    printf("\n");
    printf("Input file format:\n");
    printf("  First line: n numreps\n");
    printf("  Following lines: n integers\n");
    printf("\n");
    printf("Example: %s -i ./data/sort.in -t 8\n", prog_name);
}

// 解析命令行参数
Config parse_args(int argc, char* argv[]) {
    Config config;
    int opt;
    
    while ((opt = getopt(argc, argv, "i:t:h")) != -1) {
        switch (opt) {
            case 'i':
                config.input_file = optarg;
                break;
            case 't':
                config.threads = atoi(optarg);
                if (config.threads <= 0) {
                    fprintf(stderr, "Error: Thread count must be positive\n");
                    exit(1);
                }
                break;
            case 'h':
                config.help = true;
                break;
            default:
                fprintf(stderr, "Unknown option: %c\n", opt);
                print_usage(argv[0]);
                exit(1);
        }
    }
    return config;
}

// 串行归并排序函数（修复版本）
void merge_sort_serial(int arr[], int len) {
    if (len <= 1) return;
    
    int *a = arr;
    int *b = new int[len];
    
    for (int seg = 1; seg < len; seg += seg) {
        for (int start = 0; start < len; start += seg + seg) {
            int low = start;
            int mid = std::min(start + seg, len);
            int high = std::min(start + seg + seg, len);
            
            int k = low;
            int start1 = low, end1 = mid;
            int start2 = mid, end2 = high;
            
            while (start1 < end1 && start2 < end2)
                b[k++] = a[start1] < a[start2] ? a[start1++] : a[start2++];
            while (start1 < end1)
                b[k++] = a[start1++];
            while (start2 < end2)
                b[k++] = a[start2++];
        }
        std::swap(a, b);
    }
    
    // 如果最终结果不在原始数组中，复制回去
    if (a != arr) {
        for (int i = 0; i < len; i++) {
            arr[i] = a[i];
        }
        std::swap(a, b);  // 确保b指向临时数组
    }
    
    delete[] b;
}

// 并行归并 - 线程局部排序函数
void* parallel_sort_local(void* arg) {
    ThreadData* data = (ThreadData*)arg;
    
    // 对本地数据进行排序
    std::sort(data->arr + data->start_idx, data->arr + data->end_idx);
    
    return nullptr;
}

// 并行归并 - 归并阶段函数
void* parallel_merge_phase(void* arg) {
    ThreadData* data = (ThreadData*)arg;
    int id = data->id;
    int total_threads = data->total_threads;
    int len = data->len;
    
    // 计算每个线程处理的归并对数量
    int seg_size = len / total_threads;
    
    for (int merge_size = seg_size; merge_size < len; merge_size *= 2) {
        // 每个线程处理一组归并对
        for (int pair_idx = id; pair_idx < len / (2 * merge_size); pair_idx += total_threads) {
            int left_start = 2 * merge_size * pair_idx;
            int left_end = std::min(left_start + merge_size, len);
            int right_end = std::min(left_start + 2 * merge_size, len);
            
            if (left_end >= right_end) continue;
            
            // 归并两个有序段
            int i = left_start, j = left_end, k = left_start;
            
            while (i < left_end && j < right_end) {
                if (data->arr[i] < data->arr[j]) {
                    data->temp[k++] = data->arr[i++];
                } else {
                    data->temp[k++] = data->arr[j++];
                }
            }
            
            while (i < left_end) data->temp[k++] = data->arr[i++];
            while (j < right_end) data->temp[k++] = data->arr[j++];
        }
        
        // 等待所有线程完成当前归并阶段
        pthread_barrier_wait(&barrier);
        
        // 线程0负责交换数组
        if (id == 0) {
            std::swap(data->arr, data->temp);
        }
        
        // 等待交换完成
        pthread_barrier_wait(&barrier);
        
        // 将临时数组的内容复制回原数组位置
        if (id == 0) {
            for (int i = 0; i < len; i++) {
                data->arr[i] = data->temp[i];
            }
        }
        
        // 等待复制完成
        pthread_barrier_wait(&barrier);
    }
    
    return nullptr;
}

// 安全的并行归并排序主函数
void merge_sort_parallel_safe(int arr[], int len, int num_threads) {
    if (num_threads <= 1 || len < 10000) {
        merge_sort_serial(arr, len);
        return;
    }
    
    // 限制线程数量，避免过多线程导致性能下降
    num_threads = std::min(num_threads, len / 1000);
    if (num_threads < 2) {
        merge_sort_serial(arr, len);
        return;
    }
    
    // 创建临时数组
    int* temp = new int[len];
    int* src = arr;
    int* dst = temp;
    
    // 初始化屏障
    pthread_barrier_init(&barrier, nullptr, num_threads);
    
    // 阶段1：每个线程对本地数据进行排序
    pthread_t threads_phase1[num_threads];
    ThreadData thread_data_phase1[num_threads];
    
    int chunk_size = len / num_threads;
    
    for (int i = 0; i < num_threads; i++) {
        thread_data_phase1[i].id = i;
        thread_data_phase1[i].arr = src;
        thread_data_phase1[i].len = len;
        thread_data_phase1[i].total_threads = num_threads;
        thread_data_phase1[i].start_idx = i * chunk_size;
        thread_data_phase1[i].end_idx = (i == num_threads - 1) ? len : (i + 1) * chunk_size;
        
        pthread_create(&threads_phase1[i], nullptr, parallel_sort_local, &thread_data_phase1[i]);
    }
    
    // 等待所有线程完成局部排序
    for (int i = 0; i < num_threads; i++) {
        pthread_join(threads_phase1[i], nullptr);
    }
    
    // 阶段2：并行归并
    pthread_t threads_phase2[num_threads];
    ThreadData thread_data_phase2[num_threads];
    
    for (int i = 0; i < num_threads; i++) {
        thread_data_phase2[i].id = i;
        thread_data_phase2[i].arr = src;
        thread_data_phase2[i].temp = dst;
        thread_data_phase2[i].len = len;
        thread_data_phase2[i].total_threads = num_threads;
        
        pthread_create(&threads_phase2[i], nullptr, parallel_merge_phase, &thread_data_phase2[i]);
    }
    
    // 等待所有线程完成归并
    for (int i = 0; i < num_threads; i++) {
        pthread_join(threads_phase2[i], nullptr);
    }
    
    // 如果最终结果在临时数组中，复制回原数组
    if (src != arr) {
        memcpy(arr, src, len * sizeof(int));
    }
    
    // 清理
    pthread_barrier_destroy(&barrier);
    delete[] temp;
}

// 更简单的并行实现 - 分治策略
void* parallel_merge_sort_dc(void* arg) {
    ThreadData* data = (ThreadData*)arg;
    int start = data->start_idx;
    int end = data->end_idx;
    
    // 对小段数据使用串行排序
    std::sort(data->arr + start, data->arr + end);
    
    return nullptr;
}

// 简单并行归并排序（更稳定）
void merge_sort_parallel_simple(int arr[], int len, int num_threads) {
    if (num_threads <= 1 || len < 10000) {
        merge_sort_serial(arr, len);
        return;
    }
    
    // 限制线程数量
    num_threads = std::min(num_threads, 16);
    num_threads = std::min(num_threads, len / 1000);
    
    // 分配数据到线程
    pthread_t threads[num_threads];
    ThreadData thread_data[num_threads];
    
    int chunk_size = len / num_threads;
    
    // 第一阶段：并行局部排序
    for (int i = 0; i < num_threads; i++) {
        thread_data[i].id = i;
        thread_data[i].arr = arr;
        thread_data[i].len = len;
        thread_data[i].start_idx = i * chunk_size;
        thread_data[i].end_idx = (i == num_threads - 1) ? len : (i + 1) * chunk_size;
        
        pthread_create(&threads[i], nullptr, parallel_merge_sort_dc, &thread_data[i]);
    }
    
    for (int i = 0; i < num_threads; i++) {
        pthread_join(threads[i], nullptr);
    }
    
    // 第二阶段：串行归并（避免复杂的并行归并带来的同步问题）
    // 使用标准归并算法合并各段
    std::vector<int> temp(len);
    int* temp_arr = temp.data();
    
    // 初始段大小为chunk_size
    for (int seg_size = chunk_size; seg_size < len; seg_size = 2 * seg_size) {
        for (int left_start = 0; left_start < len; left_start += 2 * seg_size) {
            int mid = std::min(left_start + seg_size, len);
            int right_end = std::min(left_start + 2 * seg_size, len);
            
            // 归并 [left_start, mid) 和 [mid, right_end)
            int i = left_start, j = mid, k = left_start;
            while (i < mid && j < right_end) {
                if (arr[i] <= arr[j]) {
                    temp_arr[k++] = arr[i++];
                } else {
                    temp_arr[k++] = arr[j++];
                }
            }
            while (i < mid) temp_arr[k++] = arr[i++];
            while (j < right_end) temp_arr[k++] = arr[j++];
            
            // 复制回原数组
            for (int idx = left_start; idx < right_end; idx++) {
                arr[idx] = temp_arr[idx];
            }
        }
    }
}

// 验证排序结果
bool verify_sorted(int arr[], int len) {
    for (int i = 1; i < len; i++) {
        if (arr[i] < arr[i-1]) {
            fprintf(stderr, "Verification failed at index %d: %d < %d\n", 
                    i, arr[i], arr[i-1]);
            return false;
        }
    }
    return true;
}

// 测试并计时函数
double test_sort(void (*sort_func)(int[], int, int), int arr[], int len, int threads, const char* name) {
    struct timeval tv1, tv2;
    gettimeofday(&tv1, nullptr);
    
    sort_func(arr, len, threads);
    
    gettimeofday(&tv2, nullptr);
    
    double elapsed = (double)(tv2.tv_sec - tv1.tv_sec) + 
                    (double)(tv2.tv_usec - tv1.tv_usec) * 1.e-6;
    
    bool sorted = verify_sorted(arr, len);
    printf("  %s: %.6f seconds %s\n", name, elapsed, sorted ? "✓" : "✗");
    
    return elapsed;
}

// 生成测试数据
void generate_test_data(const std::string& filename, int n, int reps) {
    FILE* fp = fopen(filename.c_str(), "w");
    if (!fp) {
        fprintf(stderr, "Error: Cannot create file %s\n", filename.c_str());
        return;
    }
    
    fprintf(fp, "%d %d\n", n, reps);
    
    std::mt19937 rng(time(nullptr));
    std::uniform_int_distribution<int> dist(0, 1000000);
    
    for (int i = 0; i < n; i++) {
        fprintf(fp, "%d", dist(rng));
        if (i % 20 == 19) fprintf(fp, "\n");
        else if (i < n - 1) fprintf(fp, " ");
    }
    if (n % 20 != 0) fprintf(fp, "\n");
    
    fclose(fp);
    printf("Generated test data: %s with %d elements\n", filename.c_str(), n);
}

int main(int argc, char* argv[]) {
    // 解析命令行参数
    Config config = parse_args(argc, argv);
    
    if (config.help) {
        print_usage(argv[0]);
        return 0;
    }
    
    // 检查系统核心数
    int max_threads = sysconf(_SC_NPROCESSORS_ONLN);
    /*if (config.threads > max_threads * 2) {
        printf("Warning: Requested %d threads (more than 2x logical cores)\n", config.threads);
        printf("Setting threads to %d\n", max_threads * 2);
        config.threads = max_threads * 2;
    }*/
    
    printf("========================================\n");
    printf("Parallel Merge Sort Benchmark (Safe Version)\n");
    printf("========================================\n");
    printf("Input file:       %s\n", config.input_file.c_str());
    printf("Threads:          %d\n", config.threads);
    printf("System cores:     %d\n", max_threads);
    
    // 检查输入文件是否存在
    FILE* fp = fopen(config.input_file.c_str(), "r");
    if (!fp) {
        printf("Input file not found. Generating sample data...\n");
        generate_test_data(config.input_file, 1000000, 5);
        fp = fopen(config.input_file.c_str(), "r");
        if (!fp) {
            fprintf(stderr, "Error: Cannot open input file: %s\n", config.input_file.c_str());
            return 1;
        }
    }
    
    // 读取文件头
    if (fscanf(fp, "%d %d", &n, &numreps) != 2) {
        fprintf(stderr, "Error: Invalid input file format\n");
        fclose(fp);
        return 1;
    }
    
    // 分配内存
    I = (int*)malloc(n * sizeof(int));
    A = (int*)malloc(n * sizeof(int));
    
    if (!I || !A) {
        fprintf(stderr, "Error: Memory allocation failed\n");
        if (fp) fclose(fp);
        if (I) free(I);
        if (A) free(A);
        return 1;
    }
    
    // 读取数组数据
    printf("Reading %d integers from input file...\n", n);
    for (int i = 0; i < n; i++) {
        if (fscanf(fp, "%d", &I[i]) != 1) {
            fprintf(stderr, "Error: Failed to read element %d\n", i);
            fclose(fp);
            free(I);
            free(A);
            return 1;
        }
    }
    fclose(fp);
    
    printf("Array size:       %d\n", n);
    printf("Repetitions:      %d\n", numreps);
    printf("========================================\n");
    
    // 创建结果数组
    int* results_serial = new int[n];
    int* results_parallel_simple = new int[n];
    
    // 测试串行排序
    printf("\n1. Testing serial merge sort (1 thread)...\n");
    double total_serial = 0.0;
    int serial_runs = std::min(3, numreps);
    
    for (int rep = 0; rep < serial_runs; rep++) {
        memcpy(results_serial, I, n * sizeof(int));
        total_serial += test_sort([](int arr[], int len, int threads) {
            merge_sort_serial(arr, len);
        }, results_serial, n, 1, "Serial");
    }
    
    printf("  Average serial time: %.6f seconds\n", total_serial / serial_runs);
    
    // 测试简单并行排序（稳定版本）
    printf("\n2. Testing parallel merge sort (%d threads, simple version)...\n", config.threads);
    double total_parallel = 0.0;
    
    for (int rep = 0; rep < numreps; rep++) {
        memcpy(results_parallel_simple, I, n * sizeof(int));
        total_parallel += test_sort(merge_sort_parallel_simple, 
                                   results_parallel_simple, n, 
                                   config.threads, "Parallel");
        
        // 验证排序结果与串行版本一致
        if (rep == 0) {
            bool match = true;
            for (int i = 0; i < n; i++) {
                if (results_parallel_simple[i] != results_serial[i]) {
                    match = false;
                    printf("    Warning: Result mismatch at index %d\n", i);
                    break;
                }
            }
            if (match) {
                printf("    Result verification: ✓ (matches serial sort)\n");
            }
        }
    }
    
    // 输出统计信息
    printf("\n========================================\n");
    printf("Performance Summary\n");
    printf("========================================\n");
    printf("Array size:          %d\n", n);
    printf("Threads used:        %d\n", config.threads);
    printf("System cores:        %d\n", max_threads);
    printf("Serial runs:         %d\n", serial_runs);
    printf("Parallel runs:       %d\n", numreps);
    printf("Average serial time: %.6f seconds\n", total_serial / serial_runs);
    printf("Average parallel time: %.6f seconds\n", total_parallel / numreps);
    
    if (total_parallel > 0) {
        double speedup = (total_serial / serial_runs) / (total_parallel / numreps);
        printf("Speedup:             %.2fx\n", speedup);
        printf("Efficiency:          %.1f%%\n", (speedup / config.threads) * 100);
    }
    
    // 输出部分排序结果用于验证
    printf("\nSample of sorted results (first and last 5 elements):\n");
    printf("First 5:  ");
    for (int i = 0; i < std::min(5, n); i++) {
        printf("%d ", results_parallel_simple[i]);
    }
    printf("\nLast 5:   ");
    for (int i = std::max(0, n - 5); i < n; i++) {
        printf("%d ", results_parallel_simple[i]);
    }
    printf("\n");
    
    // 清理内存
    free(I);
    free(A);
    delete[] results_serial;
    delete[] results_parallel_simple;
    
    return 0;
}
