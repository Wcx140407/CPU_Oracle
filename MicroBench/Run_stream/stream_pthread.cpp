#include <iostream>
#include <pthread.h>
#include <cstdlib>
#include <cstring>
#include <cmath>
#include <sys/time.h>
#include <vector>
#include <algorithm>
#include <unistd.h>
#include <climits>
#include <cfloat>
#include <fstream>
#include <sstream>

using namespace std;

// 全局配置
static int STREAM_ARRAY_SIZE = 10000000;  // 默认数组大小
static int NTIMES = 100;                  // 默认运行次数
static int OFFSET = 0;                    // 偏移量
static int NUM_THREADS = 4;               // 默认线程数

// STREAM数据类型
typedef double STREAM_TYPE;

// 全局数组
static STREAM_TYPE *a = nullptr;
static STREAM_TYPE *b = nullptr;
static STREAM_TYPE *c = nullptr;

// 时间统计
static double avgtime[4] = {0};
static double maxtime[4] = {0};
static double mintime[4] = {FLT_MAX, FLT_MAX, FLT_MAX, FLT_MAX};

// 测试标签
static const char *label[4] = {"Copy:      ", "Scale:     ",
                               "Add:       ", "Triad:     "};

// 数据量（字节）
static double bytes[4];

// 线程参数结构体
struct ThreadData {
    int thread_id;
    size_t start_idx;
    size_t end_idx;
    STREAM_TYPE scalar;
    double *local_times;
    int operation; // 0:Copy, 1:Scale, 2:Add, 3:Triad
};

// 函数声明
double mysecond();
int checktick();
void parseCommandLine(int argc, char *argv[]);
void initializeArrays();
void cleanupArrays();
void loadDataFromFile(const char *filename, STREAM_TYPE *array, size_t size);
void saveDataToFile(const char *filename, STREAM_TYPE *array, size_t size);
void generateRandomData();
void printConfig();

// 测试函数（线程版本）
void *stream_copy(void *arg) {
    ThreadData *data = (ThreadData *)arg;
    size_t start = data->start_idx;
    size_t end = data->end_idx;
    
    double start_time = mysecond();
    for (size_t j = start; j < end; j++) {
        c[j] = a[j];
    }
    double end_time = mysecond();
    
    data->local_times[0] = end_time - start_time;
    pthread_exit(NULL);
}

void *stream_scale(void *arg) {
    ThreadData *data = (ThreadData *)arg;
    size_t start = data->start_idx;
    size_t end = data->end_idx;
    STREAM_TYPE scalar = data->scalar;
    
    double start_time = mysecond();
    for (size_t j = start; j < end; j++) {
        b[j] = scalar * c[j];
    }
    double end_time = mysecond();
    
    data->local_times[1] = end_time - start_time;
    pthread_exit(NULL);
}

void *stream_add(void *arg) {
    ThreadData *data = (ThreadData *)arg;
    size_t start = data->start_idx;
    size_t end = data->end_idx;
    
    double start_time = mysecond();
    for (size_t j = start; j < end; j++) {
        c[j] = a[j] + b[j];
    }
    double end_time = mysecond();
    
    data->local_times[2] = end_time - start_time;
    pthread_exit(NULL);
}

void *stream_triad(void *arg) {
    ThreadData *data = (ThreadData *)arg;
    size_t start = data->start_idx;
    size_t end = data->end_idx;
    STREAM_TYPE scalar = data->scalar;
    
    double start_time = mysecond();
    for (size_t j = start; j < end; j++) {
        a[j] = b[j] + scalar * c[j];
    }
    double end_time = mysecond();
    
    data->local_times[3] = end_time - start_time;
    pthread_exit(NULL);
}

// 并行执行STREAM测试
double parallel_stream_test(int operation, STREAM_TYPE scalar) {
    pthread_t *threads = new pthread_t[NUM_THREADS];
    ThreadData *thread_data = new ThreadData[NUM_THREADS];
    
    // 分配数据块给每个线程
    size_t chunk_size = STREAM_ARRAY_SIZE / NUM_THREADS;
    
    // 创建线程
    for (int t = 0; t < NUM_THREADS; t++) {
        thread_data[t].thread_id = t;
        thread_data[t].start_idx = t * chunk_size;
        thread_data[t].end_idx = (t == NUM_THREADS - 1) ? STREAM_ARRAY_SIZE : (t + 1) * chunk_size;
        thread_data[t].scalar = scalar;
        thread_data[t].local_times = new double[4]();
        thread_data[t].operation = operation;
        
        switch (operation) {
            case 0:
                pthread_create(&threads[t], NULL, stream_copy, &thread_data[t]);
                break;
            case 1:
                pthread_create(&threads[t], NULL, stream_scale, &thread_data[t]);
                break;
            case 2:
                pthread_create(&threads[t], NULL, stream_add, &thread_data[t]);
                break;
            case 3:
                pthread_create(&threads[t], NULL, stream_triad, &thread_data[t]);
                break;
        }
    }
    
    // 等待所有线程完成
    for (int t = 0; t < NUM_THREADS; t++) {
        pthread_join(threads[t], NULL);
    }
    
    // 计算总时间（取最慢线程的时间）
    double max_time = 0.0;
    for (int t = 0; t < NUM_THREADS; t++) {
        if (thread_data[t].local_times[operation] > max_time) {
            max_time = thread_data[t].local_times[operation];
        }
        delete[] thread_data[t].local_times;
    }
    
    delete[] threads;
    delete[] thread_data;
    
    return max_time;
}

// 主函数
int main(int argc, char *argv[]) {
    // 解析命令行参数
    parseCommandLine(argc, argv);
    
    // 打印配置
    printConfig();
    
    // 初始化数组
    initializeArrays();
    
    // 计算数据量
    for (int i = 0; i < 4; i++) {
        bytes[i] = 2.0 * sizeof(STREAM_TYPE) * STREAM_ARRAY_SIZE;
    }
    
    // 检查时钟精度
    int quantum = checktick();
    printf("\n时钟精度: %d 微秒\n", quantum);
    
    // 预热测试
    printf("\n预热测试...\n");
    double warmup_time = mysecond();
    for (size_t j = 0; j < STREAM_ARRAY_SIZE; j++) {
        a[j] = 2.0 * a[j];
    }
    warmup_time = mysecond() - warmup_time;
    printf("预热测试时间: %.6f 秒\n", warmup_time);
    
    // 执行STREAM测试
    printf("\n开始STREAM测试...\n");
    double times[4][NTIMES];
    STREAM_TYPE scalar = 3.0;
    
    for (int k = 0; k < NTIMES; k++) {
        printf("第 %d 次迭代...\n", k + 1);
        
        // Copy
        times[0][k] = parallel_stream_test(0, scalar);
        
        // Scale
        times[1][k] = parallel_stream_test(1, scalar);
        
        // Add
        times[2][k] = parallel_stream_test(2, scalar);
        
        // Triad
        times[3][k] = parallel_stream_test(3, scalar);
        
        // 显示本次迭代结果
        printf("  本次迭代时间: Copy=%.6fs, Scale=%.6fs, Add=%.6fs, Triad=%.6fs\n",
               times[0][k], times[1][k], times[2][k], times[3][k]);
    }
    
    // 计算结果（跳过第一次迭代）
    for (int k = 1; k < NTIMES; k++) {
        for (int j = 0; j < 4; j++) {
            avgtime[j] += times[j][k];
            if (times[j][k] < mintime[j]) mintime[j] = times[j][k];
            if (times[j][k] > maxtime[j]) maxtime[j] = times[j][k];
        }
    }
    
    // 输出结果
    printf("\n%s", "-------------------------------------------------------------\n");
    printf("测试结果汇总:\n");
    printf("Function    Best Rate MB/s  Avg time     Min time     Max time\n");
    
    for (int j = 0; j < 4; j++) {
        avgtime[j] /= (NTIMES - 1);
        double best_rate = 1.0E-06 * bytes[j] / mintime[j];
        
        printf("%s%12.1f  %11.6f  %11.6f  %11.6f\n", label[j],
               best_rate, avgtime[j], mintime[j], maxtime[j]);
    }
    
    // 保存结果到文件
    saveDataToFile("stream_result.txt", a, STREAM_ARRAY_SIZE);
    
    // 清理内存
    cleanupArrays();
    
    return 0;
}

// 解析命令行参数
void parseCommandLine(int argc, char *argv[]) {
    cout << "解析命令行参数..." << endl;
    
    // 默认参数
    char *input_file = nullptr;
    char *output_file = nullptr;
    
    for (int i = 1; i < argc; i++) {
        if (strcmp(argv[i], "-size") == 0 && i + 1 < argc) {
            STREAM_ARRAY_SIZE = atoi(argv[++i]);
            cout << "  数组大小: " << STREAM_ARRAY_SIZE << endl;
        } else if (strcmp(argv[i], "-nthreads") == 0 && i + 1 < argc) {
            NUM_THREADS = atoi(argv[++i]);
            cout << "  线程数: " << NUM_THREADS << endl;
        } else if (strcmp(argv[i], "-ntimes") == 0 && i + 1 < argc) {
            NTIMES = atoi(argv[++i]);
            cout << "  迭代次数: " << NTIMES << endl;
        } else if (strcmp(argv[i], "-offset") == 0 && i + 1 < argc) {
            OFFSET = atoi(argv[++i]);
            cout << "  偏移量: " << OFFSET << endl;
        } else if (strcmp(argv[i], "-input") == 0 && i + 1 < argc) {
            input_file = argv[++i];
            cout << "  输入文件: " << input_file << endl;
        } else if (strcmp(argv[i], "-output") == 0 && i + 1 < argc) {
            output_file = argv[++i];
            cout << "  输出文件: " << output_file << endl;
        } else if (strcmp(argv[i], "-h") == 0 || strcmp(argv[i], "--help") == 0) {
            cout << "\n用法: " << argv[0] << " [选项]" << endl;
            cout << "选项:" << endl;
            cout << "  -size <N>        数组大小 (默认: 10000000)" << endl;
            cout << "  -nthreads <T>    线程数 (默认: 4)" << endl;
            cout << "  -ntimes <N>      迭代次数 (默认: 100)" << endl;
            cout << "  -offset <O>      偏移量 (默认: 0)" << endl;
            cout << "  -input <file>    输入数据文件" << endl;
            cout << "  -output <file>   输出结果文件" << endl;
            cout << "  -h, --help       显示此帮助信息" << endl;
            exit(0);
        }
    }
    
    // 验证参数
    if (NUM_THREADS <= 0) {
        cerr << "错误: 线程数必须大于0" << endl;
        exit(1);
    }
    if (STREAM_ARRAY_SIZE <= 0) {
        cerr << "错误: 数组大小必须大于0" << endl;
        exit(1);
    }
}

// 初始化数组
void initializeArrays() {
    cout << "\n初始化数组..." << endl;
    
    size_t total_size = STREAM_ARRAY_SIZE + OFFSET;
    
    a = new STREAM_TYPE[total_size];
    b = new STREAM_TYPE[total_size];
    c = new STREAM_TYPE[total_size];
    
    if (!a || !b || !c) {
        cerr << "错误: 内存分配失败" << endl;
        exit(1);
    }
    
    // 使用随机数据初始化
    generateRandomData();
    
    cout << "  数组初始化完成" << endl;
    cout << "  每个数组大小: " << sizeof(STREAM_TYPE) * STREAM_ARRAY_SIZE / (1024.0 * 1024.0) << " MB" << endl;
}

// 生成随机数据
void generateRandomData() {
    cout << "  生成随机数据..." << endl;
    srand(time(NULL));
    
    for (size_t j = 0; j < STREAM_ARRAY_SIZE; j++) {
        a[j] = 1.0;
        b[j] = 2.0;
        c[j] = 0.0;
    }
}

// 从文件加载数据
void loadDataFromFile(const char *filename, STREAM_TYPE *array, size_t size) {
    ifstream file(filename);
    if (!file.is_open()) {
        cerr << "错误: 无法打开文件 " << filename << endl;
        return;
    }
    
    cout << "  从文件 " << filename << " 加载数据..." << endl;
    
    size_t count = 0;
    STREAM_TYPE value;
    while (file >> value && count < size) {
        array[count++] = value;
    }
    
    file.close();
    cout << "  加载了 " << count << " 个数据点" << endl;
}

// 保存数据到文件
void saveDataToFile(const char *filename, STREAM_TYPE *array, size_t size) {
    ofstream file(filename);
    if (!file.is_open()) {
        cerr << "警告: 无法创建文件 " << filename << endl;
        return;
    }
    
    cout << "  保存结果到文件 " << filename << "..." << endl;
    
    for (size_t i = 0; i < min(size, (size_t)1000); i++) { // 只保存前1000个值
        file << array[i] << " ";
        if ((i + 1) % 10 == 0) file << endl;
    }
    
    file.close();
    cout << "  数据保存完成" << endl;
}

// 清理内存
void cleanupArrays() {
    cout << "\n清理内存..." << endl;
    
    delete[] a;
    delete[] b;
    delete[] c;
    
    a = b = c = nullptr;
}

// 打印配置信息
void printConfig() {
    cout << "STREAM并行基准测试" << endl;
    cout << "==================" << endl;
    cout << "配置参数:" << endl;
    cout << "  数组大小: " << STREAM_ARRAY_SIZE << " 元素" << endl;
    cout << "  数据类型大小: " << sizeof(STREAM_TYPE) << " 字节" << endl;
    cout << "  总内存需求: " 
         << (3.0 * sizeof(STREAM_TYPE) * STREAM_ARRAY_SIZE) / (1024.0 * 1024.0)
         << " MB" << endl;
    cout << "  线程数: " << NUM_THREADS << endl;
    cout << "  迭代次数: " << NTIMES << endl;
    cout << "  偏移量: " << OFFSET << endl;
}

// 获取当前时间（秒）
double mysecond() {
    struct timeval tp;
    struct timezone tzp;
    gettimeofday(&tp, &tzp);
    return ((double)tp.tv_sec + (double)tp.tv_usec * 1.e-6);
}

// 检查时钟精度
int checktick() {
    const int M = 20;
    int i, minDelta, Delta;
    double t1, t2, timesfound[M];
    
    // 收集M个不同的时间值
    for (i = 0; i < M; i++) {
        t1 = mysecond();
        while (((t2 = mysecond()) - t1) < 1.0E-6);
        timesfound[i] = t1 = t2;
    }
    
    // 计算最小时间差
    minDelta = 1000000;
    for (i = 1; i < M; i++) {
        Delta = (int)(1.0E6 * (timesfound[i] - timesfound[i - 1]));
        minDelta = min(minDelta, max(Delta, 0));
    }
    
    return minDelta;
}
