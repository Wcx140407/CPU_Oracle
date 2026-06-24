import java.io.*;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

public class ParallelStreamBenchmark {
    
    // 全局配置参数
    private static int streamArraySize = 10000000;  // 默认数组大小
    private static int ntimes = 100;                // 默认运行次数
    private static int offset = 0;                  // 偏移量
    private static int numThreads = 4;              // 默认线程数
    
    // 数据类型
    private static double[] a;
    private static double[] b;
    private static double[] c;
    
    // 时间统计
    private static double[] avgtime = new double[4];
    private static double[] maxtime = new double[4];
    private static double[] mintime = new double[4];
    
    // 测试标签
    private static final String[] labels = {
        "Copy:      ",
        "Scale:     ",
        "Add:       ",
        "Triad:     "
    };
    
    // 数据量（字节）
    private static double[] bytes = new double[4];
    
    // 线程池
    private static ExecutorService executor;
    
    // 锁和同步工具
    private static final ReentrantLock lock = new ReentrantLock();
    private static CyclicBarrier barrier;
    
    // 命令行参数
    private static String inputFile = null;
    private static String outputFile = null;
    
    // 测试任务类
    static class StreamTask implements Callable<Double> {
        private final int operation;     // 0:Copy, 1:Scale, 2:Add, 3:Triad
        private final int taskId;        // 任务ID
        private final int startIdx;      // 起始索引
        private final int endIdx;        // 结束索引
        private final double scalar;     // 缩放因子
        
        public StreamTask(int operation, int taskId, int startIdx, int endIdx, double scalar) {
            this.operation = operation;
            this.taskId = taskId;
            this.startIdx = startIdx;
            this.endIdx = endIdx;
            this.scalar = scalar;
        }
        
        @Override
        public Double call() throws Exception {
            long startTime = System.nanoTime();
            
            switch (operation) {
                case 0: // Copy: c = a
                    for (int j = startIdx; j < endIdx; j++) {
                        c[j] = a[j];
                    }
                    break;
                    
                case 1: // Scale: b = scalar * c
                    for (int j = startIdx; j < endIdx; j++) {
                        b[j] = scalar * c[j];
                    }
                    break;
                    
                case 2: // Add: c = a + b
                    for (int j = startIdx; j < endIdx; j++) {
                        c[j] = a[j] + b[j];
                    }
                    break;
                    
                case 3: // Triad: a = b + scalar * c
                    for (int j = startIdx; j < endIdx; j++) {
                        a[j] = b[j] + scalar * c[j];
                    }
                    break;
            }
            
            long endTime = System.nanoTime();
            return (endTime - startTime) / 1e9; // 转换为秒
        }
    }
    
    // 解析命令行参数
    private static void parseCommandLine(String[] args) {
        System.out.println("解析命令行参数...");
        
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-size":
                    if (i + 1 < args.length) {
                        streamArraySize = Integer.parseInt(args[++i]);
                        System.out.println("  数组大小: " + streamArraySize);
                    }
                    break;
                    
                case "-nthreads":
                    if (i + 1 < args.length) {
                        numThreads = Integer.parseInt(args[++i]);
                        System.out.println("  线程数: " + numThreads);
                    }
                    break;
                    
                case "-ntimes":
                    if (i + 1 < args.length) {
                        ntimes = Integer.parseInt(args[++i]);
                        System.out.println("  迭代次数: " + ntimes);
                    }
                    break;
                    
                case "-offset":
                    if (i + 1 < args.length) {
                        offset = Integer.parseInt(args[++i]);
                        System.out.println("  偏移量: " + offset);
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
        if (streamArraySize <= 0) {
            System.err.println("错误: 数组大小必须大于0");
            System.exit(1);
        }
    }
    
    // 打印帮助信息
    private static void printHelp() {
        System.out.println("\n用法: java ParallelStreamBenchmark [选项]");
        System.out.println("选项:");
        System.out.println("  -size <N>        数组大小 (默认: 10000000)");
        System.out.println("  -nthreads <T>    线程数 (默认: 4)");
        System.out.println("  -ntimes <N>      迭代次数 (默认: 100)");
        System.out.println("  -offset <O>      偏移量 (默认: 0)");
        System.out.println("  -input <file>    输入数据文件");
        System.out.println("  -output <file>   输出结果文件");
        System.out.println("  -h, --help       显示此帮助信息");
    }
    
    // 初始化数组
    private static void initializeArrays() {
        System.out.println("\n初始化数组...");
        
        int totalSize = streamArraySize + offset;
        
        a = new double[totalSize];
        b = new double[totalSize];
        c = new double[totalSize];
        
        // 初始化数据
        if (inputFile != null) {
            loadDataFromFile(inputFile, a, streamArraySize);
            loadDataFromFile(inputFile, b, streamArraySize); // 这里简化处理，实际应该有不同的文件
        } else {
            generateRandomData();
        }
        
        System.out.println("  数组初始化完成");
        System.out.printf("  每个数组大小: %.2f MB\n", 
                          (double)(Double.BYTES * streamArraySize) / (1024.0 * 1024.0));
    }
    
    // 生成随机数据
    private static void generateRandomData() {
        System.out.println("  生成随机数据...");
        Random random = new Random(42); // 固定种子以确保可重复性
        
        for (int j = 0; j < streamArraySize; j++) {
            a[j] = 1.0;
            b[j] = 2.0;
            c[j] = 0.0;
        }
    }
    
    // 从文件加载数据
    private static void loadDataFromFile(String filename, double[] array, int size) {
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            System.out.println("  从文件 " + filename + " 加载数据...");
            
            String line;
            int count = 0;
            
            while ((line = reader.readLine()) != null && count < size) {
                String[] values = line.trim().split("\\s+");
                for (String value : values) {
                    if (count >= size) break;
                    array[count++] = Double.parseDouble(value);
                }
            }
            
            System.out.println("  加载了 " + count + " 个数据点");
            
            // 如果文件数据不足，用默认值填充
            for (int i = count; i < size; i++) {
                array[i] = 0.0;
            }
            
        } catch (IOException e) {
            System.err.println("错误: 无法读取文件 " + filename + " - " + e.getMessage());
            System.exit(1);
        } catch (NumberFormatException e) {
            System.err.println("错误: 文件格式不正确 - " + e.getMessage());
            System.exit(1);
        }
    }
    
    // 保存数据到文件
    private static void saveDataToFile(String filename, double[] array, int size) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
            System.out.println("  保存结果到文件 " + filename + "...");
            
            int elementsToSave = Math.min(size, 1000); // 只保存前1000个值
            for (int i = 0; i < elementsToSave; i++) {
                writer.write(String.format("%.6f ", array[i]));
                if ((i + 1) % 10 == 0) {
                    writer.newLine();
                }
            }
            
            System.out.println("  数据保存完成");
            
        } catch (IOException e) {
            System.err.println("警告: 无法写入文件 " + filename + " - " + e.getMessage());
        }
    }
    
    // 检查时钟精度
    private static int checkTick() {
        final int M = 20;
        double[] timesFound = new double[M];
        
        // 收集M个不同的时间值
        for (int i = 0; i < M; i++) {
            long t1 = System.nanoTime();
            long t2;
            do {
                t2 = System.nanoTime();
            } while ((t2 - t1) < 1000); // 等待至少1微秒
            timesFound[i] = t2 / 1e9; // 转换为秒
        }
        
        // 计算最小时间差（微秒）
        int minDelta = 1000000;
        for (int i = 1; i < M; i++) {
            int delta = (int)(1e6 * (timesFound[i] - timesFound[i-1]));
            minDelta = Math.min(minDelta, Math.max(delta, 0));
        }
        
        return minDelta;
    }
    
    // 并行执行STREAM测试
    private static double parallelStreamTest(int operation, double scalar) throws InterruptedException, ExecutionException {
        // 创建任务列表
        int chunkSize = streamArraySize / numThreads;
        Future<Double>[] futures = new Future[numThreads];
        
        // 提交任务到线程池
        for (int t = 0; t < numThreads; t++) {
            int startIdx = t * chunkSize;
            int endIdx = (t == numThreads - 1) ? streamArraySize : (t + 1) * chunkSize;
            
            StreamTask task = new StreamTask(operation, t, startIdx, endIdx, scalar);
            futures[t] = executor.submit(task);
        }
        
        // 收集结果并取最慢线程的时间
        double maxTime = 0.0;
        for (int t = 0; t < numThreads; t++) {
            double taskTime = futures[t].get();
            maxTime = Math.max(maxTime, taskTime);
        }
        
        return maxTime;
    }
    
    // 预热测试
    private static void warmupTest() {
        System.out.println("\n预热测试...");
        
        long startTime = System.nanoTime();
        for (int j = 0; j < streamArraySize; j++) {
            a[j] = 2.0 * a[j];
        }
        double warmupTime = (System.nanoTime() - startTime) / 1e9;
        
        System.out.printf("预热测试时间: %.6f 秒\n", warmupTime);
    }
    
    // 打印配置信息
    private static void printConfig() {
        System.out.println("STREAM并行基准测试");
        System.out.println("==================");
        System.out.println("配置参数:");
        System.out.println("  数组大小: " + streamArraySize + " 元素");
        System.out.println("  数据类型大小: " + Double.BYTES + " 字节");
        System.out.printf("  总内存需求: %.2f MB\n", 
                          (3.0 * Double.BYTES * streamArraySize) / (1024.0 * 1024.0));
        System.out.println("  线程数: " + numThreads);
        System.out.println("  迭代次数: " + ntimes);
        System.out.println("  偏移量: " + offset);
    }
    
    // 执行测试
    private static void runBenchmark() throws InterruptedException, ExecutionException {
        // 计算数据量
        for (int i = 0; i < 4; i++) {
            bytes[i] = 2.0 * Double.BYTES * streamArraySize;
        }
        
        // 初始化时间统计数组
        for (int i = 0; i < 4; i++) {
            mintime[i] = Double.MAX_VALUE;
            maxtime[i] = 0.0;
            avgtime[i] = 0.0;
        }
        
        // 检查时钟精度
        int quantum = checkTick();
        System.out.printf("\n时钟精度: %d 微秒\n", quantum);
        
        // 预热
        warmupTest();
        
        // 执行STREAM测试
        System.out.println("\n开始STREAM测试...");
        double[][] times = new double[4][ntimes];
        double scalar = 3.0;
        
        for (int k = 0; k < ntimes; k++) {
            System.out.printf("第 %d 次迭代...\n", k + 1);
            
            // Copy
            times[0][k] = parallelStreamTest(0, scalar);
            
            // Scale
            times[1][k] = parallelStreamTest(1, scalar);
            
            // Add
            times[2][k] = parallelStreamTest(2, scalar);
            
            // Triad
            times[3][k] = parallelStreamTest(3, scalar);
            
            // 显示本次迭代结果
            System.out.printf("  本次迭代时间: Copy=%.6fs, Scale=%.6fs, Add=%.6fs, Triad=%.6fs\n",
                             times[0][k], times[1][k], times[2][k], times[3][k]);
        }
        
        // 计算结果（跳过第一次迭代）
        for (int k = 1; k < ntimes; k++) {
            for (int j = 0; j < 4; j++) {
                avgtime[j] += times[j][k];
                if (times[j][k] < mintime[j]) mintime[j] = times[j][k];
                if (times[j][k] > maxtime[j]) maxtime[j] = times[j][k];
            }
        }
        
        // 输出结果
        System.out.println("\n-------------------------------------------------------------");
        System.out.println("测试结果汇总:");
        System.out.println("Function    Best Rate MB/s  Avg time     Min time     Max time");
        
        for (int j = 0; j < 4; j++) {
            avgtime[j] /= (ntimes - 1);
            double bestRate = 1.0E-06 * bytes[j] / mintime[j];
            
            System.out.printf("%s%12.1f  %11.6f  %11.6f  %11.6f\n", 
                             labels[j], bestRate, avgtime[j], mintime[j], maxtime[j]);
        }
        
        // 保存结果到文件
        if (outputFile != null) {
            saveDataToFile(outputFile, a, streamArraySize);
        } else {
            saveDataToFile("stream_result.txt", a, streamArraySize);
        }
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
            
            // 打印配置
            printConfig();
            
            // 初始化数组
            initializeArrays();
            
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
    
    // 辅助类：用于同步的屏障任务
    static class BarrierAction implements Runnable {
        private final AtomicInteger counter;
        
        public BarrierAction(AtomicInteger counter) {
            this.counter = counter;
        }
        
        @Override
        public void run() {
            counter.incrementAndGet();
        }
    }
    
    // 数据验证方法（可选）
    private static void verifyResults() {
        System.out.println("\n验证结果...");
        
        // 验证Copy操作
        double errorSum = 0.0;
        for (int i = 0; i < Math.min(1000, streamArraySize); i++) {
            errorSum += Math.abs(c[i] - a[i]);
        }
        System.out.printf("Copy操作误差总和: %.6f\n", errorSum);
        
        // 验证Scale操作（使用c的原始值和b的当前值）
        errorSum = 0.0;
        for (int i = 0; i < Math.min(1000, streamArraySize); i++) {
            // 注意：这里需要保存原始c值才能验证，这里只是演示
            errorSum += Math.abs(b[i] - 3.0 * c[i]); // scalar为3.0
        }
        System.out.printf("Scale操作误差总和: %.6f\n", errorSum);
    }
}
