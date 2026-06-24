import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

public class CustomSHA256Parallel {
    
    // 全局配置参数
    private static int inputSize = 20 * (1 << 20);  // 默认输入大小 (20MB)
    private static int numReps = 100;               // 默认重复次数
    private static int numThreads = 4;              // 默认线程数
    private static String inputFile = null;         // 输入文件
    private static String outputFile = null;        // 输出文件
    private static boolean debugMode = false;       // 调试模式
    
    // 全局数据
    private static List<byte[]> inputData = new ArrayList<>();
    private static List<String> outputHashes = Collections.synchronizedList(new ArrayList<>());
    
    // 同步工具
    private static final AtomicInteger nextTask = new AtomicInteger(0);
    private static final AtomicLong processedCount = new AtomicLong(0);
    private static final ReentrantLock outputLock = new ReentrantLock();
    private static final ReentrantLock ioLock = new ReentrantLock();
    
    // 线程池
    private static ExecutorService executor;
    
    // SHA256常量
    private static final int SHA256_DIGEST_LENGTH = 32;
    
    // SHA256常量K数组
    private static final int[] K = {
        0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5,
        0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
        0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3,
        0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
        0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc,
        0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
        0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7,
        0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
        0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13,
        0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
        0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3,
        0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
        0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5,
        0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
        0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208,
        0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2
    };
    
    // 自定义SHA256类
    static class SHA256 {
        private byte[] data = new byte[64];
        private int blockLen = 0;
        private long bitLen = 0;
        private int[] state = new int[8]; // A, B, C, D, E, F, G, H
        
        public SHA256() {
            state[0] = 0x6a09e667;
            state[1] = 0xbb67ae85;
            state[2] = 0x3c6ef372;
            state[3] = 0xa54ff53a;
            state[4] = 0x510e527f;
            state[5] = 0x9b05688c;
            state[6] = 0x1f83d9ab;
            state[7] = 0x5be0cd19;
        }
        
        private int rotr(int x, int n) {
            return (x >>> n) | (x << (32 - n));
        }
        
        private int choose(int e, int f, int g) {
            return (e & f) ^ (~e & g);
        }
        
        private int majority(int a, int b, int c) {
            return (a & (b | c)) | (b & c);
        }
        
        private int sig0(int x) {
            return rotr(x, 7) ^ rotr(x, 18) ^ (x >>> 3);
        }
        
        private int sig1(int x) {
            return rotr(x, 17) ^ rotr(x, 19) ^ (x >>> 10);
        }
        
        private void transform() {
            int[] m = new int[64];
            int[] localState = new int[8];
            
            // 将数据分成32位块
            for (int i = 0, j = 0; i < 16; i++, j += 4) {
                m[i] = ((data[j] & 0xFF) << 24) |
                       ((data[j + 1] & 0xFF) << 16) |
                       ((data[j + 2] & 0xFF) << 8) |
                       (data[j + 3] & 0xFF);
            }
            
            // 扩展消息
            for (int k = 16; k < 64; k++) {
                m[k] = sig1(m[k - 2]) + m[k - 7] + sig0(m[k - 15]) + m[k - 16];
            }
            
            // 初始化局部状态
            System.arraycopy(state, 0, localState, 0, 8);
            
            // 主循环
            for (int i = 0; i < 64; i++) {
                int maj = majority(localState[0], localState[1], localState[2]);
                int xorA = rotr(localState[0], 2) ^ rotr(localState[0], 13) ^ rotr(localState[0], 22);
                int ch = choose(localState[4], localState[5], localState[6]);
                int xorE = rotr(localState[4], 6) ^ rotr(localState[4], 11) ^ rotr(localState[4], 25);
                int sum = m[i] + K[i] + localState[7] + ch + xorE;
                int newA = xorA + maj + sum;
                int newE = localState[3] + sum;
                
                localState[7] = localState[6];
                localState[6] = localState[5];
                localState[5] = localState[4];
                localState[4] = newE;
                localState[3] = localState[2];
                localState[2] = localState[1];
                localState[1] = localState[0];
                localState[0] = newA;
            }
            
            // 更新状态
            for (int i = 0; i < 8; i++) {
                state[i] += localState[i];
            }
        }
        
        public void update(byte[] data, int length) {
            for (int i = 0; i < length; i++) {
                this.data[blockLen++] = data[i];
                if (blockLen == 64) {
                    transform();
                    bitLen += 512;
                    blockLen = 0;
                }
            }
        }
        
        public void update(byte[] data) {
            update(data, data.length);
        }
        
        public void update(String str) {
            update(str.getBytes());
        }
        
        private void pad() {
            int i = blockLen;
            int end = blockLen < 56 ? 56 : 64;
            
            data[i++] = (byte) 0x80; // 添加1位
            
            while (i < end) {
                data[i++] = 0;
            }
            
            if (blockLen >= 56) {
                transform();
                Arrays.fill(data, 0, 56, (byte) 0);
            }
            
            bitLen += blockLen * 8L;
            data[63] = (byte) bitLen;
            data[62] = (byte) (bitLen >>> 8);
            data[61] = (byte) (bitLen >>> 16);
            data[60] = (byte) (bitLen >>> 24);
            data[59] = (byte) (bitLen >>> 32);
            data[58] = (byte) (bitLen >>> 40);
            data[57] = (byte) (bitLen >>> 48);
            data[56] = (byte) (bitLen >>> 56);
            
            transform();
        }
        
        public byte[] digest() {
            pad();
            
            byte[] hash = new byte[SHA256_DIGEST_LENGTH];
            for (int i = 0; i < 4; i++) {
                for (int j = 0; j < 8; j++) {
                    hash[i + (j * 4)] = (byte) ((state[j] >>> (24 - i * 8)) & 0xFF);
                }
            }
            return hash;
        }
        
        public static String toString(byte[] hash) {
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(b & 0xFF);
                if (hex.length() == 1) {
                    sb.append('0');
                }
                sb.append(hex);
            }
            return sb.toString();
        }
    }
    
    // SHA256计算函数
    private static String sha256(String str) {
        SHA256 sha256 = new SHA256();
        sha256.update(str);
        byte[] hash = sha256.digest();
        return SHA256.toString(hash);
    }
    
    private static String sha256(byte[] data) {
        SHA256 sha256 = new SHA256();
        sha256.update(data);
        byte[] hash = sha256.digest();
        return SHA256.toString(hash);
    }
    
    // 解析命令行参数
    private static void parseCommandLine(String[] args) {
        System.out.println("解析命令行参数...");
        
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-size":
                    if (i + 1 < args.length) {
                        inputSize = Integer.parseInt(args[++i]);
                        System.out.println("  输入大小: " + inputSize + " 字节");
                    }
                    break;
                    
                case "-nthreads":
                    if (i + 1 < args.length) {
                        numThreads = Integer.parseInt(args[++i]);
                        System.out.println("  线程数: " + numThreads);
                    }
                    break;
                    
                case "-nreps":
                    if (i + 1 < args.length) {
                        numReps = Integer.parseInt(args[++i]);
                        System.out.println("  重复次数: " + numReps);
                    }
                    break;
                    
                case "-input":
                    if (i + 1 < args.length) {
                        inputFile = args[++i];
                        System.out.println("  输入文件: " + inputFile);
                    }
                    break;
                    
                case "-output":
                    if (i + 1 < args.length) {
                        outputFile = args[++i];
                        System.out.println("  输出文件: " + outputFile);
                    }
                    break;
                    
                case "-debug":
                    debugMode = true;
                    System.out.println("  调试模式: 启用");
                    break;
                    
                case "-h":
                case "--help":
                    printHelp();
                    System.exit(0);
                    break;
            }
        }
        
        // 验证参数
        if (numThreads <= 0) {
            System.err.println("错误: 线程数必须大于0");
            System.exit(1);
        }
        if (inputSize <= 0) {
            System.err.println("错误: 输入大小必须大于0");
            System.exit(1);
        }
        if (numReps <= 0) {
            System.err.println("错误: 重复次数必须大于0");
            System.exit(1);
        }
    }
    
    // 打印帮助信息
    private static void printHelp() {
        System.out.println("\n用法: java CustomSHA256Parallel [选项]");
        System.out.println("选项:");
        System.out.println("  -size <N>        输入数据大小(字节) (默认: 20971520)");
        System.out.println("  -nthreads <T>    线程数 (默认: 4)");
        System.out.println("  -nreps <N>       重复次数 (默认: 100)");
        System.out.println("  -input <file>    输入数据文件");
        System.out.println("  -output <file>   输出结果文件");
        System.out.println("  -debug           启用调试模式");
        System.out.println("  -h, --help       显示此帮助信息");
    }
    
    // 生成随机数据
    private static byte[] generateRandomData(int size) {
        Random random = new Random(2333); // 固定种子以确保可重复性
        StringBuilder sb = new StringBuilder(size);
        
        for (int i = 0; i < size; i++) {
            switch (random.nextInt(3)) {
                case 0:
                    sb.append((char) ('A' + random.nextInt(26)));
                    break;
                case 1:
                    sb.append((char) ('a' + random.nextInt(26)));
                    break;
                case 2:
                    sb.append((char) ('0' + random.nextInt(10)));
                    break;
            }
        }
        return sb.toString().getBytes();
    }
    
    // 从文件加载数据
    private static void loadDataFromFile(String filename) throws IOException {
        System.out.println("从文件 " + filename + " 加载数据...");
        
        File file = new File(filename);
        long fileSize = file.length();
        
        if (fileSize <= 0) {
            System.err.println("错误: 文件大小为0或读取失败");
            System.exit(1);
        }
        
        System.out.println("文件大小: " + fileSize + " 字节");
        
        byte[] content = Files.readAllBytes(Paths.get(filename));
        
        // 根据文件大小决定分块策略
        if (fileSize > 100 * 1024 * 1024) { // 大于100MB
            int chunkCount = numThreads * 4;
            int chunkSize = (int) (fileSize / chunkCount);
            
            for (int i = 0; i < chunkCount; i++) {
                int start = i * chunkSize;
                int end = (i == chunkCount - 1) ? (int) fileSize : (i + 1) * chunkSize;
                byte[] chunk = Arrays.copyOfRange(content, start, end);
                inputData.add(chunk);
            }
        } else if (fileSize > 1024 * 1024) { // 大于1MB
            int chunkSize = (int) (fileSize / numThreads);
            for (int i = 0; i < numThreads; i++) {
                int start = i * chunkSize;
                int end = (i == numThreads - 1) ? (int) fileSize : (i + 1) * chunkSize;
                byte[] chunk = Arrays.copyOfRange(content, start, end);
                inputData.add(chunk);
            }
        } else {
            inputData.add(content);
        }
        
        System.out.println("加载了 " + inputData.size() + " 个数据块");
    }
    
    // 保存结果到文件
    private static void saveResultsToFile(String filename) throws IOException {
        System.out.println("保存结果到文件 " + filename + "...");
        
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
            for (int i = 0; i < outputHashes.size(); i++) {
                writer.write("数据块 " + i + " (" + inputData.get(i).length + " 字节):");
                writer.newLine();
                writer.write("  SHA256: " + outputHashes.get(i));
                writer.newLine();
                
                if (debugMode && i < 3 && inputData.get(i).length <= 100) {
                    String preview = new String(inputData.get(i), 0, 
                            Math.min(100, inputData.get(i).length));
                    writer.write("  预览: " + preview);
                    if (inputData.get(i).length > 100) {
                        writer.write("...");
                    }
                    writer.newLine();
                }
                writer.newLine();
            }
        }
        
        System.out.println("结果保存完成");
    }
    
    // 打印进度
    private static void printProgress(int current, int total) {
        if (total == 0) return;
        
        int percentage = (int) ((double) current / total * 100);
        int barWidth = 50;
        int pos = (int) (barWidth * (double) current / total);
        
        ioLock.lock();
        try {
            System.out.print("\r[");
            for (int i = 0; i < barWidth; i++) {
                if (i < pos) {
                    System.out.print("=");
                } else if (i == pos) {
                    System.out.print(">");
                } else {
                    System.out.print(" ");
                }
            }
            System.out.print("] " + percentage + "% (" + current + "/" + total + ")");
            System.out.flush();
            
            if (current == total) {
                System.out.println();
            }
        } finally {
            ioLock.unlock();
        }
    }
    
    // 任务队列模式的工作线程
    static class TaskQueueWorker implements Callable<WorkerResult> {
        private final int threadId;
        private final Map<Integer, String> resultMap = new ConcurrentHashMap<>();
        private long operationsCount = 0;
        
        public TaskQueueWorker(int threadId) {
            this.threadId = threadId;
        }
        
        @Override
        public WorkerResult call() {
            long startTime = System.nanoTime();
            
            while (true) {
                int taskIdx = nextTask.getAndIncrement();
                if (taskIdx >= numReps * inputData.size()) {
                    break;
                }
                
                int dataIdx = taskIdx % inputData.size();
                int repIdx = taskIdx / inputData.size();
                
                // 计算SHA256
                String hash = sha256(inputData.get(dataIdx));
                operationsCount++;
                
                // 保存结果
                if (repIdx == 0) {
                    resultMap.put(dataIdx, hash);
                    
                    // 更新全局结果
                    outputLock.lock();
                    try {
                        if (dataIdx >= outputHashes.size()) {
                            while (outputHashes.size() <= dataIdx) {
                                outputHashes.add("");
                            }
                        }
                        outputHashes.set(dataIdx, hash);
                    } finally {
                        outputLock.unlock();
                    }
                }
                
                // 更新进度
                if (threadId == 0) {
                    long current = processedCount.incrementAndGet();
                    int total = numReps * inputData.size();
                    
                    if (current % Math.max(1, total / 100) == 0) {
                        printProgress((int) current, total);
                    }
                }
            }
            
            long endTime = System.nanoTime();
            double elapsedTime = (endTime - startTime) / 1e9;
            
            return new WorkerResult(elapsedTime, operationsCount, resultMap);
        }
    }
    
    // 数据分区模式的工作线程
    static class PartitionWorker implements Callable<WorkerResult> {
        private final int threadId;
        private final int startIdx;
        private final int endIdx;
        private long operationsCount = 0;
        
        public PartitionWorker(int threadId, int startIdx, int endIdx) {
            this.threadId = threadId;
            this.startIdx = startIdx;
            this.endIdx = endIdx;
        }
        
        @Override
        public WorkerResult call() {
            long startTime = System.nanoTime();
            
            for (int i = startIdx; i < endIdx; i++) {
                for (int rep = 0; rep < numReps; rep++) {
                    String hash = sha256(inputData.get(i));
                    operationsCount++;
                    
                    if (rep == 0) {
                        outputLock.lock();
                        try {
                            if (i >= outputHashes.size()) {
                                while (outputHashes.size() <= i) {
                                    outputHashes.add("");
                                }
                            }
                            outputHashes.set(i, hash);
                        } finally {
                            outputLock.unlock();
                        }
                    }
                    
                    // 更新进度
                    if (threadId == 0) {
                        long current = processedCount.incrementAndGet();
                        int total = numReps * inputData.size();
                        
                        if (current % Math.max(1, total / 100) == 0) {
                            printProgress((int) current, total);
                        }
                    }
                }
            }
            
            long endTime = System.nanoTime();
            double elapsedTime = (endTime - startTime) / 1e9;
            
            return new WorkerResult(elapsedTime, operationsCount, null);
        }
    }
    
    // 工作线程结果类
    static class WorkerResult {
        final double elapsedTime;
        final long operationsCount;
        final Map<Integer, String> resultMap;
        
        WorkerResult(double elapsedTime, long operationsCount, Map<Integer, String> resultMap) {
            this.elapsedTime = elapsedTime;
            this.operationsCount = operationsCount;
            this.resultMap = resultMap;
        }
    }
    
    // 并行执行SHA256计算
    private static double parallelSHA256(boolean useTaskQueue) 
            throws InterruptedException, ExecutionException {
        
        // 重置计数器和任务队列
        nextTask.set(0);
        processedCount.set(0);
        outputHashes.clear();
        for (int i = 0; i < inputData.size(); i++) {
            outputHashes.add("");
        }
        
        // 显示进度条标题
        if (!debugMode) {
            System.out.println("处理进度:");
            printProgress(0, numReps * inputData.size());
        }
        
        // 创建任务列表
        List<Callable<WorkerResult>> tasks = new ArrayList<>();
        
        if (useTaskQueue) {
            // 任务队列模式
            for (int t = 0; t < numThreads; t++) {
                tasks.add(new TaskQueueWorker(t));
            }
        } else {
            // 数据分区模式
            int chunkSize = inputData.size() / numThreads;
            for (int t = 0; t < numThreads; t++) {
                int startIdx = t * chunkSize;
                int endIdx = (t == numThreads - 1) ? inputData.size() : (t + 1) * chunkSize;
                tasks.add(new PartitionWorker(t, startIdx, endIdx));
            }
        }
        
        // 提交任务并等待完成
        List<Future<WorkerResult>> futures = executor.invokeAll(tasks);
        
        // 计算总时间（取最慢线程的时间）
        double maxTime = 0.0;
        long totalOperations = 0;
        
        for (Future<WorkerResult> future : futures) {
            WorkerResult result = future.get();
            if (result.elapsedTime > maxTime) {
                maxTime = result.elapsedTime;
            }
            totalOperations += result.operationsCount;
        }
        
        // 显示完成进度
        if (!debugMode) {
            printProgress(numReps * inputData.size(), numReps * inputData.size());
        }
        
        return maxTime;
    }
    
    // 串行执行SHA256计算（用于性能对比）
    private static double sequentialSHA256() {
        long startTime = System.nanoTime();
        
        outputHashes.clear();
        for (int i = 0; i < inputData.size(); i++) {
            outputHashes.add("");
        }
        
        System.out.println("串行处理...");
        printProgress(0, inputData.size());
        
        for (int i = 0; i < inputData.size(); i++) {
            for (int rep = 0; rep < numReps; rep++) {
                String hash = sha256(inputData.get(i));
                if (rep == 0) {
                    outputHashes.set(i, hash);
                }
            }
            
            if (i % Math.max(1, inputData.size() / 100) == 0) {
                printProgress(i + 1, inputData.size());
            }
        }
        
        printProgress(inputData.size(), inputData.size());
        long endTime = System.nanoTime();
        return (endTime - startTime) / 1e9;
    }
    
    // 打印配置信息
    private static void printConfig() {
        System.out.println("自定义SHA256并行计算程序");
        System.out.println("=======================");
        System.out.println("配置参数:");
        System.out.println("  数据块数量: " + inputData.size());
        
        long totalSize = 0;
        long minSize = Long.MAX_VALUE;
        long maxSize = 0;
        
        for (byte[] data : inputData) {
            long size = data.length;
            totalSize += size;
            minSize = Math.min(minSize, size);
            maxSize = Math.max(maxSize, size);
        }
        
        System.out.printf("  总数据大小: %d 字节 (%.2f MB)%n", 
                totalSize, totalSize / (1024.0 * 1024.0));
        
        if (inputData.size() > 1) {
            System.out.println("  最小数据块: " + minSize + " 字节");
            System.out.println("  最大数据块: " + maxSize + " 字节");
            System.out.printf("  平均数据块: %.2f 字节%n", (double) totalSize / inputData.size());
        }
        
        System.out.println("  线程数: " + numThreads);
        System.out.println("  重复次数: " + numReps);
        System.out.println("  总计算次数: " + (long) numReps * inputData.size());
        
        if (inputFile != null) {
            System.out.println("  输入文件: " + inputFile);
        }
        if (outputFile != null) {
            System.out.println("  输出文件: " + outputFile);
        }
        if (debugMode) {
            System.out.println("  调试模式: 启用");
        }
    }
    
    // 执行测试
    private static void runBenchmark() 
            throws IOException, InterruptedException, ExecutionException {
        
        // 打印配置
        printConfig();
        
        // 执行计算
        System.out.println("\n开始SHA256计算...");
        
        long startTime = System.nanoTime();
        
        // 选择并行模式
        boolean useTaskQueue = (inputData.size() * numReps > 100);
        double elapsed = parallelSHA256(useTaskQueue);
        
        long endTime = System.nanoTime();
        
        // 输出结果
        if (debugMode || inputData.size() <= 3) {
            System.out.println("\n计算结果:");
            int displayCount = Math.min(outputHashes.size(), 3);
            for (int i = 0; i < displayCount; i++) {
                if (debugMode && inputData.get(i).length <= 100) {
                    String content = new String(inputData.get(i));
                    System.out.println("数据块 " + i + " (" + inputData.get(i).length + " 字节)");
                    System.out.println("  内容: " + content);
                }
                System.out.println("  SHA256: " + outputHashes.get(i));
            }
        }
        
        // 验证结果（调试模式）
        if (debugMode && !inputData.isEmpty()) {
            String testString = new String(inputData.get(0));
            if (testString.equals("hello world")) {
                String expected = "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9";
                System.out.println("\n验证结果:");
                System.out.println("  计算值: " + outputHashes.get(0));
                System.out.println("  期望值: " + expected);
                System.out.println("  匹配: " + (outputHashes.get(0).equals(expected) ? "是" : "否"));
            }
        }
        
        // 性能统计
        long totalOperations = (long) numReps * inputData.size();
        double wallTime = (endTime - startTime) / 1e9;
        
        System.out.println("\n性能统计:");
        System.out.printf("  并行时间: %.6f 秒%n", elapsed);
        System.out.printf("  总时间: %.6f 秒%n", wallTime);
        System.out.println("  总操作数: " + totalOperations + " 次SHA256计算");
        System.out.printf("  平均时间: %.6f 秒/次%n", elapsed / totalOperations);
        System.out.printf("  吞吐量: %.2f 次SHA256/秒%n", totalOperations / elapsed);
        
        // 与串行版本对比（小规模数据）
        if (inputData.size() * numReps <= 1000 && !inputData.isEmpty()) {
            System.out.println("\n与串行版本对比...");
            double seqTime = sequentialSHA256();
            double speedup = seqTime / elapsed;
            double efficiency = (speedup / numThreads) * 100;
            
            System.out.printf("  串行时间: %.6f 秒%n", seqTime);
            System.out.printf("  并行时间: %.6f 秒%n", elapsed);
            System.out.printf("  加速比: %.2f%n", speedup);
            System.out.printf("  并行效率: %.2f%%%n", efficiency);
        }
        
        // 保存结果
        String resultFile = outputFile != null ? outputFile : "sha256_results.txt";
        saveResultsToFile(resultFile);
    }
    
    // 清理资源
    private static void cleanup() {
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        System.out.println("\n程序执行完成!");
    }
    
    // 主函数
    public static void main(String[] args) {
        try {
            // 解析命令行参数
            parseCommandLine(args);
            
            // 初始化数据
            if (inputFile != null) {
                loadDataFromFile(inputFile);
            } else {
                System.out.println("生成随机数据...");
                if (debugMode) {
                    inputData.add("hello world".getBytes());
                    System.out.println("调试模式: 使用固定字符串 'hello world'");
                } else {
                    byte[] randomData = generateRandomData(inputSize);
                    inputData.add(randomData);
                    System.out.println("生成了 " + randomData.length + " 字节的随机数据");
                }
            }
            
            // 初始化线程池
            executor = Executors.newFixedThreadPool(numThreads);
            
            // 运行基准测试
            runBenchmark();
            
        } catch (Exception e) {
            System.err.println("程序执行出错: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } finally {
            // 清理资源
            cleanup();
        }
    }
}
