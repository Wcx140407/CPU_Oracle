//g++ -o sha256_custom_gcc sha256_custom_pthread.cpp -lpthread -std=c++17
#include <iostream>
#include <pthread.h>
#include <cstdlib>
#include <cstring>
#include <vector>
#include <string>
#include <sstream>
#include <iomanip>
#include <sys/time.h>
#include <fstream>
#include <atomic>
#include <array>
#include <cstdint>

using namespace std;

// SHA256常量定义
#define SHA256_DIGEST_LENGTH 32

// 自定义SHA256类
class SHA256 {
public:
    SHA256();
    void update(const uint8_t *data, size_t length);
    void update(const string &data);
    uint8_t *digest();
    static string toString(const uint8_t *digest);

private:
    uint8_t  m_data[64];
    uint32_t m_blocklen;
    uint64_t m_bitlen;
    uint32_t m_state[8]; // A, B, C, D, E, F, G, H

    static constexpr array<uint32_t, 64> K = {
        0x428a2f98,0x71374491,0xb5c0fbcf,0xe9b5dba5,
        0x3956c25b,0x59f111f1,0x923f82a4,0xab1c5ed5,
        0xd807aa98,0x12835b01,0x243185be,0x550c7dc3,
        0x72be5d74,0x80deb1fe,0x9bdc06a7,0xc19bf174,
        0xe49b69c1,0xefbe4786,0x0fc19dc6,0x240ca1cc,
        0x2de92c6f,0x4a7484aa,0x5cb0a9dc,0x76f988da,
        0x983e5152,0xa831c66d,0xb00327c8,0xbf597fc7,
        0xc6e00bf3,0xd5a79147,0x06ca6351,0x14292967,
        0x27b70a85,0x2e1b2138,0x4d2c6dfc,0x53380d13,
        0x650a7354,0x766a0abb,0x81c2c92e,0x92722c85,
        0xa2bfe8a1,0xa81a664b,0xc24b8b70,0xc76c51a3,
        0xd192e819,0xd6990624,0xf40e3585,0x106aa070,
        0x19a4c116,0x1e376c08,0x2748774c,0x34b0bcb5,
        0x391c0cb3,0x4ed8aa4a,0x5b9cca4f,0x682e6ff3,
        0x748f82ee,0x78a5636f,0x84c87814,0x8cc70208,
        0x90befffa,0xa4506ceb,0xbef9a3f7,0xc67178f2
    };

    static uint32_t rotr(uint32_t x, uint32_t n);
    static uint32_t choose(uint32_t e, uint32_t f, uint32_t g);
    static uint32_t majority(uint32_t a, uint32_t b, uint32_t c);
    static uint32_t sig0(uint32_t x);
    static uint32_t sig1(uint32_t x);
    void transform();
    void pad();
    void revert(uint8_t *hash);
};

SHA256::SHA256(): m_blocklen(0), m_bitlen(0) {
    m_state[0] = 0x6a09e667;
    m_state[1] = 0xbb67ae85;
    m_state[2] = 0x3c6ef372;
    m_state[3] = 0xa54ff53a;
    m_state[4] = 0x510e527f;
    m_state[5] = 0x9b05688c;
    m_state[6] = 0x1f83d9ab;
    m_state[7] = 0x5be0cd19;
}

void SHA256::update(const uint8_t *data, size_t length) {
    for (size_t i = 0; i < length; i++) {
        m_data[m_blocklen++] = data[i];
        if (m_blocklen == 64) {
            transform();
            m_bitlen += 512;
            m_blocklen = 0;
        }
    }
}

void SHA256::update(const string &data) {
    update(reinterpret_cast<const uint8_t*>(data.c_str()), data.size());
}

uint8_t *SHA256::digest() {
    uint8_t *hash = new uint8_t[SHA256_DIGEST_LENGTH];
    pad();
    revert(hash);
    return hash;
}

uint32_t SHA256::rotr(uint32_t x, uint32_t n) {
    return (x >> n) | (x << (32 - n));
}

uint32_t SHA256::choose(uint32_t e, uint32_t f, uint32_t g) {
    return (e & f) ^ (~e & g);
}

uint32_t SHA256::majority(uint32_t a, uint32_t b, uint32_t c) {
    return (a & (b | c)) | (b & c);
}

uint32_t SHA256::sig0(uint32_t x) {
    return SHA256::rotr(x, 7) ^ SHA256::rotr(x, 18) ^ (x >> 3);
}

uint32_t SHA256::sig1(uint32_t x) {
    return SHA256::rotr(x, 17) ^ SHA256::rotr(x, 19) ^ (x >> 10);
}

void SHA256::transform() {
    uint32_t maj, xorA, ch, xorE, sum, newA, newE;
    uint32_t m[64];
    uint32_t state[8];

    for (uint8_t i = 0, j = 0; i < 16; i++, j += 4) {
        m[i] = (m_data[j] << 24) | (m_data[j + 1] << 16) | 
               (m_data[j + 2] << 8) | (m_data[j + 3]);
    }

    for (uint8_t k = 16; k < 64; k++) {
        m[k] = SHA256::sig1(m[k - 2]) + m[k - 7] + 
               SHA256::sig0(m[k - 15]) + m[k - 16];
    }

    for (uint8_t i = 0; i < 8; i++) {
        state[i] = m_state[i];
    }

    for (uint8_t i = 0; i < 64; i++) {
        maj = SHA256::majority(state[0], state[1], state[2]);
        xorA = SHA256::rotr(state[0], 2) ^ SHA256::rotr(state[0], 13) ^ 
               SHA256::rotr(state[0], 22);
        ch = SHA256::choose(state[4], state[5], state[6]);
        xorE = SHA256::rotr(state[4], 6) ^ SHA256::rotr(state[4], 11) ^ 
               SHA256::rotr(state[4], 25);
        sum = m[i] + K[i] + state[7] + ch + xorE;
        newA = xorA + maj + sum;
        newE = state[3] + sum;

        state[7] = state[6];
        state[6] = state[5];
        state[5] = state[4];
        state[4] = newE;
        state[3] = state[2];
        state[2] = state[1];
        state[1] = state[0];
        state[0] = newA;
    }

    for (uint8_t i = 0; i < 8; i++) {
        m_state[i] += state[i];
    }
}

void SHA256::pad() {
    uint64_t i = m_blocklen;
    uint8_t end = m_blocklen < 56 ? 56 : 64;

    m_data[i++] = 0x80;
    while (i < end) {
        m_data[i++] = 0x00;
    }

    if (m_blocklen >= 56) {
        transform();
        memset(m_data, 0, 56);
    }

    m_bitlen += m_blocklen * 8;
    m_data[63] = m_bitlen;
    m_data[62] = m_bitlen >> 8;
    m_data[61] = m_bitlen >> 16;
    m_data[60] = m_bitlen >> 24;
    m_data[59] = m_bitlen >> 32;
    m_data[58] = m_bitlen >> 40;
    m_data[57] = m_bitlen >> 48;
    m_data[56] = m_bitlen >> 56;
    transform();
}

void SHA256::revert(uint8_t *hash) {
    for (uint8_t i = 0; i < 4; i++) {
        for (uint8_t j = 0; j < 8; j++) {
            hash[i + (j * 4)] = (m_state[j] >> (24 - i * 8)) & 0x000000ff;
        }
    }
}

string SHA256::toString(const uint8_t *digest) {
    stringstream s;
    s << hex << setfill('0');
    for (uint8_t i = 0; i < SHA256_DIGEST_LENGTH; i++) {
        s << setw(2) << static_cast<unsigned int>(digest[i]);
    }
    return s.str();
}

// SHA256包装函数
string sha256(const string &str) {
    SHA256 sha256;
    sha256.update(str);
    uint8_t *hash = sha256.digest();
    string ret = SHA256::toString(hash);
    delete[] hash;
    return ret;
}

// 全局配置
static int input_size = 20 * (1 << 20);  // 默认输入大小 (20MB)
static int num_reps = 100;               // 默认重复次数
static int num_threads = 4;              // 默认线程数
static string input_file = "";           // 输入文件
static string output_file = "";          // 输出文件
static bool debug_mode = false;          // 调试模式

// 全局数据
static vector<string> input_data;        // 输入数据
static vector<string> output_hashes;     // 输出哈希
static atomic<int> next_task(0);         // 下一个任务索引
static atomic<long long> processed_count(0); // 已处理计数
static pthread_mutex_t output_mutex = PTHREAD_MUTEX_INITIALIZER;
static pthread_mutex_t io_mutex = PTHREAD_MUTEX_INITIALIZER;

// 线程参数结构体
struct ThreadData {
    int thread_id;
    int start_idx;
    int end_idx;
    vector<string>* local_hashes;
    double* local_time;
    long long* operations_count;
};

// 函数声明
void parseCommandLine(int argc, char *argv[]);
string generateRandomData(int size);
void loadDataFromFile(const string &filename);
void saveResultsToFile(const string &filename);
void printConfig();
double getCurrentTime();
void printProgress(int current, int total);

// 生成随机数据
string generateRandomData(int size) {
    srand(2333); // 固定种子以确保可重复性
    stringstream ss;
    
    for (int i = 0; i < size; ++i) {
        switch (rand() % 3) {
            case 0:
                ss << static_cast<char>('A' + rand() % 26);
                break;
            case 1:
                ss << static_cast<char>('a' + rand() % 26);
                break;
            case 2:
                ss << static_cast<char>('0' + rand() % 10);
                break;
        }
    }
    return ss.str();
}

// 从文件加载数据
void loadDataFromFile(const string &filename) {
    ifstream file(filename, ios::binary | ios::ate);
    if (!file.is_open()) {
        cerr << "错误: 无法打开文件 " << filename << endl;
        exit(1);
    }
    
    streamsize file_size = file.tellg();
    file.seekg(0, ios::beg);
    
    if (file_size <= 0) {
        cerr << "错误: 文件大小为0或读取失败" << endl;
        exit(1);
    }
    
    cout << "从文件 " << filename << " 加载数据..." << endl;
    cout << "文件大小: " << file_size << " 字节" << endl;
    
    // 根据文件大小决定分块策略
    if (file_size > 100 * 1024 * 1024) { // 大于100MB
        int chunk_count = num_threads * 4; // 更多分块以平衡负载
        size_t chunk_size = file_size / chunk_count;
        
        for (int i = 0; i < chunk_count; i++) {
            size_t start = i * chunk_size;
            size_t end = (i == chunk_count - 1) ? file_size : (i + 1) * chunk_size;
            size_t chunk_len = end - start;
            
            string chunk(chunk_len, '\0');
            file.seekg(start, ios::beg);
            file.read(&chunk[0], chunk_len);
            
            input_data.push_back(chunk);
        }
    } else if (file_size > 1024 * 1024) { // 大于1MB
        size_t chunk_size = file_size / num_threads;
        for (size_t i = 0; i < num_threads; i++) {
            size_t start = i * chunk_size;
            size_t end = (i == num_threads - 1) ? file_size : (i + 1) * chunk_size;
            size_t chunk_len = end - start;
            
            string chunk(chunk_len, '\0');
            file.seekg(start, ios::beg);
            file.read(&chunk[0], chunk_len);
            
            input_data.push_back(chunk);
        }
    } else {
        string content(file_size, '\0');
        file.seekg(0, ios::beg);
        file.read(&content[0], file_size);
        input_data.push_back(content);
    }
    
    file.close();
    cout << "加载了 " << input_data.size() << " 个数据块" << endl;
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
        
        if (debug_mode && i < 3 && input_data[i].size() <= 100) {
            string preview = input_data[i].substr(0, min<size_t>(100, input_data[i].size()));
            file << "  预览: " << preview;
            if (input_data[i].size() > 100) file << "...";
            file << endl;
        }
        file << endl;
    }
    
    file.close();
    cout << "结果保存完成" << endl;
}

// 打印进度
void printProgress(int current, int total) {
    if (total == 0) return;
    
    int percentage = static_cast<int>((static_cast<double>(current) / total) * 100);
    int bar_width = 50;
    int pos = static_cast<int>(bar_width * static_cast<double>(current) / total);
    
    pthread_mutex_lock(&io_mutex);
    cout << "\r[";
    for (int i = 0; i < bar_width; ++i) {
        if (i < pos) cout << "=";
        else if (i == pos) cout << ">";
        else cout << " ";
    }
    cout << "] " << percentage << "% (" << current << "/" << total << ")";
    cout.flush();
    
    if (current == total) cout << endl;
    pthread_mutex_unlock(&io_mutex);
}

// 线程函数 - 任务队列模式
void* processTasks(void* arg) {
    ThreadData* data = static_cast<ThreadData*>(arg);
    int thread_id = data->thread_id;
    
    double start_time = getCurrentTime();
    long long local_count = 0;
    
    while (true) {
        int task_idx = next_task.fetch_add(1);
        if (task_idx >= num_reps * input_data.size()) {
            break;
        }
        
        int data_idx = task_idx % input_data.size();
        int rep_idx = task_idx / input_data.size();
        
        // 计算SHA256
        string hash = sha256(input_data[data_idx]);
        local_count++;
        
        // 保存结果
        if (rep_idx == 0) {
            pthread_mutex_lock(&output_mutex);
            if (data_idx >= output_hashes.size()) {
                output_hashes.resize(data_idx + 1);
            }
            output_hashes[data_idx] = hash;
            pthread_mutex_unlock(&output_mutex);
        }
        
        // 更新进度
        if (thread_id == 0) {
            processed_count.fetch_add(1);
            int current = processed_count.load();
            int total = num_reps * input_data.size();
            
            if (current % max(1, total / 100) == 0) {
                printProgress(current, total);
            }
        }
    }
    
    double end_time = getCurrentTime();
    *data->local_time = end_time - start_time;
    *data->operations_count = local_count;
    
    pthread_exit(nullptr);
}

// 线程函数 - 数据分区模式
void* processPartition(void* arg) {
    ThreadData* data = static_cast<ThreadData*>(arg);
    int thread_id = data->thread_id;
    int start_idx = data->start_idx;
    int end_idx = data->end_idx;
    
    double start_time = getCurrentTime();
    long long local_count = 0;
    
    for (int i = start_idx; i < end_idx; i++) {
        for (int rep = 0; rep < num_reps; rep++) {
            string hash = sha256(input_data[i]);
            local_count++;
            
            if (rep == 0) {
                pthread_mutex_lock(&output_mutex);
                if (i >= output_hashes.size()) {
                    output_hashes.resize(i + 1);
                }
                output_hashes[i] = hash;
                pthread_mutex_unlock(&output_mutex);
            }
            
            // 更新进度
            if (thread_id == 0) {
                processed_count.fetch_add(1);
                int current = processed_count.load();
                int total = num_reps * input_data.size();
                
                if (current % max(1, total / 100) == 0) {
                    printProgress(current, total);
                }
            }
        }
    }
    
    double end_time = getCurrentTime();
    *data->local_time = end_time - start_time;
    *data->operations_count = local_count;
    
    pthread_exit(nullptr);
}

// 并行执行SHA256计算
double parallelSHA256(bool use_task_queue) {
    pthread_t* threads = new pthread_t[num_threads];
    ThreadData* thread_data = new ThreadData[num_threads];
    
    // 重置计数器和任务队列
    next_task.store(0);
    processed_count.store(0);
    output_hashes.clear();
    output_hashes.resize(input_data.size());
    
    // 显示进度条标题
    if (!debug_mode) {
        cout << "处理进度:" << endl;
        printProgress(0, num_reps * input_data.size());
    }
    
    // 创建线程
    for (int t = 0; t < num_threads; t++) {
        thread_data[t].thread_id = t;
        thread_data[t].local_hashes = new vector<string>();
        thread_data[t].local_time = new double(0.0);
        thread_data[t].operations_count = new long long(0);
        
        if (use_task_queue) {
            // 任务队列模式
            pthread_create(&threads[t], nullptr, processTasks, &thread_data[t]);
        } else {
            // 数据分区模式
            int chunk_size = input_data.size() / num_threads;
            thread_data[t].start_idx = t * chunk_size;
            thread_data[t].end_idx = (t == num_threads - 1) ? input_data.size() : (t + 1) * chunk_size;
            pthread_create(&threads[t], nullptr, processPartition, &thread_data[t]);
        }
    }
    
    // 等待所有线程完成
    double max_time = 0.0;
    long long total_operations = 0;
    
    for (int t = 0; t < num_threads; t++) {
        pthread_join(threads[t], nullptr);
        
        if (*thread_data[t].local_time > max_time) {
            max_time = *thread_data[t].local_time;
        }
        total_operations += *thread_data[t].operations_count;
        
        // 清理内存
        delete thread_data[t].local_hashes;
        delete thread_data[t].local_time;
        delete thread_data[t].operations_count;
    }
    
    delete[] threads;
    delete[] thread_data;
    
    // 显示完成进度
    if (!debug_mode) {
        printProgress(num_reps * input_data.size(), num_reps * input_data.size());
    }
    
    return max_time;
}

// 串行执行SHA256计算（用于性能对比）
double sequentialSHA256() {
    double start_time = getCurrentTime();
    
    output_hashes.clear();
    output_hashes.resize(input_data.size());
    
    cout << "串行处理..." << endl;
    printProgress(0, input_data.size());
    
    for (size_t i = 0; i < input_data.size(); i++) {
        for (int rep = 0; rep < num_reps; rep++) {
            output_hashes[i] = sha256(input_data[i]);
        }
        
        if (i % max<size_t>(1, input_data.size() / 100) == 0) {
            printProgress(i + 1, input_data.size());
        }
    }
    
    printProgress(input_data.size(), input_data.size());
    double end_time = getCurrentTime();
    return end_time - start_time;
}

// 打印配置信息
void printConfig() {
    cout << "自定义SHA256并行计算程序" << endl;
    cout << "=======================" << endl;
    cout << "配置参数:" << endl;
    cout << "  数据块数量: " << input_data.size() << endl;
    
    size_t total_size = 0;
    size_t min_size = SIZE_MAX;
    size_t max_size = 0;
    
    for (const auto& data : input_data) {
        size_t sz = data.size();
        total_size += sz;
        min_size = min(min_size, sz);
        max_size = max(max_size, sz);
    }
    
    cout << "  总数据大小: " << total_size << " 字节 (" 
         << total_size / (1024.0 * 1024.0) << " MB)" << endl;
    if (input_data.size() > 1) {
        cout << "  最小数据块: " << min_size << " 字节" << endl;
        cout << "  最大数据块: " << max_size << " 字节" << endl;
        cout << "  平均数据块: " << total_size / input_data.size() << " 字节" << endl;
    }
    
    cout << "  线程数: " << num_threads << endl;
    cout << "  重复次数: " << num_reps << endl;
    cout << "  总计算次数: " << static_cast<long long>(num_reps) * input_data.size() << endl;
    
    if (!input_file.empty()) {
        cout << "  输入文件: " << input_file << endl;
    }
    if (!output_file.empty()) {
        cout << "  输出文件: " << output_file << endl;
    }
    if (debug_mode) {
        cout << "  调试模式: 启用" << endl;
    }
}

// 获取当前时间（秒）
double getCurrentTime() {
    struct timeval tv;
    gettimeofday(&tv, nullptr);
    return static_cast<double>(tv.tv_sec) + static_cast<double>(tv.tv_usec) * 1.e-6;
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
        } else if (strcmp(argv[i], "-debug") == 0) {
            debug_mode = true;
            cout << "  调试模式: 启用" << endl;
        } else if (strcmp(argv[i], "-h") == 0 || strcmp(argv[i], "--help") == 0) {
            cout << "\n用法: " << argv[0] << " [选项]" << endl;
            cout << "选项:" << endl;
            cout << "  -size <N>        输入数据大小(字节) (默认: 20971520)" << endl;
            cout << "  -nthreads <T>    线程数 (默认: 4)" << endl;
            cout << "  -nreps <N>       重复次数 (默认: 100)" << endl;
            cout << "  -input <file>    输入数据文件" << endl;
            cout << "  -output <file>   输出结果文件" << endl;
            cout << "  -debug           启用调试模式" << endl;
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
        cout << "生成随机数据..." << endl;
        if (debug_mode) {
            input_data.push_back("hello world");
            cout << "调试模式: 使用固定字符串 'hello world'" << endl;
        } else {
            string random_data = generateRandomData(input_size);
            input_data.push_back(random_data);
            cout << "生成了 " << random_data.size() << " 字节的随机数据" << endl;
        }
    }
    
    // 打印配置
    printConfig();
    
    // 执行计算
    cout << "\n开始SHA256计算..." << endl;
    
    double start_time = getCurrentTime();
    
    // 选择并行模式
    bool use_task_queue = (input_data.size() * num_reps > 100);
    double elapsed = parallelSHA256(use_task_queue);
    
    double end_time = getCurrentTime();
    
    // 输出结果
    if (debug_mode || input_data.size() <= 3) {
        cout << "\n计算结果:" << endl;
        for (size_t i = 0; i < min(output_hashes.size(), static_cast<size_t>(3)); i++) {
            if (debug_mode && input_data[i].size() <= 100) {
                cout << "数据块 " << i << " (" << input_data[i].size() << " 字节)" << endl;
                cout << "  内容: " << input_data[i] << endl;
            }
            cout << "  SHA256: " << output_hashes[i] << endl;
        }
    }
    
    // 验证结果（调试模式）
    if (debug_mode && !input_data.empty() && input_data[0] == "hello world") {
        string expected = "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9";
        cout << "\n验证结果:" << endl;
        cout << "  计算值: " << output_hashes[0] << endl;
        cout << "  期望值: " << expected << endl;
        cout << "  匹配: " << (output_hashes[0] == expected ? "是" : "否") << endl;
    }
    
    // 性能统计
    long long total_operations = static_cast<long long>(num_reps) * input_data.size();
    double wall_time = end_time - start_time;
    
    cout << "\n性能统计:" << endl;
    cout << "  并行时间: " << elapsed << " 秒" << endl;
    cout << "  总时间: " << wall_time << " 秒" << endl;
    cout << "  总操作数: " << total_operations << " 次SHA256计算" << endl;
    cout << "  平均时间: " << elapsed / total_operations << " 秒/次" << endl;
    cout << "  吞吐量: " << total_operations / elapsed << " 次SHA256/秒" << endl;
    
    // 与串行版本对比（小规模数据）
    if (input_data.size() * num_reps <= 1000 && input_data.size() > 0) {
        cout << "\n与串行版本对比..." << endl;
        double seq_time = sequentialSHA256();
        double speedup = seq_time / elapsed;
        double efficiency = (speedup / num_threads) * 100;
        
        cout << "  串行时间: " << seq_time << " 秒" << endl;
        cout << "  并行时间: " << elapsed << " 秒" << endl;
        cout << "  加速比: " << speedup << endl;
        cout << "  并行效率: " << efficiency << "%" << endl;
    }
    
    // 保存结果
    if (!output_file.empty() || debug_mode) {
        string result_file = output_file.empty() ? "sha256_results.txt" : output_file;
        saveResultsToFile(result_file);
    }
    
    // 清理互斥锁
    pthread_mutex_destroy(&output_mutex);
    pthread_mutex_destroy(&io_mutex);
    
    cout << "\n程序执行完成!" << endl;
    return 0;
}
