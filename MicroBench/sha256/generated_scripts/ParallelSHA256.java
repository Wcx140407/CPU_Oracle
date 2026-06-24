import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

public class ParallelSHA256 {
    
    // 全局配置参数
    private static int inputSize = 20 * (1 << 20);  // 默认输入大小 (20MB)
    private static int numReps = 100;               // 默认重复次数
    private static int numThreads = 4;              // 默认线程数
    private static String inputFile = null;         // 输入文件
    private static String outputFile = null;        // 输出文件
    
    // 全局数据
    private static List<byte[]> inputData = Collections.synchronizedList(new ArrayList<>());
    private static List<String> outputHashes = Collections.synchronizedList(new ArrayList<>());
    
    // 同步工具
    private static final AtomicInteger nextTask = new AtomicInteger(0);
    private static final ReentrantLock outputLock = new ReentrantLock();
    
    // 线程池
    private static ExecutorService executor;
    
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
        System.out.println("\n用法: java ParallelSHA256 [选项]");
        System.out.println("选项:");
        System.out.println("  -size <N>        输入数据大小(字节) (默认: 20971520)");
        System.out.println("  -nthreads <T>    线程数 (默认: 4)");
        System.out.println("  -nreps <N>       重复次数 (默认: 100)");
        System.out.println("  -input <file>    输入数据文件");
        System.out.println("  -output <file>   输出结果文件");
        System.out.println("  -h, --help       显示此帮助信息");
    }
    
    // SHA256计算函数
    private static String sha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            
            // 转换为十六进制字符串
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
            
        } catch (NoSuchAlgorithmException e) {
            System.err.println("错误: SHA-256算法不可用");
            return "";
        }
    }
    
    // 生成随机数据
    private static void generateRandomData(int size) {
        System.out.println("生成随机数据...");
        
        Random random = new Random(2333); // 固定种子以确保可重复性
        StringBuilder sb = new StringBuilder(size);
        
        // 生成指定大小的数据
        for (int i = 0; i < size; i++) {
            switch (random.nextInt(3)) {
                case 0:
                    sb.append((char)('A' + random.nextInt(26)));
                    break;
                case 1:
                    sb.append((char)('a' + random.nextInt(26)));
                    break;
                case 2:
                    sb.append((char)('0' + random.nextInt(10)));
                    break;
            }
        }
        
        inputData.add(sb.toString().getBytes());
        System.out.println("生成了 " + inputData.get(0).length + " 字节的随机数据");
    }
    
    // 从文件加载数据
    private static void loadDataFromFile(String filename) throws IOException {
        System.out.println("从文件 " + filename + " 加载数据...");
        
        File file = new File(filename);
        long fileSize = file.length();
        
        // 读取整个文件
        byte[] content = Files.readAllBytes(Paths.get(filename));
        
        // 如果文件很大，分割成多个块
        if (fileSize > 1024 * 1024) { // 如果大于1MB，分割
            int chunkSize = (int)(fileSize / numThreads);
            for (int i = 0; i < numThreads; i++) {
                int start = i * chunkSize;
                int end = (i == numThreads - 1) ? (int)fileSize : (i + 1) * chunkSize;
                byte[] chunk = Arrays.copyOfRange(content, start, end);
                inputData.add(chunk);
            }
        } else {
            inputData.add(content);
        }
        
        System.out.println("加载了 " + inputData.size() + " 个数据块，总大小 " + fileSize + " 字节");
    }
    
    // 任务队列模式的工作线程
    static class TaskQueueWorker implements Callable<Double> {
        private final int threadId;
        private final List<String> localHashes;
        private final Map<Integer, String> resultMap;
        
        public TaskQueueWorker(int threadId) {
            this.threadId = threadId;
            this.localHashes = new ArrayList<>(Collections.nCopies(inputData.size(), ""));
            this.resultMap = new ConcurrentHashMap<>();
        }
        
        @Override
        public Double call() {
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
                
                // 保存结果
                if (repIdx == 0) { // 只保存第一次的结果
                    resultMap.put(dataIdx, hash);
                    
                    if (dataIdx < localHashes.size()) {
                        localHashes.set(dataIdx, hash);
                    } else {
                        while (localHashes.size() <= dataIdx) {
                            localHashes.add("");
                        }
                        localHashes.set(dataIdx, hash);
                    }
                }
                
                // 进度报告
                if (repIdx == 0 && threadId == 0 && taskIdx % 10 == 0) {
                    System.out.printf("处理进度: %d/%d%n", 
                            taskIdx + 1, numReps * inputData.size());
                }
            }
            
            long endTime = System.nanoTime();
            return (endTime - startTime) / 1e9; // 转换为秒
        }
        
        public List<String> getLocalHashes() {
            return localHashes;
        }
        
        public Map<Integer, String> getResultMap() {
            return resultMap;
        }
    }
    
    // 数据分区模式的工作线程
    static class PartitionWorker implements Callable<Double> {
        private final int threadId;
        private final int startIdx;
        private final int endIdx;
        private final List<String> localHashes;
        
        public PartitionWorker(int threadId, int startIdx, int endIdx) {
            this.threadId = threadId;
            this.startIdx = startIdx;
            this.endIdx = endIdx;
            this.localHashes = new ArrayList<>(Collections.nCopies(endIdx - startIdx, ""));
        }
        
        @Override
        public Double call() {
            long startTime = System.nanoTime();
            
            // 处理分配的数据分区
            for (int i = startIdx; i < endIdx; i++) {
                for (int rep = 0; rep < numReps; rep++) {
                    String hash = sha256(inputData.get(i));
                    
                    if (rep == 0) { // 只保存第一次的结果
                        outputLock.lock();
                        try {
                            // 同步更新全局结果
                            if (i >= outputHashes.size()) {
                                while (outputHashes.size() <= i) {
                                    outputHashes.add("");
                                }
                            }
                            outputHashes.set(i, hash);
                        } finally {
                            outputLock.unlock();
                        }
                        
                        // 保存到本地
                        localHashes.set(i - startIdx, hash);
                    }
                }
                
                // 进度报告
                int totalChunks = endIdx - startIdx;
                if (threadId == 0 && i % Math.max(1, totalChunks / 10) == 0) {
                    int progress = i - startIdx + 1;
                    System.out.printf("线程 %d 处理进度: %d/%d%n", 
                            threadId, progress, totalChunks);
                }
            }
            
            long endTime = System.nanoTime();
            return (endTime - startTime) / 1e9; // 转换为秒
        }
        
        public List<String> getLocalHashes() {
            return localHashes;
        }
    }
    
    // 并行执行SHA256计算
    private static double parallelSHA256(boolean useTaskQueue) 
            throws InterruptedException, ExecutionException {
        
        // 重置任务队列
        nextTask.set(0);
        
        // 初始化输出哈希列表
        outputHashes.clear();
        for (int i = 0; i < inputData.size(); i++) {
            outputHashes.add("");
        }
        
        // 创建任务列表
        List<Callable<Double>> tasks = new ArrayList<>();
        List<Object> workers = new ArrayList<>();
        
        if (useTaskQueue) {
            // 任务队列模式
            for (int t = 0; t < numThreads; t++) {
                TaskQueueWorker worker = new TaskQueueWorker(t);
                tasks.add(worker);
                workers.add(worker);
            }
        } else {
            // 数据分区模式
            int chunkSize = inputData.size() / numThreads;
            for (int t = 0; t < numThreads; t++) {
                int startIdx = t * chunkSize;
                int endIdx = (t == numThreads - 1) ? inputData.size() : (t + 1) * chunkSize;
                PartitionWorker worker = new PartitionWorker(t, startIdx, endIdx);
                tasks.add(worker);
                workers.add(worker);
            }
        }
        
        // 提交任务并等待完成
        long startTime = System.nanoTime();
        List<Future<Double>> futures = executor.invokeAll(tasks);
        long endTime = System.nanoTime();
        
        // 合并结果（任务队列模式需要额外处理）
        if (useTaskQueue) {
            for (Object worker : workers) {
                TaskQueueWorker taskWorker = (TaskQueueWorker) worker;
                Map<Integer, String> resultMap = taskWorker.getResultMap();
                for (Map.Entry<Integer, String> entry : resultMap.entrySet()) {
                    int idx = entry.getKey();
                    String hash = entry.getValue();
                    if (idx < outputHashes.size() && !hash.isEmpty()) {
                        outputHashes.set(idx, hash);
                    }
                }
            }
        }
        
        // 计算总时间（取最慢线程的时间）
        double maxTime = 0.0;
        for (Future<Double> future : futures) {
            double threadTime = future.get();
            if (threadTime > maxTime) {
                maxTime = threadTime;
            }
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
        
        for (int i = 0; i < inputData.size(); i++) {
            for (int rep = 0; rep < numReps; rep++) {
                String hash = sha256(inputData.get(i));
                if (rep == 0) {
                    outputHashes.set(i, hash);
                }
            }
            
            // 进度报告
            if (i % Math.max(1, inputData.size() / 10) == 0) {
                System.out.printf("串行处理进度: %d/%d%n", i + 1, inputData.size());
            }
        }
        
        long endTime = System.nanoTime();
        return (endTime - startTime) / 1e9; // 转换为秒
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
                
                // 只显示前3个小数据块的内容
                if (i < 3 && inputData.get(i).length <= 100) {
                    String content = new String(inputData.get(i));
                    if (content.length() > 100) {
                        content = content.substring(0, 100) + "...";
                    }
                    writer.write("  内容: " + content);
                    writer.newLine();
                }
                writer.newLine();
            }
        }
        
        System.out.println("结果保存完成");
    }
    
    // 打印配置信息
    private static void printConfig() {
        System.out.println("SHA256并行计算程序");
        System.out.println("=================");
        System.out.println("配置参数:");
        System.out.println("  数据块数量: " + inputData.size());
        if (!inputData.isEmpty()) {
            long totalSize = 0;
            for (byte[] data : inputData) {
                totalSize += data.length;
            }
            System.out.println("  总数据大小: " + totalSize + " 字节");
            System.out.println("  最大数据块: " + inputData.get(0).length + " 字节");
        }
        System.out.println("  线程数: " + numThreads);
        System.out.println("  重复次数: " + numReps);
        System.out.println("  总计算次数: " + (long)numReps * inputData.size());
        if (inputFile != null) {
            System.out.println("  输入文件: " + inputFile);
        }
        if (outputFile != null) {
            System.out.println("  输出文件: " + outputFile);
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
        
        // 选择并行模式：true为任务队列模式，false为数据分区模式
        boolean useTaskQueue = (inputData.size() * numReps > 100);
        double elapsed = parallelSHA256(useTaskQueue);
        
        long endTime = System.nanoTime();
        
        // 输出结果
        System.out.println("\n计算结果:");
        int displayCount = Math.min(outputHashes.size(), 3);
        for (int i = 0; i < displayCount; i++) {
            System.out.println("数据块 " + i + " 的SHA256: " + outputHashes.get(i));
        }
        
        // 性能统计
        long totalOperations = (long)numReps * inputData.size();
        System.out.println("\n性能统计:");
        System.out.printf("  总时间: %.6f 秒%n", elapsed);
        System.out.println("  总操作数: " + totalOperations + " 次SHA256计算");
        System.out.printf("  平均时间: %.6f 秒/次%n", elapsed / totalOperations);
        System.out.printf("  吞吐量: %.2f 次SHA256/秒%n", totalOperations / elapsed);
        
        // 与串行版本对比（可选）
        if (inputData.size() * numReps <= 1000) { // 小规模数据才进行对比
            System.out.println("\n与串行版本对比...");
            double seqTime = sequentialSHA256();
            System.out.printf("  串行时间: %.6f 秒%n", seqTime);
            System.out.printf("  并行时间: %.6f 秒%n", elapsed);
            System.out.printf("  加速比: %.2f%n", seqTime / elapsed);
            System.out.printf("  并行效率: %.2f%%%n", (seqTime / elapsed) / numThreads * 100);
        }
        
        // 保存结果
        String resultFile = (outputFile != null) ? outputFile : "sha256_results.txt";
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
        
        System.out.println("\n测试完成!");
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
                generateRandomData(inputSize);
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
