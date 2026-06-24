#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <stdbool.h>
#include <unistd.h>
#include <sys/time.h>
#include <errno.h>
#include <sys/types.h>   
#include <aio.h>
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
#define MAX_DATASETS 100
#define MAX_DATASET_NAME 50

// 数据集结构
typedef struct {
    char name[MAX_DATASET_NAME];
    char filename[20];
    char directory[MAX_PATH_LEN];
    uint64_t file_size;  // bytes
    int write_flag;      // 0: read, 1: write
    int random_flag;     // 0: sequential, 1: random
} dataset_t;

// 命令行参数结构
typedef struct {
    char dataset_file[MAX_PATH_LEN];  // 数据集文件路径
    int runtime;                      // 运行时间(秒)
    int thread_num;                   // 总线程数
    int thread_per_dataset;           // 每个数据集分配的线程数
    int num_datasets;                 // 数据集数量
    dataset_t *datasets;              // 数据集数组
} command_t;

// 线程参数结构
struct thread_args {
    int dataset_id;      // 数据集ID
    int thread_id;       // 线程ID
    int runtime;         // 运行时间(秒)
    uint64_t file_size;  // 文件大小
    int write_flag;      // 读写标志
    int random_flag;     // 随机/顺序标志
    char *file_path;     // 文件路径
    int64_t io_bytes;    // IO字节数
    int64_t start_time;  // 开始时间(微秒)
    int64_t end_time;    // 结束时间(微秒)
};

long page_size;
command_t cmd;

// 统一文件大小单位
uint64_t unit_trans(const char *size) {
    const char *sizeUnit[] = {"B", "K", "M", "G", "T"};
    char *endptr;
    double value = strtod(size, &endptr);
    
    if (endptr == size) {
        fprintf(stderr, "Invalid size format: %s\n", size);
        exit(EXIT_FAILURE);
    }
    
    int kiloExp = 0;
    int unit_len = sizeof(sizeUnit) / sizeof(*sizeUnit);
    
    for (int i = 0; i < unit_len; i++) {
        if (strcasecmp(endptr, sizeUnit[i]) == 0) {
            kiloExp = i;
            break;
        }
    }
    
    // 如果没有单位，默认为B
    if (strlen(endptr) == 0) {
        kiloExp = 0;
    }
    
    return (uint64_t)(value * pow(1024, kiloExp));
}

// 解析数据集文件
int parse_dataset_file(const char *filename) {
    FILE *fp = fopen(filename, "r");
    if (!fp) {
        fprintf(stderr, "Failed to open dataset file: %s\n", filename);
        return -1;
    }
    
    char line[512];
    int dataset_count = 0;
    
    // 第一行可能是标题行，跳过
    if (fgets(line, sizeof(line), fp)) {
        // 检查是否是标题行（包含name,filename等关键词）
        if (strstr(line, "name") || strstr(line, "filename") || 
            strstr(line, "directory") || strstr(line, "filesize") ||
            strstr(line, "operation") || strstr(line, "pattern")) {
            // 这是标题行，跳过
        } else {
            // 不是标题行，回退
            fseek(fp, 0, SEEK_SET);
        }
    }
    
    while (fgets(line, sizeof(line), fp) && dataset_count < MAX_DATASETS) {
        // 跳过空行和注释行
        if (line[0] == '\n' || line[0] == '#' || line[0] == '\0')
            continue;
        
        char name[MAX_DATASET_NAME], filename[20], directory[MAX_PATH_LEN];
        char filesize[20], operation[10], pattern[10];
        
        // 解析CSV格式：name,filename,directory,filesize,operation,pattern
        int parsed = sscanf(line, "%[^,],%[^,],%[^,],%[^,],%[^,],%[^,\n]",
                           name, filename, directory, filesize, operation, pattern);
        
        if (parsed == 6) {
            strncpy(cmd.datasets[dataset_count].name, name, MAX_DATASET_NAME-1);
            strncpy(cmd.datasets[dataset_count].filename, filename, 19);
            strncpy(cmd.datasets[dataset_count].directory, directory, MAX_PATH_LEN-1);
            
            cmd.datasets[dataset_count].file_size = unit_trans(filesize);
            
            if (strcmp(operation, "write") == 0 || strcmp(operation, "w") == 0) {
                cmd.datasets[dataset_count].write_flag = 1;
            } else {
                cmd.datasets[dataset_count].write_flag = 0;
            }
            
            if (strcmp(pattern, "random") == 0 || strcmp(pattern, "rand") == 0) {
                cmd.datasets[dataset_count].random_flag = 1;
            } else {
                cmd.datasets[dataset_count].random_flag = 0;
            }
            
            dataset_count++;
        }
    }
    
    fclose(fp);
    return dataset_count;
}

// 配置参数
void params_parse(int argc, char *argv[]) {
    const char *optstr = "d:t:p:f:h";
    struct option opts[] = {
        {"dataset", 1, NULL, 'd'},
        {"threads", 1, NULL, 't'},
        {"threads-per-dataset", 1, NULL, 'p'},
        {"runtime", 1, NULL, 'f'},
        {"help", 0, NULL, 'h'},
        {0, 0, 0, 0}
    };
    
    // 设置默认值
    strcpy(cmd.dataset_file, "");
    cmd.runtime = 10;
    cmd.thread_num = 4;
    cmd.thread_per_dataset = 1;
    
    int opt;
    while ((opt = getopt_long(argc, argv, optstr, opts, NULL)) != -1) {
        switch(opt) {
            case 'd':
                strncpy(cmd.dataset_file, optarg, MAX_PATH_LEN-1);
                break;
            case 't':
                cmd.thread_num = atoi(optarg);
                if (cmd.thread_num <= 0) {
                    fprintf(stderr, "Thread number must be positive\n");
                    exit(EXIT_FAILURE);
                }
                break;
            case 'p':
                cmd.thread_per_dataset = atoi(optarg);
                if (cmd.thread_per_dataset <= 0) {
                    fprintf(stderr, "Threads per dataset must be positive\n");
                    exit(EXIT_FAILURE);
                }
                break;
            case 'f':
                cmd.runtime = atoi(optarg);
                if (cmd.runtime <= 0) {
                    fprintf(stderr, "Runtime must be positive\n");
                    exit(EXIT_FAILURE);
                }
                break;
            case 'h':
                printf("Usage: %s [OPTIONS]\n", argv[0]);
                printf("Options:\n");
                printf("  -d, --dataset FILE           Dataset configuration file (required)\n");
                printf("  -t, --threads N              Total number of threads (default: 4)\n");
                printf("  -p, --threads-per-dataset N  Threads per dataset (default: 1)\n");
                printf("  -f, --runtime SECONDS        Runtime in seconds (default: 10)\n");
                printf("  -h, --help                   Show this help message\n");
                printf("\nDataset file format (CSV):\n");
                printf("  name,filename,directory,filesize,operation,pattern\n");
                printf("  Example: dataset1,test1.dat,/tmp/data,1G,write,sequential\n");
                printf("           dataset2,test2.dat,/tmp/data,2G,read,random\n");
                exit(EXIT_SUCCESS);
        }
    }
    
    // 检查必需参数
    if (strlen(cmd.dataset_file) == 0) {
        fprintf(stderr, "Dataset file is required. Use -d or --dataset option.\n");
        exit(EXIT_FAILURE);
    }
    
    // 分配数据集内存
    cmd.datasets = (dataset_t *)malloc(MAX_DATASETS * sizeof(dataset_t));
    if (!cmd.datasets) {
        fprintf(stderr, "Failed to allocate memory for datasets\n");
        exit(EXIT_FAILURE);
    }
    
    // 解析数据集文件
    cmd.num_datasets = parse_dataset_file(cmd.dataset_file);
    if (cmd.num_datasets <= 0) {
        fprintf(stderr, "No valid datasets found in file: %s\n", cmd.dataset_file);
        free(cmd.datasets);
        exit(EXIT_FAILURE);
    }
    
    // 自动计算线程分配
    if (cmd.thread_per_dataset <= 0) {
        cmd.thread_per_dataset = cmd.thread_num / cmd.num_datasets;
        if (cmd.thread_per_dataset < 1) cmd.thread_per_dataset = 1;
    }
    
    printf("Configuration:\n");
    printf("  Dataset file: %s\n", cmd.dataset_file);
    printf("  Number of datasets: %d\n", cmd.num_datasets);
    printf("  Total threads: %d\n", cmd.thread_num);
    printf("  Threads per dataset: %d\n", cmd.thread_per_dataset);
    printf("  Runtime: %d seconds\n", cmd.runtime);
}

// 创建文件
char* create_file(int dataset_id) {
    char *dest = (char *)malloc(MAX_PATH_LEN);
    if (!dest) return NULL;
    
    memset(dest, 0, MAX_PATH_LEN);
    snprintf(dest, MAX_PATH_LEN, "%s/%s", 
             cmd.datasets[dataset_id].directory,
             cmd.datasets[dataset_id].filename);
    
    // 检查目录是否存在，不存在则创建
    char *dir_end = strrchr(dest, '/');
    if (dir_end) {
        *dir_end = '\0';
        mkdir(dest, 0755);
        *dir_end = '/';
    }
    
    int fd = open(dest, O_CREAT | O_WRONLY | O_TRUNC, 0644);
    if (fd == -1) {
        fprintf(stderr, "Failed to create file: %s, error: %s\n", dest, strerror(errno));
        free(dest);
        return NULL;
    }
    
    char *buf = (char *)malloc(BLOCK_SIZE);
    if (!buf) {
        close(fd);
        free(dest);
        return NULL;
    }
    
    // 填充测试数据
    memset(buf, 'T', BLOCK_SIZE);
    
    uint64_t bytes_written = 0;
    while (bytes_written < cmd.datasets[dataset_id].file_size) {
        size_t to_write = BLOCK_SIZE;
        if (bytes_written + to_write > cmd.datasets[dataset_id].file_size) {
            to_write = cmd.datasets[dataset_id].file_size - bytes_written;
        }
        
        ssize_t written = write(fd, buf, to_write);
        if (written <= 0) {
            fprintf(stderr, "Write failed: %s\n", strerror(errno));
            break;
        }
        bytes_written += written;
    }
    
    free(buf);
    close(fd);
    
    if (bytes_written < cmd.datasets[dataset_id].file_size) {
        fprintf(stderr, "Only wrote %lu bytes out of %lu\n", 
                bytes_written, cmd.datasets[dataset_id].file_size);
        free(dest);
        return NULL;
    }
    
    return dest;
}

// 顺序写操作
void* disk_seq_write(void *args) {
    struct thread_args *targs = (struct thread_args *)args;
    char *file_path = targs->file_path;
    
    int fd = open(file_path, O_WRONLY);
    if (fd == -1) {
        fprintf(stderr, "Failed to open file for writing: %s\n", file_path);
        pthread_exit(NULL);
    }
    
    char *buffer = (char *)malloc(BLOCK_SIZE);
    if (!buffer) {
        close(fd);
        pthread_exit(NULL);
    }
    memset(buffer, 'W', BLOCK_SIZE);
    
    struct timeval start, stop;
    gettimeofday(&start, NULL);
    targs->start_time = start.tv_sec * 1000000 + start.tv_usec;
    
    int64_t bytes_written = 0;
    uint64_t pass = 0;
    
    while (pass < (targs->runtime * 1000000)) {
        ssize_t written = write(fd, buffer, BLOCK_SIZE);
        if (written > 0) {
            bytes_written += written;
        }
        
        gettimeofday(&stop, NULL);
        pass = ((stop.tv_sec - start.tv_sec) * 1000000 + 
                (stop.tv_usec - start.tv_usec));
        
        // 如果到达文件末尾，回到开头
        if (lseek(fd, 0, SEEK_CUR) >= targs->file_size) {
            lseek(fd, 0, SEEK_SET);
        }
    }
    
    targs->end_time = stop.tv_sec * 1000000 + stop.tv_usec;
    targs->io_bytes = bytes_written;
    
    free(buffer);
    close(fd);
    pthread_exit(NULL);
}

// 随机写操作
void* disk_rand_write(void *args) {
    struct thread_args *targs = (struct thread_args *)args;
    char *file_path = targs->file_path;
    
    int fd = open(file_path, O_WRONLY);
    if (fd == -1) {
        fprintf(stderr, "Failed to open file for writing: %s\n", file_path);
        pthread_exit(NULL);
    }
    
    char *buffer = (char *)malloc(BLOCK_SIZE);
    if (!buffer) {
        close(fd);
        pthread_exit(NULL);
    }
    memset(buffer, 'W', BLOCK_SIZE);
    
    struct timeval start, stop;
    gettimeofday(&start, NULL);
    targs->start_time = start.tv_sec * 1000000 + start.tv_usec;
    
    int64_t bytes_written = 0;
    uint64_t pass = 0;
    
    srand(time(NULL) ^ pthread_self());
    
    while (pass < (targs->runtime * 1000000)) {
        // 计算随机偏移
        uint64_t max_offset = (targs->file_size > BLOCK_SIZE) ? 
                              targs->file_size - BLOCK_SIZE : 0;
        if (max_offset > 0) {
            uint64_t offset = rand() % (max_offset / page_size) * page_size;
            lseek(fd, offset, SEEK_SET);
        }
        
        ssize_t written = write(fd, buffer, BLOCK_SIZE);
        if (written > 0) {
            bytes_written += written;
        }
        
        gettimeofday(&stop, NULL);
        pass = ((stop.tv_sec - start.tv_sec) * 1000000 + 
                (stop.tv_usec - start.tv_usec));
    }
    
    targs->end_time = stop.tv_sec * 1000000 + stop.tv_usec;
    targs->io_bytes = bytes_written;
    
    free(buffer);
    close(fd);
    pthread_exit(NULL);
}

// 顺序读操作
void* disk_seq_read(void *args) {
    struct thread_args *targs = (struct thread_args *)args;
    char *file_path = targs->file_path;
    
    int fd = open(file_path, O_RDONLY);
    if (fd == -1) {
        fprintf(stderr, "Failed to open file for reading: %s\n", file_path);
        pthread_exit(NULL);
    }
    
    char *buffer = (char *)malloc(BLOCK_SIZE);
    if (!buffer) {
        close(fd);
        pthread_exit(NULL);
    }
    
    struct timeval start, stop;
    gettimeofday(&start, NULL);
    targs->start_time = start.tv_sec * 1000000 + start.tv_usec;
    
    int64_t bytes_read = 0;
    uint64_t pass = 0;
    
    while (pass < (targs->runtime * 1000000)) {
        ssize_t nread = read(fd, buffer, BLOCK_SIZE);
        if (nread > 0) {
            bytes_read += nread;
        }
        
        gettimeofday(&stop, NULL);
        pass = ((stop.tv_sec - start.tv_sec) * 1000000 + 
                (stop.tv_usec - start.tv_usec));
        
        // 如果到达文件末尾，回到开头
        if (lseek(fd, 0, SEEK_CUR) >= targs->file_size) {
            lseek(fd, 0, SEEK_SET);
        }
    }
    
    targs->end_time = stop.tv_sec * 1000000 + stop.tv_usec;
    targs->io_bytes = bytes_read;
    
    free(buffer);
    close(fd);
    pthread_exit(NULL);
}

// 随机读操作
void* disk_rand_read(void *args) {
    struct thread_args *targs = (struct thread_args *)args;
    char *file_path = targs->file_path;
    
    int fd = open(file_path, O_RDONLY);
    if (fd == -1) {
        fprintf(stderr, "Failed to open file for reading: %s\n", file_path);
        pthread_exit(NULL);
    }
    
    char *buffer = (char *)malloc(BLOCK_SIZE);
    if (!buffer) {
        close(fd);
        pthread_exit(NULL);
    }
    
    struct timeval start, stop;
    gettimeofday(&start, NULL);
    targs->start_time = start.tv_sec * 1000000 + start.tv_usec;
    
    int64_t bytes_read = 0;
    uint64_t pass = 0;
    
    srand(time(NULL) ^ pthread_self());
    
    while (pass < (targs->runtime * 1000000)) {
        // 计算随机偏移
        uint64_t max_offset = (targs->file_size > BLOCK_SIZE) ? 
                              targs->file_size - BLOCK_SIZE : 0;
        if (max_offset > 0) {
            uint64_t offset = rand() % (max_offset / page_size) * page_size;
            lseek(fd, offset, SEEK_SET);
        }
        
        ssize_t nread = read(fd, buffer, BLOCK_SIZE);
        if (nread > 0) {
            bytes_read += nread;
        }
        
        gettimeofday(&stop, NULL);
        pass = ((stop.tv_sec - start.tv_sec) * 1000000 + 
                (stop.tv_usec - start.tv_usec));
    }
    
    targs->end_time = stop.tv_sec * 1000000 + stop.tv_usec;
    targs->io_bytes = bytes_read;
    
    free(buffer);
    close(fd);
    pthread_exit(NULL);
}

// 执行IO测试
void execute_io_test() {
    pthread_t *threads = NULL;
    struct thread_args *thread_args = NULL;
    char **file_paths = NULL;
    
    // 为所有数据集创建文件
    file_paths = (char **)malloc(cmd.num_datasets * sizeof(char *));
    for (int i = 0; i < cmd.num_datasets; i++) {
        printf("Creating file for dataset '%s'...\n", cmd.datasets[i].name);
        file_paths[i] = create_file(i);
        if (!file_paths[i]) {
            fprintf(stderr, "Failed to create file for dataset %d\n", i);
            exit(EXIT_FAILURE);
        }
    }
    
    // 计算总线程数
    int total_threads = cmd.num_datasets * cmd.thread_per_dataset;
    if (total_threads > cmd.thread_num) {
        total_threads = cmd.thread_num;
    }
    
    printf("Starting IO test with %d threads...\n", total_threads);
    
    // 分配线程和参数内存
    threads = (pthread_t *)malloc(total_threads * sizeof(pthread_t));
    thread_args = (struct thread_args *)malloc(total_threads * sizeof(struct thread_args));
    
    // 创建线程
    int thread_idx = 0;
    for (int dataset_id = 0; dataset_id < cmd.num_datasets && thread_idx < total_threads; dataset_id++) {
        for (int t = 0; t < cmd.thread_per_dataset && thread_idx < total_threads; t++) {
            thread_args[thread_idx].dataset_id = dataset_id;
            thread_args[thread_idx].thread_id = t;
            thread_args[thread_idx].runtime = cmd.runtime;
            thread_args[thread_idx].file_size = cmd.datasets[dataset_id].file_size;
            thread_args[thread_idx].write_flag = cmd.datasets[dataset_id].write_flag;
            thread_args[thread_idx].random_flag = cmd.datasets[dataset_id].random_flag;
            thread_args[thread_idx].file_path = file_paths[dataset_id];
            thread_args[thread_idx].io_bytes = 0;
            
            // 选择适当的IO函数
            void *(*io_func)(void *) = NULL;
            if (cmd.datasets[dataset_id].write_flag) {
                if (cmd.datasets[dataset_id].random_flag) {
                    io_func = disk_rand_write;
                } else {
                    io_func = disk_seq_write;
                }
            } else {
                if (cmd.datasets[dataset_id].random_flag) {
                    io_func = disk_rand_read;
                } else {
                    io_func = disk_seq_read;
                }
            }
            
            if (pthread_create(&threads[thread_idx], NULL, io_func, &thread_args[thread_idx]) != 0) {
                fprintf(stderr, "Failed to create thread %d\n", thread_idx);
                exit(EXIT_FAILURE);
            }
            
            thread_idx++;
        }
    }
    
    // 等待所有线程完成
    for (int i = 0; i < thread_idx; i++) {
        pthread_join(threads[i], NULL);
    }
    
    // 收集结果并输出
    printf("\n=================== Results ===================\n");
    
    for (int dataset_id = 0; dataset_id < cmd.num_datasets; dataset_id++) {
        int64_t total_bytes = 0;
        int64_t min_start = INT64_MAX;
        int64_t max_end = 0;
        int thread_count = 0;
        
        for (int i = 0; i < thread_idx; i++) {
            if (thread_args[i].dataset_id == dataset_id) {
                total_bytes += thread_args[i].io_bytes;
                if (thread_args[i].start_time < min_start) {
                    min_start = thread_args[i].start_time;
                }
                if (thread_args[i].end_time > max_end) {
                    max_end = thread_args[i].end_time;
                }
                thread_count++;
            }
        }
        
        if (thread_count > 0) {
            double elapsed_seconds = (max_end - min_start) / 1000000.0;
            double bandwidth = (total_bytes / elapsed_seconds) / (1024.0 * 1024.0); // MB/s
            
            printf("Dataset: %s\n", cmd.datasets[dataset_id].name);
            printf("  Operation: %s %s\n", 
                   cmd.datasets[dataset_id].write_flag ? "Write" : "Read",
                   cmd.datasets[dataset_id].random_flag ? "Random" : "Sequential");
            printf("  Threads: %d\n", thread_count);
            printf("  Time elapsed: %.2f seconds\n", elapsed_seconds);
            printf("  Total IO: %.2f MB\n", total_bytes / (1024.0 * 1024.0));
            printf("  Bandwidth: %.2f MB/s\n", bandwidth);
            printf("  IOPS: %.2f\n", (total_bytes / BLOCK_SIZE) / elapsed_seconds);
            printf("\n");
        }
    }
    
    // 清理资源
    free(threads);
    free(thread_args);
    
    for (int i = 0; i < cmd.num_datasets; i++) {
        free(file_paths[i]);
    }
    free(file_paths);
}

int main(int argc, char *argv[]) {
    page_size = sysconf(_SC_PAGE_SIZE);
    
    params_parse(argc, argv);
    
    execute_io_test();
    
    // 清理
    free(cmd.datasets);
    
    return 0;
}