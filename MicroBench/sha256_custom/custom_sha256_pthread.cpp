// g++ sha256_pthread.cpp -O3 -g -o sha256_pthread -std=c++17 -lpthread

#include <bits/stdc++.h>
#include <sys/time.h>
#include <pthread.h> // 引入 pthread

using namespace std;

#define SHA256_DIGEST_LENGTH 32

// --- 原始 SHA256 类保持不变 ---
class SHA256 {
public:
    SHA256();
    void update(const uint8_t * data, size_t length);
    void update(const std::string &data);
    uint8_t * digest();
    static std::string toString(const uint8_t * digest);

private:
    uint8_t  m_data[64];
    uint32_t m_blocklen;
    uint64_t m_bitlen;
    uint32_t m_state[8]; 
    static constexpr std::array<uint32_t, 64> K = {
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
    void revert(uint8_t * hash);
};

SHA256::SHA256(): m_blocklen(0), m_bitlen(0) {
    m_state[0] = 0x6a09e667; m_state[1] = 0xbb67ae85; m_state[2] = 0x3c6ef372; m_state[3] = 0xa54ff53a;
    m_state[4] = 0x510e527f; m_state[5] = 0x9b05688c; m_state[6] = 0x1f83d9ab; m_state[7] = 0x5be0cd19;
}
void SHA256::update(const uint8_t * data, size_t length) {
    for (size_t i = 0 ; i < length ; i++) {
        m_data[m_blocklen++] = data[i];
        if (m_blocklen == 64) {
            transform();
            m_bitlen += 512;
            m_blocklen = 0;
        }
    }
}
void SHA256::update(const std::string &data) {
    update(reinterpret_cast<const uint8_t*> (data.c_str()), data.size());
}
uint8_t * SHA256::digest() {
    uint8_t * hash = new uint8_t[SHA256_DIGEST_LENGTH];
    pad();
    revert(hash);
    return hash;
}
uint32_t SHA256::rotr(uint32_t x, uint32_t n) { return (x >> n) | (x << (32 - n)); }
uint32_t SHA256::choose(uint32_t e, uint32_t f, uint32_t g) { return (e & f) ^ (~e & g); }
uint32_t SHA256::majority(uint32_t a, uint32_t b, uint32_t c) { return (a & (b | c)) | (b & c); }
uint32_t SHA256::sig0(uint32_t x) { return SHA256::rotr(x, 7) ^ SHA256::rotr(x, 18) ^ (x >> 3); }
uint32_t SHA256::sig1(uint32_t x) { return SHA256::rotr(x, 17) ^ SHA256::rotr(x, 19) ^ (x >> 10); }
void SHA256::transform() {
    uint32_t maj, xorA, ch, xorE, sum, newA, newE, m[64], state[8];
    for (uint8_t i = 0, j = 0; i < 16; i++, j += 4) {
        m[i] = (m_data[j] << 24) | (m_data[j + 1] << 16) | (m_data[j + 2] << 8) | (m_data[j + 3]);
    }
    for (uint8_t k = 16 ; k < 64; k++) {
        m[k] = SHA256::sig1(m[k - 2]) + m[k - 7] + SHA256::sig0(m[k - 15]) + m[k - 16];
    }
    for(uint8_t i = 0 ; i < 8 ; i++) state[i] = m_state[i];
    for (uint8_t i = 0; i < 64; i++) {
        maj   = SHA256::majority(state[0], state[1], state[2]);
        xorA  = SHA256::rotr(state[0], 2) ^ SHA256::rotr(state[0], 13) ^ SHA256::rotr(state[0], 22);
        ch = choose(state[4], state[5], state[6]);
        xorE  = SHA256::rotr(state[4], 6) ^ SHA256::rotr(state[4], 11) ^ SHA256::rotr(state[4], 25);
        sum  = m[i] + K[i] + state[7] + ch + xorE;
        newA = xorA + maj + sum;
        newE = state[3] + sum;
        state[7] = state[6]; state[6] = state[5]; state[5] = state[4]; state[4] = newE;
        state[3] = state[2]; state[2] = state[1]; state[1] = state[0]; state[0] = newA;
    }
    for(uint8_t i = 0 ; i < 8 ; i++) m_state[i] += state[i];
}
void SHA256::pad() {
    uint64_t i = m_blocklen;
    uint8_t end = m_blocklen < 56 ? 56 : 64;
    m_data[i++] = 0x80;
    while (i < end) m_data[i++] = 0x00;
    if(m_blocklen >= 56) {
        transform();
        memset(m_data, 0, 56);
    }
    m_bitlen += m_blocklen * 8;
    m_data[63] = m_bitlen; m_data[62] = m_bitlen >> 8; m_data[61] = m_bitlen >> 16;
    m_data[60] = m_bitlen >> 24; m_data[59] = m_bitlen >> 32; m_data[58] = m_bitlen >> 40;
    m_data[57] = m_bitlen >> 48; m_data[56] = m_bitlen >> 56;
    transform();
}
void SHA256::revert(uint8_t * hash) {
    for (uint8_t i = 0 ; i < 4 ; i++) {
        for(uint8_t j = 0 ; j < 8 ; j++) {
            hash[i + (j * 4)] = (m_state[j] >> (24 - i * 8)) & 0x000000ff;
        }
    }
}
std::string SHA256::toString(const uint8_t * digest) {
    std::stringstream s;
    s << std::setfill('0') << std::hex;
    for(uint8_t i = 0 ; i < SHA256_DIGEST_LENGTH ; i++) {
        s << std::setw(2) << (unsigned int) digest[i];
    }
    return s.str();
}

// 线程安全的辅助函数：每次调用都实例化一个新的 SHA256 对象
std::string sha256(const std::string &str) {
    SHA256 sha256;
    sha256.update(str);
    uint8_t * hash = sha256.digest();
    std::string ret = sha256.toString(hash);
    delete[] hash;
    return ret;
}

// --- 并行处理部分 ---

// 用于生成随机数据的辅助函数
std::string randstr(const int len) {
    static const char alphanum[] =
        "0123456789"
        "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        "abcdefghijklmnopqrstuvwxyz";
    std::string s;
    s.resize(len);
    for (int i = 0; i < len; ++i) {
        s[i] = alphanum[rand() % (sizeof(alphanum) - 1)];
    }
    return s;
}

// 传递给线程的结构体参数
struct ThreadData {
    int thread_id;
    int start_index;
    int end_index;
    const std::vector<std::string>* inputs; // 只读输入
    std::vector<std::string>* outputs;      // 写入输出
};

// 线程工作函数
void* worker_routine(void* arg) {
    ThreadData* data = (ThreadData*)arg;
    
    // 处理分配给该线程的数据片段
    for (int i = data->start_index; i < data->end_index; ++i) {
        // 调用 SHA256 计算
        (*data->outputs)[i] = sha256((*data->inputs)[i]);
    }
    
    return NULL;
}

int main(int argc, char *argv[]) {
    // 默认参数
    int num_threads = 1;
    std::vector<std::string> inputs;
    std::vector<std::string> outputs;
    
    if (argc < 3) {
        std::cerr << "Usage: " << argv[0] << " <GENERATE|filename> <num_threads> [count] [length]" << std::endl;
        std::cerr << "Example 1 (File): " << argv[0] << " data.txt 4" << std::endl;
        std::cerr << "Example 2 (Random): " << argv[0] << " GENERATE 4 100000 1024" << std::endl;
        return 1;
    }

    std::string input_mode = argv[1];
    num_threads = atoi(argv[2]);
    if (num_threads <= 0) num_threads = 1;

    // --- 1. 准备数据 ---
    std::cout << "Preparing data..." << std::endl;
    
    if (input_mode == "GENERATE") {
        int count = (argc >= 4) ? atoi(argv[3]) : 10000;
        int len = (argc >= 5) ? atoi(argv[4]) : 1024;
        std::cout << "Generating " << count << " random strings (len=" << len << ")..." << std::endl;
        srand(time(NULL));
        inputs.reserve(count);
        for(int i=0; i<count; ++i) {
            inputs.push_back(randstr(len));
        }
    } else {
        // 从文件读取
        std::ifstream infile(input_mode);
        if (!infile.good()) {
            std::cerr << "Error: Cannot open file " << input_mode << std::endl;
            return 1;
        }
        std::string line;
        while (std::getline(infile, line)) {
            if(!line.empty()) inputs.push_back(line);
        }
        std::cout << "Loaded " << inputs.size() << " lines from file." << std::endl;
    }

    size_t total_items = inputs.size();
    if (total_items == 0) {
        std::cerr << "No data to process." << std::endl;
        return 0;
    }

    outputs.resize(total_items); // 预分配输出空间

    // --- 2. 设置线程 ---
    pthread_t* threads = new pthread_t[num_threads];
    ThreadData* thread_args = new ThreadData[num_threads];

    int items_per_thread = total_items / num_threads;
    int remainder = total_items % num_threads;
    int current_start = 0;

    struct timeval tv1, tv2;
    gettimeofday(&tv1, NULL);

    std::cout << "Starting " << num_threads << " threads to process " << total_items << " items..." << std::endl;

    for (int i = 0; i < num_threads; ++i) {
        thread_args[i].thread_id = i;
        thread_args[i].inputs = &inputs;
        thread_args[i].outputs = &outputs;
        thread_args[i].start_index = current_start;
        
        // 分配任务：最后一个线程处理余数，或者简单的均分+余数分配
        int count = items_per_thread + (i < remainder ? 1 : 0);
        thread_args[i].end_index = current_start + count;
        current_start += count;

        if (count > 0) {
            int rc = pthread_create(&threads[i], NULL, worker_routine, (void*)&thread_args[i]);
            if (rc) {
                std::cerr << "Error: unable to create thread, " << rc << std::endl;
                exit(-1);
            }
        }
    }

    // --- 3. 等待结束 ---
    for (int i = 0; i < num_threads; ++i) {
        // 只有分配了任务才需要 join（虽然 join 一个没启动的会有问题，但上面逻辑保证 count>0 才启动，
        // 不过为了严谨，这里假设所有线程ID都尝试启动了，实际中通常直接 join 所有 ID）
        // 简单起见，如果 count 为 0，该线程并没有实际启动，但这里我们逻辑上假设线程数 <= 数据量
        if (thread_args[i].start_index < thread_args[i].end_index) {
             pthread_join(threads[i], NULL);
        }
    }

    gettimeofday(&tv2, NULL);
    double elapsed = (double)(tv2.tv_sec - tv1.tv_sec) + (double)(tv2.tv_usec - tv1.tv_usec) * 1.e-6;

    // --- 4. 结果验证与输出 ---
    // 打印前几个结果作为验证
    int preview_count = std::min((size_t)3, total_items);
    for(int i=0; i<preview_count; ++i) {
        std::string preview_src = inputs[i].length() > 20 ? inputs[i].substr(0, 20) + "..." : inputs[i];
        std::cout << "Result[" << i << "]: SHA256(" << preview_src << ") = " << outputs[i] << std::endl;
    }

    printf("Total processing time = %lf seconds\n", elapsed);
    printf("Throughput = %lf hashes/sec\n", total_items / elapsed);

    // 清理
    delete[] threads;
    delete[] thread_args;

    return 0;
}
