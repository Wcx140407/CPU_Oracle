import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import java.security.*;

/**
 * 并行AES测试程序
 * 支持自定义输入数据集和多线程并行处理
 */
public class ConcurrentAESTest {
    
    // AES相关常量
    private static final int AES_BLOCK_SIZE = 16;
    private static final int AES_KEY_SIZE_128 = 128;
    private static final int AES_KEY_SIZE_192 = 192;
    private static final int AES_KEY_SIZE_256 = 256;
    
    // 最大线程数
    private static final int MAX_THREADS = 256;
    
    // 配置类
    static class Config {
        int keySize;
        int inputSize;
        
        Config(int keySize, int inputSize) {
            this.keySize = keySize;
            this.inputSize = inputSize;
        }
        
        @Override
        public String toString() {
            return String.format("KeySize: %d, InputSize: %d", keySize, inputSize);
        }
    }
    
    // 测试结果类
    static class TestResult {
        int configId;
        int keySize;
        int inputSize;
        int threadId;
        long startTime;
        long endTime;
        long duration;
        
        TestResult(int configId, int keySize, int inputSize, int threadId, 
                   long startTime, long endTime) {
            this.configId = configId;
            this.keySize = keySize;
            this.inputSize = inputSize;
            this.threadId = threadId;
            this.startTime = startTime;
            this.endTime = endTime;
            this.duration = endTime - startTime;
        }
        
        @Override
        public String toString() {
            return String.format("%6d | %8d | %6d | %6d | %12d | %12d | %8d",
                    configId, keySize, inputSize, threadId, 
                    startTime, endTime, duration);
        }
    }
    
    // AES工具类
    static class AESUtil {
        
        /**
         * 生成随机字节数组
         */
        public static byte[] generateRandomBytes(int length) {
            byte[] bytes = new byte[length];
            new Random().nextBytes(bytes);
            return bytes;
        }
        
        /**
         * 生成随机数据集
         */
        public static List<byte[]> generateRandomData(int dataSize, int blockSize) {
            List<byte[]> data = new ArrayList<>(dataSize);
            for (int i = 0; i < dataSize; i++) {
                data.add(generateRandomBytes(blockSize));
            }
            return data;
        }
        
        /**
         * AES加密
         */
        public static byte[] encrypt(byte[] key, byte[] plaintext) throws Exception {
            SecretKeySpec secretKey = new SecretKeySpec(key, "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            return cipher.doFinal(plaintext);
        }
        
        /**
         * AES解密
         */
        public static byte[] decrypt(byte[] key, byte[] ciphertext) throws Exception {
            SecretKeySpec secretKey = new SecretKeySpec(key, "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            return cipher.doFinal(ciphertext);
        }
        
        /**
         * 验证加密解密结果
         */
        public static boolean verify(byte[] key, byte[] plaintext) throws Exception {
            byte[] ciphertext = encrypt(key, plaintext);
            byte[] decrypted = decrypt(key, ciphertext);
            return Arrays.equals(plaintext, decrypted);
        }
    }
    
    // 测试任务类
    static class AESTestTask implements Callable<TestResult> {
        private final int taskId;
        private final int configId;
        private final Config config;
        private final int rounds;
        private final AtomicInteger threadCounter;
        
        public AESTestTask(int taskId, int configId, Config config, 
                          int rounds, AtomicInteger threadCounter) {
            this.taskId = taskId;
            this.configId = configId;
            this.config = config;
            this.rounds = rounds;
            this.threadCounter = threadCounter;
        }
        
        @Override
        public TestResult call() throws Exception {
            int threadId = threadCounter.incrementAndGet();
            long startTime = System.currentTimeMillis();
            
            try {
                // 生成密钥
                int keySizeBytes = config.keySize / 8;
                byte[] key = AESUtil.generateRandomBytes(keySizeBytes);
                
                // 生成测试数据
                List<byte[]> testData = AESUtil.generateRandomData(config.inputSize, AES_BLOCK_SIZE);
                
                // 执行多轮测试
                for (int round = 0; round < rounds; round++) {
                    for (byte[] plaintext : testData) {
                        // 验证加密解密
                        boolean success = AESUtil.verify(key, plaintext);
                        if (!success) {
                            System.err.printf("Thread %d: AES verification failed!\n", threadId);
                        }
                    }
                }
                
                long endTime = System.currentTimeMillis();
                return new TestResult(configId, config.keySize, config.inputSize, 
                                     threadId, startTime, endTime);
                
            } catch (Exception e) {
                System.err.printf("Thread %d encountered error: %s\n", 
                                 threadId, e.getMessage());
                long endTime = System.currentTimeMillis();
                return new TestResult(configId, config.keySize, config.inputSize, 
                                     threadId, startTime, endTime);
            }
        }
    }
    
    /**
     * 从文件读取配置
     */
    public static List<Config> readConfigFromFile(String filename) throws IOException {
        List<Config> configs = new ArrayList<>();
        List<String> lines = Files.readAllLines(Paths.get(filename));
        
        boolean firstLine = true;
        for (String line : lines) {
            // 跳过空行和注释
            if (line.trim().isEmpty() || line.startsWith("#")) {
                continue;
            }
            
            // 跳过标题行
            if (firstLine && (line.toLowerCase().contains("key") || 
                line.toLowerCase().contains("input"))) {
                firstLine = false;
                continue;
            }
            
            // 解析配置行
            String[] parts = line.split(",");
            if (parts.length >= 2) {
                try {
                    int keySize = Integer.parseInt(parts[0].trim());
                    int inputSize = Integer.parseInt(parts[1].trim());
                    
                    // 验证密钥大小
                    if (keySize == AES_KEY_SIZE_128 || 
                        keySize == AES_KEY_SIZE_192 || 
                        keySize == AES_KEY_SIZE_256) {
                        configs.add(new Config(keySize, inputSize));
                    } else {
                        System.err.printf("Warning: Invalid key size %d in line: %s\n", 
                                         keySize, line);
                    }
                } catch (NumberFormatException e) {
                    System.err.printf("Warning: Invalid number format in line: %s\n", line);
                }
            }
        }
        
        return configs;
    }
    
    /**
     * 并发执行AES测试
     */
    public static List<TestResult> testAESConcurrent(int rounds, int numThreads, 
                                                    List<Config> configs) 
            throws InterruptedException, ExecutionException {
        
        if (numThreads <= 0 || numThreads > MAX_THREADS) {
            throw new IllegalArgumentException(
                String.format("Thread count must be between 1 and %d", MAX_THREADS));
        }
        
        System.out.println("开始并发AES测试:");
        System.out.printf("- 线程数: %d\n", numThreads);
        System.out.printf("- 配置数量: %d\n", configs.size());
        
        long totalStartTime = System.currentTimeMillis();
        
        // 创建线程池
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        List<Future<TestResult>> futures = new ArrayList<>();
        AtomicInteger threadCounter = new AtomicInteger(0);
        
        // 提交所有任务
        for (int i = 0; i < configs.size(); i++) {
            AESTestTask task = new AESTestTask(i, i, configs.get(i), 
                                              rounds, threadCounter);
            futures.add(executor.submit(task));
        }
        
        // 等待所有任务完成
        List<TestResult> results = new ArrayList<>();
        for (Future<TestResult> future : futures) {
            results.add(future.get());
        }
        
        // 关闭线程池
        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.MINUTES);
        
        long totalEndTime = System.currentTimeMillis();
        
        // 输出结果
        System.out.println("\n测试结果:");
        System.out.println("配置号 | 密钥大小 | 数据量 | 线程ID | 开始时间(ms) | 结束时间(ms) | 耗时(ms)");
        System.out.println("----------------------------------------------------------------------------");
        
        for (TestResult result : results) {
            System.out.println(result);
        }
        
        System.out.printf("\n总耗时: %d ms\n", totalEndTime - totalStartTime);
        
        return results;
    }
    
    /**
     * 顺序执行AES测试（兼容模式）
     */
    public static List<TestResult> testAESSequential(int rounds, List<Config> configs) 
            throws Exception {
        
        System.out.println("AES顺序测试开始");
        long totalStartTime = System.currentTimeMillis();
        
        List<TestResult> results = new ArrayList<>();
        AtomicInteger threadCounter = new AtomicInteger(0);
        
        for (int i = 0; i < configs.size(); i++) {
            Config config = configs.get(i);
            long configStartTime = System.currentTimeMillis();
            
            System.out.printf("配置 %dx%d 开始: %d\n", 
                             config.keySize, config.inputSize, configStartTime);
            
            // 单线程执行
            AESTestTask task = new AESTestTask(i, i, config, rounds, threadCounter);
            TestResult result = task.call();
            results.add(result);
            
            long configEndTime = System.currentTimeMillis();
            System.out.printf("配置 %dx%d 结束: %d, 耗时: %d ms\n",
                             config.keySize, config.inputSize, 
                             configEndTime, configEndTime - configStartTime);
        }
        
        long totalEndTime = System.currentTimeMillis();
        System.out.printf("AES顺序测试结束: %d\n", totalEndTime);
        System.out.printf("总运行时间: %d ms\n", totalEndTime - totalStartTime);
        
        return results;
    }
    
    /**
     * 生成示例配置文件
     */
    public static void generateSampleConfigFile(String filename) throws IOException {
        List<String> lines = Arrays.asList(
            "# AES测试配置文件",
            "# 格式: key_size,input_size",
            "128,1000",
            "192,2000",
            "256,3000",
            "128,5000",
            "192,10000",
            "256,15000"
        );
        
        Files.write(Paths.get(filename), lines);
        System.out.printf("已生成示例配置文件: %s\n", filename);
    }
    
    /**
     * 保存测试结果到文件
     */
    public static void saveResultsToFile(List<TestResult> results, String filename) 
            throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("config_id,key_size,input_size,thread_id,start_time,end_time,duration_ms");
        
        for (TestResult result : results) {
            lines.add(String.format("%d,%d,%d,%d,%d,%d,%d",
                    result.configId, result.keySize, result.inputSize,
                    result.threadId, result.startTime, result.endTime, result.duration));
        }
        
        Files.write(Paths.get(filename), lines);
        System.out.printf("测试结果已保存到: %s\n", filename);
    }
    
    /**
     * 主函数
     */
    public static void main(String[] args) {
        // 参数检查
        if (args.length < 4) {
            printUsage();
            
            // 如果没有参数，生成示例配置文件
            if (args.length == 0) {
                try {
                    generateSampleConfigFile("aes_config.csv");
                } catch (IOException e) {
                    System.err.println("无法生成示例配置文件: " + e.getMessage());
                }
            }
            return;
        }
        
        try {
            // 解析参数
            int rounds = Integer.parseInt(args[0]);
            int numThreads = Integer.parseInt(args[1]);
            String configFile = args[2];
            String mode = args[3].toLowerCase();
            
            // 验证参数
            if (rounds <= 0) {
                System.err.println("错误：测试轮数必须大于0");
                return;
            }
            
            if (numThreads <= 0 || numThreads > MAX_THREADS) {
                System.err.printf("错误：线程数必须在1到%d之间\n", MAX_THREADS);
                return;
            }
            
            // 读取配置
            List<Config> configs = readConfigFromFile(configFile);
            if (configs.isEmpty()) {
                System.err.println("错误：未读取到有效配置");
                return;
            }
            
            System.out.printf("成功读取 %d 个配置\n", configs.size());
            
            List<TestResult> results;
            
            // 根据运行模式执行测试
            switch (mode) {
                case "con":
                    // 并发模式
                    results = testAESConcurrent(rounds, numThreads, configs);
                    break;
                    
                case "seq":
                    // 顺序模式
                    results = testAESSequential(rounds, configs);
                    break;
                    
                default:
                    System.err.println("错误：运行模式必须是 'seq' 或 'con'");
                    return;
            }
            
            // 保存结果
            saveResultsToFile(results, "aes_test_results.csv");
            
        } catch (NumberFormatException e) {
            System.err.println("错误：参数格式不正确");
            printUsage();
        } catch (FileNotFoundException e) {
            System.err.println("错误：配置文件不存在: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("IO错误: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("程序执行错误: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 打印使用说明
     */
    private static void printUsage() {
        System.out.println("用法: java ConcurrentAESTest <测试轮数> <线程数> <配置文件> <运行模式>");
        System.out.println("\n参数说明:");
        System.out.println("  测试轮数: 每个配置执行的AES测试次数");
        System.out.println("  线程数: 并行执行的线程数 (1-" + MAX_THREADS + ")");
        System.out.println("  配置文件: 包含测试配置的CSV文件");
        System.out.println("  运行模式: 'seq'顺序执行 或 'con'并发执行");
        System.out.println("\n配置文件格式 (CSV):");
        System.out.println("  key_size,input_size");
        System.out.println("  128,1000");
        System.out.println("  192,2000");
        System.out.println("  256,3000");
        System.out.println("\n示例:");
        System.out.println("  java ConcurrentAESTest 5 4 config.csv con");
        System.out.println("  java ConcurrentAESTest 5 1 config.csv seq");
    }
    
    /**
     * 性能基准测试
     */
    public static void benchmark() throws Exception {
        System.out.println("\n=== AES性能基准测试 ===\n");
        
        // 测试不同配置
        List<Config> benchmarkConfigs = Arrays.asList(
            new Config(128, 1000),
            new Config(192, 1000),
            new Config(256, 1000),
            new Config(128, 5000),
            new Config(192, 5000),
            new Config(256, 5000)
        );
        
        // 测试不同线程数
        int[] threadCounts = {1, 2, 4, 8};
        
        for (int threads : threadCounts) {
            System.out.printf("\n=== 线程数: %d ===\n", threads);
            
            long startTime = System.currentTimeMillis();
            testAESConcurrent(3, threads, benchmarkConfigs);
            long endTime = System.currentTimeMillis();
            
            System.out.printf("基准测试总耗时: %d ms\n", endTime - startTime);
        }
    }
}