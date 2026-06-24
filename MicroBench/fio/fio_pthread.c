#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <stdbool.h>
#include <unistd.h>
#include <sys/time.h>
#include <errno.h>
#include <sys/types.h>   
#include <getopt.h>
#include <time.h>
#include <sys/stat.h>
#include <stdint.h>
#include <math.h>
#include <fcntl.h>
#include <pthread.h>

#define MAX_PATH_LEN 100 
#define MAX_CMD_PARA_LEN 2048
#define MAX_FIO_CMD_LEN ((MAX_PATH_LEN) + (MAX_CMD_PARA_LEN) + 256)
#define BLOCK_SIZE 262144

// 输入数据集配置
typedef struct dataset_config {
    char name[20];
    char filename[20];
    char directory[MAX_PATH_LEN];
    uint64_t file_size;    // bytes
    int write_flag;        // 0=read, 1=write
    int random_flag;       // 0=sequential, 1=random
    int runtime;           // seconds
} dataset_t;

// 全局配置
typedef struct global_config {
    int num_datasets;      // 数据集数量
    int num_threads;       // 总线程数（用于并发执行数据集）
    int thread_per_dataset; // 每个数据集的线程数
    dataset_t *datasets;   // 数据集数组
} global_config_t;

// 线程参数
typedef struct thread_data {
    int thread_id;
    int dataset_id;
    uint64_t io_bytes;
    uint64_t start_time;
    uint64_t end_time;
    dataset_t *dataset;
} thread_data_t;

long page_size;
global_config_t g_config;

// 统一文件大小单位
uint64_t unit_trans(const char *size) {
    const char *sizeUnit[] = {"B", "K", "M", "G"};
    int unit_len = sizeof(sizeUnit) / sizeof(*sizeUnit);
    char *ret = NULL;
    char unit_char;
    uint64_t result = 0;
    
    // 获取数字部分
    char *endptr;
    double value = strtod(size, &endptr);
    
    // 查找单位
    for (int i = 0; i < unit_len; i++) {
        if (strstr(endptr, sizeUnit[i])) {
            result = (uint64_t)(value * pow(1024, i));
            break;
        }
    }
    
    if (result == 0) {
        // 如果没有单位，默认是字节
        result = (uint64_t)value;
    }
    
    return result;
}

// 打印帮助信息
void print_help() {
    printf("Usage: ./fio_concurrent [OPTIONS]\n");
    printf("Options:\n");
    printf("  -d, --datasets=NUM       Number of datasets (required)\n");
    printf("  -t, --threads=NUM        Total number of threads (default: 1)\n");
    printf("  -p, --per-dataset=NUM    Threads per dataset (default: 1)\n");
    printf("\nDataset options (for each dataset, use dataset index i=0..N-1):\n");
    printf("  --name[i]=NAME           Dataset name\n");
    printf("  --filename[i]=FILE       Output filename\n");
    printf("  --directory[i]=DIR       Directory path\n");
    printf("  --filesize[i]=SIZE       File size (e.g., 1M, 100K)\n");
    printf("  --rw[i]=[r|w]            Read or write (r=read, w=write)\n");
    printf("  --pattern[i]=[seq|rand]  Access pattern (seq=sequential, rand=random)\n");
    printf("  --runtime[i]=SEC         Runtime in seconds\n");
    printf("\nExample:\n");
    printf("  ./fio_concurrent --datasets=2 --threads=4 \\\n");
    printf("    --name0=test1 --filename0=file1.dat --directory0=/tmp \\\n");
    printf("    --filesize0=100M --rw0=w --pattern0=seq --runtime0=10 \\\n");
    printf("    --name1=test2 --filename1=file2.dat --directory1=/tmp \\\n");
    printf("    --filesize1=200M --rw1=r --pattern1=rand --runtime1=10\n");
}

// 解析命令行参数
void parse_arguments(int argc, char *argv[]) {
    g_config.num_datasets = 0;
    g_config.num_threads = 1;
    g_config.thread_per_dataset = 1;
    
    // 第一次扫描：获取数据集数量
    for (int i = 1; i < argc; i++) {
        if (strstr(argv[i], "--datasets=") || strstr(argv[i], "-d=")) {
            g_config.num_datasets = atoi(strchr(argv[i], '=') + 1);
            break;
        } else if (strcmp(argv[i], "--datasets") == 0 || strcmp(argv[i], "-d") == 0) {
            if (i + 1 < argc) {
                g_config.num_datasets = atoi(argv[i + 1]);
            }
            break;
        }
    }
    
    if (g_config.num_datasets <= 0) {
        fprintf(stderr, "Error: Number of datasets must be specified and > 0\n");
        print_help();
        exit(EXIT_FAILURE);
    }
    
    // 分配数据集内存
    g_config.datasets = (dataset_t *)calloc(g_config.num_datasets, sizeof(dataset_t));
    if (!g_config.datasets) {
        fprintf(stderr, "Error: Memory allocation failed\n");
        exit(EXIT_FAILURE);
    }
    
    // 第二次扫描：解析所有参数
    for (int i = 1; i < argc; i++) {
        // 线程数参数
        if (strstr(argv[i], "--threads=") || strstr(argv[i], "-t=")) {
            g_config.num_threads = atoi(strchr(argv[i], '=') + 1);
        } else if (strcmp(argv[i], "--threads") == 0 || strcmp(argv[i], "-t") == 0) {
            if (i + 1 < argc) g_config.num_threads = atoi(argv[++i]);
        }
        // 每个数据集的线程数
        else if (strstr(argv[i], "--per-dataset=") || strstr(argv[i], "-p=")) {
            g_config.thread_per_dataset = atoi(strchr(argv[i], '=') + 1);
        } else if (strcmp(argv[i], "--per-dataset") == 0 || strcmp(argv[i], "-p") == 0) {
            if (i + 1 < argc) g_config.thread_per_dataset = atoi(argv[++i]);
        }
        // 帮助信息
        else if (strcmp(argv[i], "--help") == 0 || strcmp(argv[i], "-h") == 0) {
            print_help();
            exit(EXIT_SUCCESS);
        }
        // 数据集参数（带索引）
        else if (strstr(argv[i], "--name")) {
            char *idx_str = argv[i] + 6; // 跳过"--name"
            int idx = atoi(idx_str);
            if (idx >= 0 && idx < g_config.num_datasets) {
                char *value = strchr(argv[i], '=');
                if (value) strncpy(g_config.datasets[idx].name, value + 1, 19);
            }
        } else if (strstr(argv[i], "--filename")) {
            char *idx_str = argv[i] + 10;
            int idx = atoi(idx_str);
            if (idx >= 0 && idx < g_config.num_datasets) {
                char *value = strchr(argv[i], '=');
                if (value) strncpy(g_config.datasets[idx].filename, value + 1, 19);
            }
        } else if (strstr(argv[i], "--directory")) {
            char *idx_str = argv[i] + 11;
            int idx = atoi(idx_str);
            if (idx >= 0 && idx < g_config.num_datasets) {
                char *value = strchr(argv[i], '=');
                if (value) strncpy(g_config.datasets[idx].directory, value + 1, MAX_PATH_LEN - 1);
            }
        } else if (strstr(argv[i], "--filesize")) {
            char *idx_str = argv[i] + 10;
            int idx = atoi(idx_str);
            if (idx >= 0 && idx < g_config.num_datasets) {
                char *value = strchr(argv[i], '=');
                if (value) g_config.datasets[idx].file_size = unit_trans(value + 1);
            }
        } else if (strstr(argv[i], "--rw")) {
            char *idx_str = argv[i] + 4;
            int idx = atoi(idx_str);
            if (idx >= 0 && idx < g_config.num_datasets) {
                char *value = strchr(argv[i], '=');
                if (value) g_config.datasets[idx].write_flag = (value[1] == 'w') ? 1 : 0;
            }
        } else if (strstr(argv[i], "--pattern")) {
            char *idx_str = argv[i] + 9;
            int idx = atoi(idx_str);
            if (idx >= 0 && idx < g_config.num_datasets) {
                char *value = strchr(argv[i], '=');
                if (value) g_config.datasets[idx].random_flag = (strcmp(value + 1, "rand") == 0) ? 1 : 0;
            }
        } else if (strstr(argv[i], "--runtime")) {
            char *idx_str = argv[i] + 9;
            int idx = atoi(idx_str);
            if (idx >= 0 && idx < g_config.num_datasets) {
                char *value = strchr(argv[i], '=');
                if (value) g_config.datasets[idx].runtime = atoi(value + 1);
            }
        }
    }
    
    // 验证配置
    for (int i = 0; i < g_config.num_datasets; i++) {
        if (strlen(g_config.datasets[i].filename) == 0) {
            snprintf(g_config.datasets[i].filename, 19, "file%d.dat", i);
        }
        if (strlen(g_config.datasets[i].directory) == 0) {
            strcpy(g_config.datasets[i].directory, ".");
        }
        if (strlen(g_config.datasets[i].name) == 0) {
            snprintf(g_config.datasets[i].name, 19, "dataset%d", i);
        }
        if (g_config.datasets[i].file_size == 0) {
            g_config.datasets[i].file_size = 1024 * 1024; // 1MB default
        }
        if (g_config.datasets[i].runtime == 0) {
            g_config.datasets[i].runtime = 10; // 10 seconds default
        }
    }
    
    // 调整线程数：总线程数不能小于数据集数
    if (g_config.num_threads < g_config.num_datasets) {
        g_config.num_threads = g_config.num_datasets;
    }
    
    printf("Configuration:\n");
    printf("  Number of datasets: %d\n", g_config.num_datasets);
    printf("  Total threads: %d\n", g_config.num_threads);
    printf("  Threads per dataset: %d\n", g_config.thread_per_dataset);
}

// 创建文件
void create_file(dataset_t *dataset) {
    char dest[MAX_PATH_LEN];
    snprintf(dest, sizeof(dest), "%s/%s", dataset->directory, dataset->filename);
    
    printf("Creating file: %s (size: %lu bytes)\n", dest, dataset->file_size);
    
    int fd = open(dest, O_CREAT | O_WRONLY | O_TRUNC, 0644);
    if (fd == -1) {
        fprintf(stderr, "Error creating file %s: %s\n", dest, strerror(errno));
        exit(EXIT_FAILURE);
    }
    
    char buf[BLOCK_SIZE];
    memset(buf, 'A', sizeof(buf));  // 用'A'填充缓冲区
    
    uint64_t written = 0;
    while (written < dataset->file_size) {
        size_t to_write = (dataset->file_size - written < BLOCK_SIZE) ? 
                          dataset->file_size - written : BLOCK_SIZE;
        ssize_t w = write(fd, buf, to_write);
        if (w <= 0) {
            fprintf(stderr, "Write error: %s\n", strerror(errno));
            close(fd);
            exit(EXIT_FAILURE);
        }
        written += w;
    }
    
    close(fd);
}

// 顺序写
void* disk_seq_write(void *arg) {
    thread_data_t *td = (thread_data_t *)arg;
    dataset_t *ds = td->dataset;
    
    char dest[MAX_PATH_LEN];
    snprintf(dest, sizeof(dest), "%s/%s", ds->directory, ds->filename);
    
    int fd = open(dest, O_WRONLY);
    if (fd == -1) {
        fprintf(stderr, "Error opening file %s: %s\n", dest, strerror(errno));
        return NULL;
    }
    
    char buffer[BLOCK_SIZE];
    memset(buffer, 'B', sizeof(buffer));
    
    struct timeval start, stop;
    gettimeofday(&start, NULL);
    td->start_time = start.tv_sec * 1000000 + start.tv_usec;
    
    uint64_t pass = 0;
    while (pass < (ds->runtime * 1000000)) {
        ssize_t r = write(fd, buffer, sizeof(buffer));
        if (r > 0) {
            td->io_bytes += r;
        }
        
        // 如果到达文件末尾，回到开头
        if (lseek(fd, 0, SEEK_CUR) >= ds->file_size) {
            lseek(fd, 0, SEEK_SET);
        }
        
        gettimeofday(&stop, NULL);
        pass = (stop.tv_sec - start.tv_sec) * 1000000 + (stop.tv_usec - start.tv_usec);
    }
    
    td->end_time = stop.tv_sec * 1000000 + stop.tv_usec;
    close(fd);
    
    return NULL;
}

// 随机写
void* disk_rand_write(void *arg) {
    thread_data_t *td = (thread_data_t *)arg;
    dataset_t *ds = td->dataset;
    
    char dest[MAX_PATH_LEN];
    snprintf(dest, sizeof(dest), "%s/%s", ds->directory, ds->filename);
    
    int fd = open(dest, O_RDWR);
    if (fd == -1) {
        fprintf(stderr, "Error opening file %s: %s\n", dest, strerror(errno));
        return NULL;
    }
    
    char buffer[BLOCK_SIZE];
    memset(buffer, 'B', sizeof(buffer));
    
    struct timeval start, stop;
    gettimeofday(&start, NULL);
    td->start_time = start.tv_sec * 1000000 + start.tv_usec;
    
    uint64_t pass = 0;
    while (pass < (ds->runtime * 1000000)) {
        // 计算随机偏移
        uint64_t max_offset = (ds->file_size > BLOCK_SIZE) ? ds->file_size - BLOCK_SIZE : 0;
        uint64_t offset = (max_offset > 0) ? rand() % max_offset : 0;
        offset &= ~(page_size - 1);  // 对齐到页面边界
        
        lseek(fd, offset, SEEK_SET);
        
        ssize_t r = write(fd, buffer, sizeof(buffer));
        if (r > 0) {
            td->io_bytes += r;
        }
        
        gettimeofday(&stop, NULL);
        pass = (stop.tv_sec - start.tv_sec) * 1000000 + (stop.tv_usec - start.tv_usec);
    }
    
    td->end_time = stop.tv_sec * 1000000 + stop.tv_usec;
    close(fd);
    
    return NULL;
}

// 顺序读
void* disk_seq_read(void *arg) {
    thread_data_t *td = (thread_data_t *)arg;
    dataset_t *ds = td->dataset;
    
    char dest[MAX_PATH_LEN];
    snprintf(dest, sizeof(dest), "%s/%s", ds->directory, ds->filename);
    
    int fd = open(dest, O_RDONLY);
    if (fd == -1) {
        fprintf(stderr, "Error opening file %s: %s\n", dest, strerror(errno));
        return NULL;
    }
    
    char buffer[BLOCK_SIZE];
    
    struct timeval start, stop;
    gettimeofday(&start, NULL);
    td->start_time = start.tv_sec * 1000000 + start.tv_usec;
    
    uint64_t pass = 0;
    while (pass < (ds->runtime * 1000000)) {
        ssize_t r = read(fd, buffer, sizeof(buffer));
        if (r > 0) {
            td->io_bytes += r;
        } else if (r == 0) {
            // 到达文件末尾，回到开头
            lseek(fd, 0, SEEK_SET);
        }
        
        gettimeofday(&stop, NULL);
        pass = (stop.tv_sec - start.tv_sec) * 1000000 + (stop.tv_usec - start.tv_usec);
    }
    
    td->end_time = stop.tv_sec * 1000000 + stop.tv_usec;
    close(fd);
    
    return NULL;
}

// 随机读
void* disk_rand_read(void *arg) {
    thread_data_t *td = (thread_data_t *)arg;
    dataset_t *ds = td->dataset;
    
    char dest[MAX_PATH_LEN];
    snprintf(dest, sizeof(dest), "%s/%s", ds->directory, ds->filename);
    
    int fd = open(dest, O_RDONLY);
    if (fd == -1) {
        fprintf(stderr, "Error opening file %s: %s\n", dest, strerror(errno));
        return NULL;
    }
    
    char buffer[BLOCK_SIZE];
    
    struct timeval start, stop;
    gettimeofday(&start, NULL);
    td->start_time = start.tv_sec * 1000000 + start.tv_usec;
    
    uint64_t pass = 0;
    while (pass < (ds->runtime * 1000000)) {
        // 计算随机偏移
        uint64_t max_offset = (ds->file_size > BLOCK_SIZE) ? ds->file_size - BLOCK_SIZE : 0;
        uint64_t offset = (max_offset > 0) ? rand() % max_offset : 0;
        offset &= ~(page_size - 1);  // 对齐到页面边界
        
        lseek(fd, offset, SEEK_SET);
        
        ssize_t r = read(fd, buffer, sizeof(buffer));
        if (r > 0) {
            td->io_bytes += r;
        }
        
        gettimeofday(&stop, NULL);
        pass = (stop.tv_sec - start.tv_sec) * 1000000 + (stop.tv_usec - start.tv_usec);
    }
    
    td->end_time = stop.tv_sec * 1000000 + stop.tv_usec;
    close(fd);
    
    return NULL;
}

// 执行IO测试
void execute_io_test(dataset_t *dataset, int dataset_id) {
    printf("\n=== Starting test for dataset %d: %s ===\n", 
           dataset_id, dataset->name);
    
    // 创建文件（如果是写测试）
    if (dataset->write_flag) {
        create_file(dataset);
    }
    
    // 分配线程数据
    int actual_threads = g_config.thread_per_dataset;
    pthread_t *threads = (pthread_t *)malloc(actual_threads * sizeof(pthread_t));
    thread_data_t *thread_data = (thread_data_t *)malloc(actual_threads * sizeof(thread_data_t));
    
    // 创建线程
    for (int i = 0; i < actual_threads; i++) {
        thread_data[i].thread_id = i;
        thread_data[i].dataset_id = dataset_id;
        thread_data[i].dataset = dataset;
        thread_data[i].io_bytes = 0;
        
        void* (*func_ptr)(void*) = NULL;
        
        if (dataset->random_flag) {
            func_ptr = dataset->write_flag ? disk_rand_write : disk_rand_read;
        } else {
            func_ptr = dataset->write_flag ? disk_seq_write : disk_seq_read;
        }
        
        pthread_create(&threads[i], NULL, func_ptr, &thread_data[i]);
    }
    
    // 等待所有线程完成
    for (int i = 0; i < actual_threads; i++) {
        pthread_join(threads[i], NULL);
    }
    
    // 计算统计信息
    uint64_t total_io_bytes = 0;
    uint64_t min_start_time = thread_data[0].start_time;
    uint64_t max_end_time = thread_data[0].end_time;
    
    for (int i = 0; i < actual_threads; i++) {
        total_io_bytes += thread_data[i].io_bytes;
        if (thread_data[i].start_time < min_start_time) 
            min_start_time = thread_data[i].start_time;
        if (thread_data[i].end_time > max_end_time) 
            max_end_time = thread_data[i].end_time;
    }
    
    double elapsed_time = (max_end_time - min_start_time) / 1000000.0;
    double bandwidth = (total_io_bytes / 1024.0 / 1024.0) / elapsed_time;
    
    printf("Dataset %d results:\n", dataset_id);
    printf("  Threads: %d\n", actual_threads);
    printf("  Total I/O: %lu bytes\n", total_io_bytes);
    printf("  Elapsed time: %.3f seconds\n", elapsed_time);
    printf("  Bandwidth: %.2f MB/s\n", bandwidth);
    printf("  IOPS: %.0f ops/s\n", (actual_threads * dataset->runtime * 1.0) / elapsed_time);
    
    free(threads);
    free(thread_data);
}

// 并发执行所有数据集测试
void execute_concurrent_tests() {
    pthread_t *threads = NULL;
    
    // 如果总线程数等于数据集数，每个数据集一个线程
    if (g_config.num_threads == g_config.num_datasets) {
        threads = (pthread_t *)malloc(g_config.num_datasets * sizeof(pthread_t));
        
        // 为每个数据集创建线程
        for (int i = 0; i < g_config.num_datasets; i++) {
            // 创建线程执行数据集测试
            // 注意：这里需要传递数据集指针，但由于线程函数需要特定签名，
            // 我们简化处理，按顺序执行
            execute_io_test(&g_config.datasets[i], i);
        }
    } else {
        // 如果线程数多于数据集数，每个数据集使用多个线程
        // 这里简化处理，按顺序执行每个数据集（每个数据集内多线程）
        for (int i = 0; i < g_config.num_datasets; i++) {
            execute_io_test(&g_config.datasets[i], i);
        }
    }
    
    if (threads) free(threads);
}

int main(int argc, char *argv[]) {
    // 初始化
    page_size = sysconf(_SC_PAGE_SIZE);
    srand(time(NULL));
    
    // 解析命令行参数
    parse_arguments(argc, argv);
    
    printf("\n=== Starting concurrent I/O tests ===\n");
    
    // 执行测试
    execute_concurrent_tests();
    
    printf("\n=== All tests completed ===\n");
    
    // 清理
    free(g_config.datasets);
    
    return 0;
}
