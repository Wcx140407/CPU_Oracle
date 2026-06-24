//g++ -o sha256_gcc sha256_pthread.cpp -lpthread -lssl -lcrypto
#include <iostream>
#include <pthread.h>
#include <cstdlib>
#include <cstring>
#include <vector>
#include <string>
#include <sstream>
#include <iomanip>
#include <openssl/sha.h>
#include <sys/time.h>
#include <fstream>
#include <atomic>

using namespace std;

// 全局配置
static int input_size = 20 * (1 << 20);  // 默认输入大小 (20MB)
static int num_reps = 100;               // 默认重复次数
static int num_threads = 4;              // 默认线程数
static string input_file = "";           // 输入文件
static string output_file = "";          // 输出文件

// 全局数据
static vector<string> input_data;        // 输入数据
static vector<string> output_hashes;     // 输出哈希
static atomic<int> next_task(0);         // 下一个任务索引
static pthread_mutex_t output_mutex = PTHREAD_MUTEX_INITIALIZER;
static pthread_mutex_t input_mutex = PTHREAD_MUTEX_INITIALIZER;

// 线程参数结构体
struct ThreadData {
    int thread_id;
    int start_idx;
    int end_idx;
    vector<string>* local_hashes;
    double* local_time;
};

// 函数声明
string sha256(const string &str);
void parseCommandLine(int argc, char *argv[]);
void generateRandomData(int size);
void loadDataFromFile(const string &filename);
void saveResultsToFile(const string &filename);
void printConfig();
double getCurrentTime();

// SHA256计算函数
string sha256(const string &str) {
    unsigned char hash[SHA256_DIGEST_LENGTH];
    SHA256_CTX sha256;
    SHA256_Init(&sha256);
    SHA256_Update(&sha256, str.c_str(), str.size());
    SHA256_Final(hash, &sha256);
    
    stringstream ss;
    ss << hex << setfill('0');
    for (int i = 0; i < SHA256_DIGEST_LENGTH; i++) {
        ss << setw(2) << (int)hash[i];
    }
    return ss.str();
}

// 生成随机数据
void generateRandomData(int size) {
    cout << "生成随机数据..." << endl;
    
    // 生成一个大的随机字符串
    srand(2333); // 固定种子以确保可重复性
    stringstream ss;
    
    // 生成指定大小的数据
    for (int i = 0; i < size; ++i) {
        switch (rand() % 3) {
            case 0:
                ss << (char)('A' + rand() % 26);
                break;
            case 1:
                ss << (char)('a' + rand() % 26);
                break;
            case 2:
                ss << (char)('0' + rand() % 10);
                break;
        }
    }
    
    input_data.push_back(ss.str());
    cout << "生成了 " << input_data[0].size() << " 字节的随机数据" << endl;
}

// 从文件加载数据
void loadDataFromFile(const string &filename) {
    cout << "从文件 " << filename << " 加载数据..." << endl;
    
    ifstream file(filename, ios::binary);
    if (!file.is_open()) {
        cerr << "错误: 无法打开文件 " << filename << endl;
        exit(1);
    }
    
    // 获取文件大小
    file.seekg(0, ios::end);
    size_t file_size = file.tellg();
    file.seekg(0, ios::beg);
    
    // 读取文件内容
    string content(file_size, '\0');
    file.read(&content[0], file_size);
    file.close();
    
    // 如果文件很大，分割成多个块
    if (file_size > 1024 * 1024) { // 如果大于1MB，分割
        size_t chunk_size = file_size / num_threads;
        for (size_t i = 0; i < num_threads; i++) {
            size_t start = i * chunk_size;
            size_t end = (i == num_threads - 1) ? file_size : (i + 1) * chunk_size;
            input_data.push_back(content.substr(start, end - start));
        }
    } else {
        input_data.push_back(content);
    }
    
    cout << "加载了 " << input_data.size() << " 个数据块，总大小 " << file_size << " 字节" << endl;
}

// 线程函数 - 基于任务队列
void* processTasks(void* arg) {
    ThreadData* data = (ThreadData*)arg;
    int thread_id = data->thread_id;
    
    double start_time = getCurrentTime();
    
    // 从任务队列获取任务
    while (true) {
        int task_idx = next_task.fetch_add(1);
        if (task_idx >= num_reps * input_data.size()) {
            break;
        }
        
        int data_idx = task_idx % input_data.size();
        int rep_idx = task_idx / input_data.size();
        
        // 计算SHA256
        string hash = sha256(input_data[data_idx]);
        
        // 保存结果
        if (rep_idx == 0) { // 只保存第一次的结果
            pthread_mutex_lock(&output_mutex);
            if (data_idx >= data->local_hashes->size()) {
                data->local_hashes->resize(data_idx + 1);
            }
            (*data->local_hashes)[data_idx] = hash;
            pthread_mutex_unlock(&output_mutex);
        }
        
        if (rep_idx == 0 && thread_id == 0 && task_idx % 10 == 0) {
            cout << "处理进度: " << task_idx + 1 << "/" << num_reps * input_data.size() << endl;
        }
    }
    
    double end_time = getCurrentTime();
    *data->local_time = end_time - start_time;
    
    pthread_exit(NULL);
}

// 线程函数 - 基于数据分区
void* processPartition(void* arg) {
    ThreadData* data = (ThreadData*)arg;
    int thread_id = data->thread_id;
    int start_idx = data->start_idx;
    int end_idx = data->end_idx;
    
    double start_time = getCurrentTime();
    
    // 处理分配的数据分区
    for (int i = start_idx; i < end_idx; i++) {
        for (int rep = 0; rep < num_reps; rep++) {
            string hash = sha256(input_data[i]);
            
            if (rep == 0) { // 只保存第一次的结果
                pthread_mutex_lock(&output_mutex);
                if (i >= data->local_hashes->size()) {
                    data->local_hashes->resize(i + 1);
                }
                (*data->local_hashes)[i] = hash;
                pthread_mutex_unlock(&output_mutex);
            }
        }
        
        if (thread_id == 0 && i % max(1, (end_idx - start_idx) / 10) == 0) {
            cout << "处理进度: " << (i - start_idx + 1) << "/" << (end_idx - start_idx) << endl;
        }
    }
    
    double end_time = getCurrentTime();
    *data->local_time = end_time - start_time;
    
    pthread_exit(NULL);
}

// 并行执行SHA256计算
void parallelSHA256(bool use_task_queue) {
    pthread_t* threads = new pthread_t[num_threads];
    ThreadData* thread_data = new ThreadData[num_threads];
    
    // 重置任务队列
    next_task.store(0);
    
    // 初始化输出哈希
    output_hashes.resize(input_data.size());
    
    // 创建线程
    for (int t = 0; t < num_threads; t++) {
        thread_data[t].thread_id = t;
        thread_data[t].local_hashes = new vector<string>();
        thread_data[t].local_time = new double(0.0);
        
        if (use_task_queue) {
            // 任务队列模式 - 所有线程共享任务队列
            pthread_create(&threads[t], NULL, processTasks, &thread_data[t]);
        } else {
            // 数据分区模式 - 每个线程处理一部分数据
            int chunk_size = input_data.size() / num_threads;
            thread_data[t].start_idx = t * chunk_size;
            thread_data[t].end_idx = (t == num_threads - 1) ? input_data.size() : (t + 1) * chunk_size;
            pthread_create(&threads[t], NULL, processPartition, &thread_data[t]);
        }
    }
    
    // 等待所有线程完成
    for (int t = 0; t < num_threads; t++) {
        pthread_join(threads[t], NULL);
        
        // 合并结果
        for (size_t i = 0; i < thread_data[t].local_hashes->size(); i++) {
            if (!(*thread_data[t].local_hashes)[i].empty()) {
                output_hashes[i] = (*thread_data[t].local_hashes)[i];
            }
        }
    }
    
    // 清理线程数据
    for (int t = 0; t < num_threads; t++) {
        delete thread_data[t].local_hashes;
        delete thread_data[t].local_time;
    }
    
    delete[] threads;
    delete[] thread_data;
}

// 串行执行SHA256计算（用于性能对比）
double sequentialSHA256() {
    double start_time = getCurrentTime();
    
    output_hashes.resize(input_data.size());
    
    for (size_t i = 0; i < input_data.size(); i++) {
        for (int rep = 0; rep < num_reps; rep++) {
            output_hashes[i] = sha256(input_data[i]);
        }
        
        if (i % max(1, (int)input_data.size() / 10) == 0) {
            cout << "处理进度: " << i + 1 << "/" << input_data.size() << endl;
        }
    }
    
    double end_time = getCurrentTime();
    return end_time - start_time;
}

// 保存结果到文件
void saveResultsToFile(const string &filename) {
    ofstream file(filename);
    if (!file.is_open()) {
        cerr << "警告: 无法创建文件 " << filename << endl;
        return;
    }
    
    cout << "保存结果到文件 " << filename << "..." << endl;
    
    for (size_t i = 0; i < output_hashes.size(); i++) {
        file << "数据块 " << i << " (" << input_data[i].size() << " 字节):" << endl;
        file << "  SHA256: " << output_hashes[i] << endl;
        if (i < 3 && input_data[i].size() <= 100) { // 只显示前3个小数据块的内容
            file << "  内容: " << input_data[i].substr(0, 100) << 
                 (input_data[i].size() > 100 ? "..." : "") << endl;
        }
        file << endl;
    }
    
    file.close();
    cout << "结果保存完成" << endl;
}

// 打印配置信息
void printConfig() {
    cout << "SHA256并行计算程序" << endl;
    cout << "=================" << endl;
    cout << "配置参数:" << endl;
    cout << "  数据块数量: " << input_data.size() << endl;
    if (!input_data.empty()) {
        size_t total_size = 0;
        for (const auto& data : input_data) {
            total_size += data.size();
        }
        cout << "  总数据大小: " << total_size << " 字节" << endl;
        cout << "  最大数据块: " << input_data[0].size() << " 字节" << endl;
    }
    cout << "  线程数: " << num_threads << endl;
    cout << "  重复次数: " << num_reps << endl;
    cout << "  总计算次数: " << num_reps * input_data.size() << endl;
    if (!input_file.empty()) {
        cout << "  输入文件: " << input_file << endl;
    }
    if (!output_file.empty()) {
        cout << "  输出文件: " << output_file << endl;
    }
}

// 获取当前时间（秒）
double getCurrentTime() {
    struct timeval tv;
    gettimeofday(&tv, NULL);
    return (double)tv.tv_sec + (double)tv.tv_usec * 1.e-6;
}

// 解析命令行参数
void parseCommandLine(int argc, char *argv[]) {
    cout << "解析命令行参数..." << endl;
    
    for (int i = 1; i < argc; i++) {
        if (strcmp(argv[i], "-size") == 0 && i + 1 < argc) {
            input_size = atoi(argv[++i]);
            cout << "  输入大小: " << input_size << " 字节" << endl;
        } else if (strcmp(argv[i], "-nthreads") == 0 && i + 1 < argc) {
            num_threads = atoi(argv[++i]);
            cout << "  线程数: " << num_threads << endl;
        } else if (strcmp(argv[i], "-nreps") == 0 && i + 1 < argc) {
            num_reps = atoi(argv[++i]);
            cout << "  重复次数: " << num_reps << endl;
        } else if (strcmp(argv[i], "-input") == 0 && i + 1 < argc) {
            input_file = argv[++i];
            cout << "  输入文件: " << input_file << endl;
        } else if (strcmp(argv[i], "-output") == 0 && i + 1 < argc) {
            output_file = argv[++i];
            cout << "  输出文件: " << output_file << endl;
        } else if (strcmp(argv[i], "-h") == 0 || strcmp(argv[i], "--help") == 0) {
            cout << "\n用法: " << argv[0] << " [选项]" << endl;
            cout << "选项:" << endl;
            cout << "  -size <N>        输入数据大小(字节) (默认: 20971520)" << endl;
            cout << "  -nthreads <T>    线程数 (默认: 4)" << endl;
            cout << "  -nreps <N>       重复次数 (默认: 100)" << endl;
            cout << "  -input <file>    输入数据文件" << endl;
            cout << "  -output <file>   输出结果文件" << endl;
            cout << "  -h, --help       显示此帮助信息" << endl;
            exit(0);
        }
    }
    
    // 验证参数
    if (num_threads <= 0) {
        cerr << "错误: 线程数必须大于0" << endl;
        exit(1);
    }
    if (input_size <= 0) {
        cerr << "错误: 输入大小必须大于0" << endl;
        exit(1);
    }
    if (num_reps <= 0) {
        cerr << "错误: 重复次数必须大于0" << endl;
        exit(1);
    }
}

// 主函数
int main(int argc, char *argv[]) {
    // 解析命令行参数
    parseCommandLine(argc, argv);
    
    // 初始化数据
    if (!input_file.empty()) {
        loadDataFromFile(input_file);
    } else {
        generateRandomData(input_size);
    }
    
    // 打印配置
    printConfig();
    
    // 执行计算
    cout << "\n开始SHA256计算..." << endl;
    
    double start_time = getCurrentTime();
    
    // 选择并行模式：true为任务队列模式，false为数据分区模式
    bool use_task_queue = (input_data.size() * num_reps > 100);
    parallelSHA256(use_task_queue);
    
    double end_time = getCurrentTime();
    double elapsed = end_time - start_time;
    
    // 输出结果
    cout << "\n计算结果:" << endl;
    for (size_t i = 0; i < min(output_hashes.size(), (size_t)3); i++) {
        cout << "数据块 " << i << " 的SHA256: " << output_hashes[i] << endl;
    }
    
    // 性能统计
    long long total_operations = (long long)num_reps * input_data.size();
    cout << "\n性能统计:" << endl;
    cout << "  总时间: " << elapsed << " 秒" << endl;
    cout << "  总操作数: " << total_operations << " 次SHA256计算" << endl;
    cout << "  平均时间: " << elapsed / total_operations << " 秒/次" << endl;
    cout << "  吞吐量: " << total_operations / elapsed << " 次SHA256/秒" << endl;
    
    // 与串行版本对比（可选）
    if (input_data.size() * num_reps <= 1000) { // 小规模数据才进行对比
        cout << "\n与串行版本对比..." << endl;
        double seq_time = sequentialSHA256();
        cout << "  串行时间: " << seq_time << " 秒" << endl;
        cout << "  并行时间: " << elapsed << " 秒" << endl;
        cout << "  加速比: " << seq_time / elapsed << endl;
        cout << "  并行效率: " << (seq_time / elapsed) / num_threads * 100 << "%" << endl;
    }
    
    // 保存结果
    if (!output_file.empty()) {
        saveResultsToFile(output_file);
    } else {
        saveResultsToFile("sha256_results.txt");
    }
    
    // 清理互斥锁
    pthread_mutex_destroy(&output_mutex);
    pthread_mutex_destroy(&input_mutex);
    
    return 0;
}
