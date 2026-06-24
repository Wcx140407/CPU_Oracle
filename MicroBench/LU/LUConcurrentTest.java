import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * LU分解并发测试程序
 * 支持自定义输入数据集和并行执行
 */
public class LUConcurrentTest {
    
    // ==================== 数据集配置类 ====================
    static class DatasetConfig {
        int id;                // 数据集ID
        int matrixSize;       // 矩阵大小 (N x N)
        int minTime;          // 最小测试时间(秒)
        int randomSeed;       // 随机数种子
        double mflops;        // 性能结果(Mflops)
        double execTime;      // 执行时间(秒)
        
        public DatasetConfig(int id, int matrixSize, int minTime, int randomSeed) {
            this.id = id;
            this.matrixSize = matrixSize;
            this.minTime = minTime;
            this.randomSeed = randomSeed;
            this.mflops = 0.0;
            this.execTime = 0.0;
        }
        
        @Override
        public String toString() {
            return String.format("Dataset %d: N=%d, MinTime=%ds, Seed=%d", 
                                id, matrixSize, minTime, randomSeed);
        }
    }
    
    // ==================== 随机数生成器类 ====================
    static class RandomGenerator {
        private int[] m = new int[17];
        private int seed;
        private int i;  // 原始值 = 4
        private int j;  // 原始值 = 16
        private boolean haveRange;
        private double left;
        private double right;
        private double width;
        
        private static final int MDIG = 32;
        private static final int ONE = 1;
        private static final int m1 = (ONE << (MDIG-2)) + ((ONE << (MDIG-2)) - ONE);
        private static final int m2 = ONE << MDIG/2;
        private static final double dm1 = 1.0 / (double) m1;
        
        public RandomGenerator(int seed) {
            initialize(seed);
            this.left = 0.0;
            this.right = 1.0;
            this.width = 1.0;
            this.haveRange = false;
        }
        
        public RandomGenerator(int seed, double left, double right) {
            initialize(seed);
            this.left = left;
            this.right = right;
            this.width = right - left;
            this.haveRange = true;
        }
        
        private void initialize(int seed) {
            this.seed = seed;
            
            int jseed = (seed < 0) ? -seed : seed;
            if (jseed > m1) jseed = m1;
            if (jseed % 2 == 0) jseed--;
            
            int k0 = 9069 % m2;
            int k1 = 9069 / m2;
            int j0 = jseed % m2;
            int j1 = jseed / m2;
            
            for (int iloop = 0; iloop < 17; iloop++) {
                jseed = j0 * k0;
                j1 = (jseed / m2 + j0 * k1 + j1 * k0) % (m2 / 2);
                j0 = jseed % m2;
                m[iloop] = j0 + m2 * j1;
            }
            
            this.i = 4;
            this.j = 16;
        }
        
        public double nextDouble() {
            int k = m[i] - m[j];
            if (k < 0) k += m1;
            m[j] = k;
            
            if (i == 0) i = 16;
            else i--;
            
            if (j == 0) j = 16;
            else j--;
            
            if (haveRange) {
                return left + dm1 * (double) k * width;
            } else {
                return dm1 * (double) k;
            }
        }
    }
    
    // ==================== 计时器类 ====================
    static class Stopwatch {
        private boolean running;
        private long lastTime;
        private long totalTime; // 纳秒
        
        public Stopwatch() {
            reset();
        }
        
        public void reset() {
            running = false;
            lastTime = 0;
            totalTime = 0;
        }
        
        public void start() {
            if (!running) {
                running = true;
                totalTime = 0;
                lastTime = System.nanoTime();
            }
        }
        
        public void stop() {
            if (running) {
                long currentTime = System.nanoTime();
                totalTime += currentTime - lastTime;
                running = false;
            }
        }
        
        public double readSeconds() {
            if (running) {
                long currentTime = System.nanoTime();
                totalTime += currentTime - lastTime;
                lastTime = currentTime;
            }
            return totalTime / 1e9;
        }
        
        public double readMilliseconds() {
            return readSeconds() * 1000.0;
        }
    }
    
    // ==================== LU分解工具类 ====================
    static class LUDecomposition {
        
        /**
         * 计算LU分解的浮点运算次数
         * @param N 矩阵大小
         * @return 浮点运算次数
         */
        public static double numFlops(int N) {
            double Nd = (double) N;
            return (2.0 * Nd * Nd * Nd / 3.0);
        }
        
        /**
         * 执行LU分解
         * @param N 矩阵大小
         * @param A 输入矩阵，分解后包含L和U
         * @param pivot 行交换信息
         * @return 0表示成功，1表示失败（主元为0）
         */
        public static int factor(int N, double[][] A, int[] pivot) {
            int minMN = N;
            
            for (int j = 0; j < minMN; j++) {
                // 寻找主元
                int jp = j;
                double t = Math.abs(A[j][j]);
                
                for (int i = j + 1; i < N; i++) {
                    double ab = Math.abs(A[i][j]);
                    if (ab > t) {
                        jp = i;
                        t = ab;
                    }
                }
                
                pivot[j] = jp;
                
                if (A[jp][j] == 0) {
                    return 1; // 分解失败
                }
                
                if (jp != j) {
                    // 交换行
                    double[] temp = A[j];
                    A[j] = A[jp];
                    A[jp] = temp;
                }
                
                if (j < N - 1) {
                    double recp = 1.0 / A[j][j];
                    for (int k = j + 1; k < N; k++) {
                        A[k][j] *= recp;
                    }
                }
                
                if (j < minMN - 1) {
                    for (int ii = j + 1; ii < N; ii++) {
                        double[] Aii = A[ii];
                        double[] Aj = A[j];
                        double AiiJ = Aii[j];
                        
                        for (int jj = j + 1; jj < N; jj++) {
                            Aii[jj] -= AiiJ * Aj[jj];
                        }
                    }
                }
            }
            
            return 0;
        }
        
        /**
         * 复制矩阵
         * @param N 矩阵大小
         * @param B 目标矩阵
         * @param A 源矩阵
         */
        public static void copyMatrix(int N, double[][] B, double[][] A) {
            for (int i = 0; i < N; i++) {
                System.arraycopy(A[i], 0, B[i], 0, N);
            }
        }
        
        /**
         * 生成随机矩阵
         * @param N 矩阵大小
         * @param random 随机数生成器
         * @return N×N随机矩阵
         */
        public static double[][] randomMatrix(int N, RandomGenerator random) {
            double[][] A = new double[N][N];
            for (int i = 0; i < N; i++) {
                for (int j = 0; j < N; j++) {
                    A[i][j] = random.nextDouble();
                }
            }
            return A;
        }
        
        /**
         * 测量LU分解性能
         * @param config 数据集配置
         * @return Mflops值
         */
        public static double measurePerformance(DatasetConfig config) {
            int N = config.matrixSize;
            double[][] A = null;
            double[][] lu = null;
            int[] pivot = null;
            
            Stopwatch timer = new Stopwatch();
            double result = 0.0;
            int cycles = 1;
            
            try {
                // 创建随机数生成器
                RandomGenerator random = new RandomGenerator(config.randomSeed);
                
                // 生成随机矩阵
                A = randomMatrix(N, random);
                lu = new double[N][N];
                pivot = new int[N];
                
                // 执行LU分解测试
                while (true) {
                    timer.start();
                    for (int i = 0; i < cycles; i++) {
                        copyMatrix(N, lu, A);
                        if (factor(N, lu, pivot) != 0) {
                            System.err.printf("LU分解失败 - 数据集 %d\n", config.id);
                            break;
                        }
                    }
                    timer.stop();
                    
                    if (timer.readSeconds() >= config.minTime) {
                        break;
                    }
                    cycles *= 2;
                }
                
                // 计算性能
                double totalTime = timer.readSeconds();
                config.execTime = totalTime;
                result = numFlops(N) * cycles / totalTime * 1e-6;
                config.mflops = result;
                
            } catch (OutOfMemoryError e) {
                System.err.printf("内存分配失败 - 数据集 %d (N=%d)\n", config.id, N);
                result = 0.0;
            } catch (Exception e) {
                System.err.printf("执行错误 - 数据集 %d: %s\n", config.id, e.getMessage());
                result = 0.0;
            }
            
            return result;
        }
    }
    
    // ==================== 测试任务类 ====================
    static class LUTestTask implements Callable<Double> {
        private final DatasetConfig config;
        private final ReentrantLock outputLock;
        
        public LUTestTask(DatasetConfig config, ReentrantLock outputLock) {
            this.config = config;
            this.outputLock = outputLock;
        }
        
        @Override
        public Double call() {
            long startTime = System.currentTimeMillis();
            
            // 执行LU分解测试
            double mflops = LUDecomposition.measurePerformance(config);
            
            long endTime = System.currentTimeMillis();
            double elapsedTime = (endTime - startTime) / 1000.0;
            
            // 输出结果（加锁保护）
            outputLock.lock();
            try {
                System.out.printf("线程 %s - 数据集 %d: N=%d, 种子=%d, 实际耗时=%.2fs, Mflops=%.2f\n",
                        Thread.currentThread().getName(),
                        config.id, config.matrixSize, config.randomSeed,
                        elapsedTime, mflops);
            } finally {
                outputLock.unlock();
            }
            
            return mflops;
        }
    }
    
    // ==================== 参数解析器类 ====================
    static class ArgumentParser {
        
        public static void printHelp() {
            System.out.println("用法: java LUConcurrentTest [选项]");
            System.out.println("选项:");
            System.out.println("  -d, --datasets=NUM      数据集数量 (默认: 1)");
            System.out.println("  -t, --threads=NUM       线程数 (默认: 1)");
            System.out.println("  -s, --size=SIZE1,SIZE2,... 矩阵大小列表 (逗号分隔)");
            System.out.println("  -m, --mintime=TIME1,TIME2,... 最小测试时间列表 (秒，逗号分隔)");
            System.out.println("  -r, --seed=SEED1,SEED2,... 随机种子列表 (逗号分隔)");
            System.out.println("  -f, --config=FILE       配置文件路径");
            System.out.println("  -h, --help              显示帮助信息");
            System.out.println();
            System.out.println("示例:");
            System.out.println("  java LUConcurrentTest --datasets=3 --threads=2 \\");
            System.out.println("    --size=500,1000,1500 --mintime=5,10,15 \\");
            System.out.println("    --seed=101,202,303");
            System.out.println();
            System.out.println("  java LUConcurrentTest --config=lu_config.txt");
        }
        
        /**
         * 解析逗号分隔的整数列表
         */
        private static int[] parseCommaList(String str, int defaultSize) {
            if (str == null || str.trim().isEmpty()) {
                return new int[]{defaultSize};
            }
            
            String[] parts = str.split(",");
            int[] result = new int[parts.length];
            for (int i = 0; i < parts.length; i++) {
                result[i] = Integer.parseInt(parts[i].trim());
            }
            return result;
        }
        
        /**
         * 从命令行参数解析配置
         */
        public static TestConfig parseCommandLine(String[] args) {
            TestConfig config = new TestConfig();
            
            try {
                for (int i = 0; i < args.length; i++) {
                    String arg = args[i];
                    
                    if (arg.equals("-h") || arg.equals("--help")) {
                        printHelp();
                        System.exit(0);
                    }
                    else if (arg.startsWith("-d=") || arg.startsWith("--datasets=")) {
                        config.numDatasets = Integer.parseInt(arg.split("=")[1]);
                    }
                    else if (arg.equals("-d") || arg.equals("--datasets")) {
                        config.numDatasets = Integer.parseInt(args[++i]);
                    }
                    else if (arg.startsWith("-t=") || arg.startsWith("--threads=")) {
                        config.numThreads = Integer.parseInt(arg.split("=")[1]);
                    }
                    else if (arg.equals("-t") || arg.equals("--threads")) {
                        config.numThreads = Integer.parseInt(args[++i]);
                    }
                    else if (arg.startsWith("-s=") || arg.startsWith("--size=")) {
                        config.matrixSizes = parseCommaList(arg.split("=")[1], 1000);
                    }
                    else if (arg.equals("-s") || arg.equals("--size")) {
                        config.matrixSizes = parseCommaList(args[++i], 1000);
                    }
                    else if (arg.startsWith("-m=") || arg.startsWith("--mintime=")) {
                        config.minTimes = parseCommaList(arg.split("=")[1], 20);
                    }
                    else if (arg.equals("-m") || arg.equals("--mintime")) {
                        config.minTimes = parseCommaList(args[++i], 20);
                    }
                    else if (arg.startsWith("-r=") || arg.startsWith("--seed=")) {
                        config.randomSeeds = parseCommaList(arg.split("=")[1], 101010);
                    }
                    else if (arg.equals("-r") || arg.equals("--seed")) {
                        config.randomSeeds = parseCommaList(args[++i], 101010);
                    }
                    else if (arg.startsWith("-f=") || arg.startsWith("--config=")) {
                        config.configFile = arg.split("=")[1];
                    }
                    else if (arg.equals("-f") || arg.equals("--config")) {
                        config.configFile = args[++i];
                    }
                }
                
                // 如果指定了配置文件，从文件读取配置
                if (config.configFile != null) {
                    return parseConfigFile(config.configFile);
                }
                
            } catch (Exception e) {
                System.err.println("参数解析错误: " + e.getMessage());
                printHelp();
                System.exit(1);
            }
            
            return config;
        }
        
        /**
         * 从配置文件解析配置
         */
        private static TestConfig parseConfigFile(String filename) {
            TestConfig config = new TestConfig();
            // 简化的配置文件解析，实际使用时可以扩展
            System.out.println("从配置文件读取: " + filename);
            // 这里可以实现具体的文件解析逻辑
            return config;
        }
    }
    
    // ==================== 测试配置类 ====================
    static class TestConfig {
        int numDatasets = 1;
        int numThreads = 1;
        int[] matrixSizes = {1000};
        int[] minTimes = {20};
        int[] randomSeeds = {101010};
        String configFile = null;
        
        /**
         * 创建数据集列表
         */
        public List<DatasetConfig> createDatasets() {
            List<DatasetConfig> datasets = new ArrayList<>();
            
            for (int i = 0; i < numDatasets; i++) {
                int size = matrixSizes[i % matrixSizes.length];
                int time = minTimes[i % minTimes.length];
                int seed = randomSeeds[i % randomSeeds.length] + i * 1000; // 确保不同数据集有不同的种子
                
                datasets.add(new DatasetConfig(i, size, time, seed));
            }
            
            return datasets;
        }
    }
    
    // ==================== 主程序 ====================
    public static void main(String[] args) {
        // 解析命令行参数
        TestConfig testConfig = ArgumentParser.parseCommandLine(args);
        
        // 确保线程数不超过数据集数
        if (testConfig.numThreads > testConfig.numDatasets) {
            testConfig.numThreads = testConfig.numDatasets;
        }
        
        // 创建数据集
        List<DatasetConfig> datasets = testConfig.createDatasets();
        
        // 打印配置信息
        System.out.println("=== LU分解并发测试 ===");
        System.out.printf("数据集数量: %d\n", testConfig.numDatasets);
        System.out.printf("线程数: %d\n", testConfig.numThreads);
        System.out.println("\n数据集配置:");
        for (DatasetConfig dataset : datasets) {
            System.out.println("  " + dataset);
        }
        System.out.println();
        
        // 创建线程池
        ExecutorService executor = Executors.newFixedThreadPool(testConfig.numThreads);
        List<Future<Double>> futures = new ArrayList<>();
        ReentrantLock outputLock = new ReentrantLock();
        
        // 记录开始时间
        long startTime = System.currentTimeMillis();
        
        try {
            // 提交测试任务
            for (DatasetConfig dataset : datasets) {
                LUTestTask task = new LUTestTask(dataset, outputLock);
                futures.add(executor.submit(task));
            }
            
            // 等待所有任务完成并收集结果
            List<Double> results = new ArrayList<>();
            for (Future<Double> future : futures) {
                try {
                    results.add(future.get());
                } catch (Exception e) {
                    System.err.println("任务执行错误: " + e.getMessage());
                    results.add(0.0);
                }
            }
            
            // 计算统计信息
            double totalMflops = 0.0;
            double totalExecTime = 0.0;
            int successfulTests = 0;
            
            for (int i = 0; i < datasets.size(); i++) {
                DatasetConfig dataset = datasets.get(i);
                totalMflops += dataset.mflops;
                totalExecTime += dataset.execTime;
                if (dataset.mflops > 0) {
                    successfulTests++;
                }
            }
            
            long endTime = System.currentTimeMillis();
            double totalElapsedTime = (endTime - startTime) / 1000.0;
            
            // 输出统计信息
            System.out.println("\n=== 测试结果汇总 ===");
            for (int i = 0; i < datasets.size(); i++) {
                DatasetConfig dataset = datasets.get(i);
                System.out.printf("数据集 %d: N=%d, %.2f Mflops (%.2f秒)\n",
                        i, dataset.matrixSize, dataset.mflops, dataset.execTime);
            }
            
            System.out.println("\n=== 性能统计 ===");
            System.out.printf("成功测试数: %d/%d\n", successfulTests, datasets.size());
            System.out.printf("平均性能: %.2f Mflops\n", totalMflops / Math.max(1, successfulTests));
            System.out.printf("总计算时间: %.2f秒\n", totalExecTime);
            System.out.printf("程序总耗时: %.2f秒\n", totalElapsedTime);
            
            // 如果有失败的任务，输出警告
            if (successfulTests < datasets.size()) {
                System.out.printf("\n警告: %d个测试失败\n", datasets.size() - successfulTests);
            }
            
        } catch (Exception e) {
            System.err.println("测试过程中发生错误: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // 关闭线程池
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
    }
    
    // ==================== 辅助方法：从标准输入读取配置 ====================
    /**
     * 交互式读取配置
     */
    public static TestConfig readInteractiveConfig() {
        java.util.Scanner scanner = new java.util.Scanner(System.in);
        TestConfig config = new TestConfig();
        
        System.out.println("=== 交互式配置LU分解测试 ===");
        
        System.out.print("数据集数量 (默认1): ");
        String input = scanner.nextLine().trim();
        if (!input.isEmpty()) {
            config.numDatasets = Integer.parseInt(input);
        }
        
        System.out.print("线程数 (默认1): ");
        input = scanner.nextLine().trim();
        if (!input.isEmpty()) {
            config.numThreads = Integer.parseInt(input);
        }
        
        System.out.print("矩阵大小列表 (逗号分隔，默认1000): ");
        input = scanner.nextLine().trim();
        if (!input.isEmpty()) {
            config.matrixSizes = ArgumentParser.parseCommaList(input, 1000);
        }
        
        System.out.print("最小测试时间列表 (秒，逗号分隔，默认20): ");
        input = scanner.nextLine().trim();
        if (!input.isEmpty()) {
            config.minTimes = ArgumentParser.parseCommaList(input, 20);
        }
        
        System.out.print("随机种子列表 (逗号分隔，默认101010): ");
        input = scanner.nextLine().trim();
        if (!input.isEmpty()) {
            config.randomSeeds = ArgumentParser.parseCommaList(input, 101010);
        }
        
        scanner.close();
        return config;
    }
}
