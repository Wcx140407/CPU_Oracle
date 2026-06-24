import java.util.concurrent.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;

// 随机数生成器（与C版本相同的算法）
class RandomGenerator {
    private int[] m = new int[17];
    private int seed;
    private int i = 4;
    private int j = 16;
    private boolean haveRange = false;
    private double left = 0.0;
    private double right = 1.0;
    private double width = 1.0;
    
    private static final int MDIG = 32;
    private static final int ONE = 1;
    private static final int m1 = (ONE << (MDIG-2)) + ((ONE << (MDIG-2)) - ONE);
    private static final int m2 = ONE << MDIG/2;
    private static final double dm1 = 1.0 / (double) m1;
    
    public RandomGenerator(int seed) {
        initialize(seed);
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
        if (seed < 0) seed = -seed;
        
        int jseed = (seed < m1 ? seed : m1);
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

// 数据集配置
class DatasetConfig {
    int datasetId;
    int matrixSize;
    int randomSeed;
    String name;
    int numThreads;
    
    public DatasetConfig(int datasetId, int matrixSize, int randomSeed, String name, int numThreads) {
        this.datasetId = datasetId;
        this.matrixSize = matrixSize;
        this.randomSeed = randomSeed;
        this.name = name;
        this.numThreads = numThreads;
    }
    
    @Override
    public String toString() {
        return String.format("Dataset %d: %s (size=%d, seed=%d, threads=%d)",
                datasetId, name, matrixSize, randomSeed, numThreads);
    }
}

// 全局配置
class GlobalConfig {
    int numDatasets;
    List<DatasetConfig> datasets;
    int totalThreads;
    double minTime;
    boolean verbose;
    
    public GlobalConfig() {
        this.numDatasets = 1;
        this.datasets = new ArrayList<>();
        this.totalThreads = 1;
        this.minTime = 2.0;
        this.verbose = false;
    }
}

// LU分解结果
class LUResult {
    int datasetId;
    String datasetName;
    double mflops;
    double executionTime;
    int matrixSize;
    int threadsUsed;
    
    public LUResult(int datasetId, String datasetName, double mflops, 
                   double executionTime, int matrixSize, int threadsUsed) {
        this.datasetId = datasetId;
        this.datasetName = datasetName;
        this.mflops = mflops;
        this.executionTime = executionTime;
        this.matrixSize = matrixSize;
        this.threadsUsed = threadsUsed;
    }
    
    @Override
    public String toString() {
        return String.format("Dataset %d (%s): %.2f Mflops (size=%d, threads=%d, time=%.3fs)",
                datasetId, datasetName, mflops, matrixSize, threadsUsed, executionTime);
    }
}

// 并行LU分解器
class ParallelLUDecomposer {
    
    // 计算浮点运算次数
    public static double luNumFlops(int n) {
        double nd = (double) n;
        return (2.0 * nd * nd * nd / 3.0);
    }
    
    // 生成随机矩阵
    public static double[][] generateRandomMatrix(int n, RandomGenerator rand) {
        double[][] matrix = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                matrix[i][j] = rand.nextDouble();
            }
        }
        return matrix;
    }
    
    // 复制矩阵
    public static void copyMatrix(double[][] dest, double[][] src) {
        int n = src.length;
        for (int i = 0; i < n; i++) {
            System.arraycopy(src[i], 0, dest[i], 0, n);
        }
    }
    
    // 并行LU分解任务
    static class LUTask implements Runnable {
        private final int threadId;
        private final int numThreads;
        private final double[][] A;
        private final int[] pivot;
        private final CyclicBarrier barrier;
        private final int startRow;
        private final int endRow;
        private final int startCol;
        private final int endCol;
        private final int n;
        
        public LUTask(int threadId, int numThreads, double[][] A, int[] pivot, 
                     CyclicBarrier barrier, int startRow, int endRow, 
                     int startCol, int endCol) {
            this.threadId = threadId;
            this.numThreads = numThreads;
            this.A = A;
            this.pivot = pivot;
            this.barrier = barrier;
            this.startRow = startRow;
            this.endRow = endRow;
            this.startCol = startCol;
            this.endCol = endCol;
            this.n = A.length;
        }
        
        @Override
        public void run() {
            try {
                for (int j = 0; j < n; j++) {
                    // 等待所有线程到达这一列
                    barrier.await();
                    
                    // 只有主线程(threadId=0)进行主元查找和行交换
                    if (threadId == 0) {
                        // 查找主元
                        int jp = j;
                        double t = Math.abs(A[j][j]);
                        for (int i = j + 1; i < n; i++) {
                            double ab = Math.abs(A[i][j]);
                            if (ab > t) {
                                jp = i;
                                t = ab;
                            }
                        }
                        
                        pivot[j] = jp;
                        
                        // 执行行交换
                        if (jp != j) {
                            double[] temp = A[j];
                            A[j] = A[jp];
                            A[jp] = temp;
                        }
                        
                        // 计算缩放因子
                        if (A[j][j] != 0.0 && j < n - 1) {
                            double recp = 1.0 / A[j][j];
                            for (int i = j + 1; i < n; i++) {
                                A[i][j] *= recp;
                            }
                        }
                    }
                    
                    // 等待主线程完成行交换和缩放
                    barrier.await();
                    
                    // 并行更新子矩阵
                    if (j < n - 1) {
                        for (int i = startRow; i < endRow; i++) {
                            if (i > j) {  // 只处理对角线以下的元素
                                double multiplier = A[i][j];
                                for (int k = startCol; k < endCol; k++) {
                                    if (k > j) {  // 只处理对角线右侧的元素
                                        A[i][k] -= multiplier * A[j][k];
                                    }
                                }
                            }
                        }
                    }
                    
                    // 等待所有线程完成这一列的更新
                    barrier.await();
                }
            } catch (Exception e) {
                e.printStackTrace();
                Thread.currentThread().interrupt();
            }
        }
    }
    
    // 测量LU分解性能
    public static LUResult measureLUPerformance(DatasetConfig config, int numThreads, 
                                               double minTime, boolean verbose) {
        int n = config.matrixSize;
        RandomGenerator rand = new RandomGenerator(config.randomSeed);
        
        // 生成原始矩阵
        double[][] A = generateRandomMatrix(n, rand);
        double[][] lu = new double[n][n];
        copyMatrix(lu, A);
        
        int[] pivot = new int[n];
        
        ExecutorService executor = null;
        double totalTime = 0.0;
        int cycles = 1;
        long startTime = System.nanoTime();
        
        try {
            while (totalTime < minTime) {
                // 重置矩阵
                copyMatrix(lu, A);
                
                // 创建屏障
                CyclicBarrier barrier = new CyclicBarrier(numThreads);
                
                // 计算每个线程的工作范围
                int rowsPerThread = (n + numThreads - 1) / numThreads;
                int colsPerThread = (n + numThreads - 1) / numThreads;
                
                // 创建并执行任务
                executor = Executors.newFixedThreadPool(numThreads);
                List<Future<?>> futures = new ArrayList<>();
                
                long iterationStart = System.nanoTime();
                
                for (int t = 0; t < numThreads; t++) {
                    int startRow = t * rowsPerThread;
                    int endRow = Math.min(startRow + rowsPerThread, n);
                    int startCol = t * colsPerThread;
                    int endCol = Math.min(startCol + colsPerThread, n);
                    
                    LUTask task = new LUTask(t, numThreads, lu, pivot, barrier,
                                           startRow, endRow, startCol, endCol);
                    futures.add(executor.submit(task));
                }
                
                // 等待所有任务完成
                for (Future<?> future : futures) {
                    future.get();
                }
                
                long iterationEnd = System.nanoTime();
                double elapsed = (iterationEnd - iterationStart) / 1e9;
                totalTime += elapsed;
                
                if (totalTime < minTime) {
                    cycles *= 2;
                }
                
                executor.shutdown();
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (executor != null && !executor.isShutdown()) {
                executor.shutdown();
            }
        }
        
        long endTime = System.nanoTime();
        double totalElapsed = (endTime - startTime) / 1e9;
        
        // 计算性能
        double flopsPerCycle = luNumFlops(n);
        double totalFlops = flopsPerCycle * cycles;
        double mflops = totalFlops / totalElapsed / 1e6;
        
        if (verbose) {
            System.out.printf("  Dataset %d: completed %d cycles in %.3f seconds%n",
                    config.datasetId, cycles, totalElapsed);
        }
        
        return new LUResult(config.datasetId, config.name, mflops, 
                           totalElapsed, n, numThreads);
    }
}

// 参数解析器
class ArgumentParser {
    public static GlobalConfig parseArguments(String[] args) {
        GlobalConfig config = new GlobalConfig();
        Map<String, String> argMap = new HashMap<>();
        
        // 将参数解析到map中
        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("--")) {
                String[] parts = args[i].substring(2).split("=", 2);
                if (parts.length == 2) {
                    argMap.put(parts[0], parts[1]);
                } else {
                    argMap.put(parts[0], "true");
                }
            } else if (args[i].startsWith("-")) {
                if (i + 1 < args.length && !args[i + 1].startsWith("-")) {
                    argMap.put(args[i].substring(1), args[i + 1]);
                    i++;
                } else {
                    argMap.put(args[i].substring(1), "true");
                }
            }
        }
        
        // 解析数据集数量
        if (argMap.containsKey("datasets")) {
            config.numDatasets = Integer.parseInt(argMap.get("datasets"));
        } else if (argMap.containsKey("d")) {
            config.numDatasets = Integer.parseInt(argMap.get("d"));
        }
        
        // 解析总线程数
        if (argMap.containsKey("threads")) {
            config.totalThreads = Integer.parseInt(argMap.get("threads"));
        } else if (argMap.containsKey("t")) {
            config.totalThreads = Integer.parseInt(argMap.get("t"));
        }
        
        // 解析最小时间
        if (argMap.containsKey("min-time")) {
            config.minTime = Double.parseDouble(argMap.get("min-time"));
        } else if (argMap.containsKey("m")) {
            config.minTime = Double.parseDouble(argMap.get("m"));
        }
        
        // 解析详细模式
        config.verbose = argMap.containsKey("verbose") || argMap.containsKey("v");
        
        // 解析帮助
        if (argMap.containsKey("help") || argMap.containsKey("h")) {
            printHelp();
            System.exit(0);
        }
        
        // 解析数据集参数
        for (int i = 0; i < config.numDatasets; i++) {
            int size = 1000;
            int seed = 101010 + i;
            String name = "dataset" + i;
            int threads = 1;
            
            // 解析矩阵大小
            String sizeKey = "size" + i;
            if (argMap.containsKey(sizeKey)) {
                size = Integer.parseInt(argMap.get(sizeKey));
            }
            
            // 解析随机种子
            String seedKey = "seed" + i;
            if (argMap.containsKey(seedKey)) {
                seed = Integer.parseInt(argMap.get(seedKey));
            }
            
            // 解析名称
            String nameKey = "name" + i;
            if (argMap.containsKey(nameKey)) {
                name = argMap.get(nameKey);
            }
            
            // 解析每个数据集的线程数
            String threadsKey = "threads-per-dataset" + i;
            if (argMap.containsKey(threadsKey)) {
                threads = Integer.parseInt(argMap.get(threadsKey));
            }
            
            config.datasets.add(new DatasetConfig(i, size, seed, name, threads));
        }
        
        // 如果没有为数据集指定线程数，则平均分配
        int totalDatasetThreads = config.datasets.stream()
                .mapToInt(d -> d.numThreads)
                .sum();
        
        if (totalDatasetThreads == config.numDatasets) {
            // 如果所有数据集都使用默认的1线程，则重新分配
            int threadsPerDataset = config.totalThreads / config.numDatasets;
            int remaining = config.totalThreads % config.numDatasets;
            
            for (int i = 0; i < config.numDatasets; i++) {
                DatasetConfig dataset = config.datasets.get(i);
                dataset.numThreads = threadsPerDataset;
                if (i < remaining) dataset.numThreads++;
                if (dataset.numThreads < 1) dataset.numThreads = 1;
            }
        }
        
        return config;
    }
    
    public static void printHelp() {
        System.out.println("并行LU分解性能测试程序");
        System.out.println("用法: java ParallelLU [选项]");
        System.out.println();
        System.out.println("选项:");
        System.out.println("  -d, --datasets=NUM       数据集数量 (默认: 1)");
        System.out.println("  -t, --threads=NUM        总线程数 (默认: 1)");
        System.out.println("  -m, --min-time=SEC       最小测量时间(秒) (默认: 2.0)");
        System.out.println("  -v, --verbose            详细输出模式");
        System.out.println("  -h, --help               显示此帮助信息");
        System.out.println();
        System.out.println("数据集选项 (对于每个数据集 i=0..N-1):");
        System.out.println("  --size[i]=NUM            矩阵大小 NxN (默认: 1000)");
        System.out.println("  --seed[i]=NUM            随机种子 (默认: 101010+i)");
        System.out.println("  --name[i]=NAME           数据集名称");
        System.out.println("  --threads-per-dataset[i]=NUM  每个数据集的线程数");
        System.out.println();
        System.out.println("示例:");
        System.out.println("  java ParallelLU --datasets=3 --threads=6 \\");
        System.out.println("    --size0=500 --seed0=12345 --name0=small --threads-per-dataset0=2 \\");
        System.out.println("    --size1=1000 --seed1=23456 --name1=medium --threads-per-dataset1=3 \\");
        System.out.println("    --size2=2000 --seed2=34567 --name2=large --threads-per-dataset2=1");
        System.out.println();
        System.out.println("从文件读取配置:");
        System.out.println("  java ParallelLU --config=config.txt");
    }
}

// 配置文件读取器
class ConfigFileReader {
    public static GlobalConfig readConfigFromFile(String filename) {
        GlobalConfig config = new GlobalConfig();
        List<DatasetConfig> datasets = new ArrayList<>();
        
        try (Scanner scanner = new Scanner(new java.io.File(filename))) {
            int datasetId = 0;
            
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine().trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue; // 跳过空行和注释
                }
                
                String[] parts = line.split("=");
                if (parts.length != 2) {
                    continue;
                }
                
                String key = parts[0].trim();
                String value = parts[1].trim();
                
                switch (key) {
                    case "datasets":
                        config.numDatasets = Integer.parseInt(value);
                        break;
                    case "totalThreads":
                        config.totalThreads = Integer.parseInt(value);
                        break;
                    case "minTime":
                        config.minTime = Double.parseDouble(value);
                        break;
                    case "verbose":
                        config.verbose = Boolean.parseBoolean(value);
                        break;
                    default:
                        // 检查是否是数据集配置
                        if (key.startsWith("dataset")) {
                            String[] datasetParts = key.split("\\.");
                            if (datasetParts.length == 2) {
                                int id = Integer.parseInt(datasetParts[0].substring(7));
                                String field = datasetParts[1];
                                
                                // 确保有足够的datasets
                                while (datasets.size() <= id) {
                                    datasets.add(new DatasetConfig(id, 1000, 
                                            101010 + id, "dataset" + id, 1));
                                }
                                
                                DatasetConfig dataset = datasets.get(id);
                                switch (field) {
                                    case "size":
                                        dataset.matrixSize = Integer.parseInt(value);
                                        break;
                                    case "seed":
                                        dataset.randomSeed = Integer.parseInt(value);
                                        break;
                                    case "name":
                                        dataset.name = value;
                                        break;
                                    case "threads":
                                        dataset.numThreads = Integer.parseInt(value);
                                        break;
                                }
                            }
                        }
                        break;
                }
            }
            
            config.datasets = datasets;
            config.numDatasets = datasets.size();
            
        } catch (Exception e) {
            System.err.println("读取配置文件错误: " + e.getMessage());
            System.exit(1);
        }
        
        return config;
    }
}

// 主程序
public class ParallelLU {
    
    public static void main(String[] args) {
        GlobalConfig config;
        
        // 检查是否从文件读取配置
        boolean useConfigFile = false;
        String configFile = null;
        
        for (String arg : args) {
            if (arg.startsWith("--config=")) {
                useConfigFile = true;
                configFile = arg.substring(9);
                break;
            }
        }
        
        if (useConfigFile && configFile != null) {
            config = ConfigFileReader.readConfigFromFile(configFile);
        } else {
            config = ArgumentParser.parseArguments(args);
        }
        
        System.out.println("=== 并行LU分解性能测试 ===");
        System.out.println("配置:");
        System.out.println("  数据集数量: " + config.numDatasets);
        System.out.println("  总线程数: " + config.totalThreads);
        System.out.println("  最小测量时间: " + config.minTime + " 秒");
        System.out.println("  详细输出: " + (config.verbose ? "是" : "否"));
        System.out.println();
        
        if (config.verbose) {
            System.out.println("数据集配置:");
            for (DatasetConfig dataset : config.datasets) {
                System.out.println("  " + dataset);
            }
            System.out.println();
        }
        
        System.out.println("开始测试...");
        long totalStartTime = System.nanoTime();
        
        // 使用线程池执行所有数据集的测试
        ExecutorService mainExecutor = Executors.newFixedThreadPool(config.totalThreads);
        List<Future<LUResult>> futures = new ArrayList<>();
        List<LUResult> results = new ArrayList<>();
        
        // 提交所有数据集任务
        for (DatasetConfig dataset : config.datasets) {
            Callable<LUResult> task = () -> {
                if (config.verbose) {
                    System.out.printf("处理数据集 %d: %s (使用 %d 线程)%n",
                            dataset.datasetId, dataset.name, dataset.numThreads);
                }
                
                int actualThreads = dataset.numThreads;
                if (actualThreads > dataset.matrixSize) {
                    actualThreads = dataset.matrixSize;
                    if (config.verbose) {
                        System.out.printf("  警告: 线程数超过矩阵大小，调整为 %d 线程%n", actualThreads);
                    }
                }
                
                return ParallelLUDecomposer.measureLUPerformance(
                        dataset, actualThreads, config.minTime, config.verbose);
            };
            
            futures.add(mainExecutor.submit(task));
        }
        
        // 收集结果
        try {
            for (Future<LUResult> future : futures) {
                LUResult result = future.get();
                results.add(result);
                System.out.println(result);
            }
            
            mainExecutor.shutdown();
            mainExecutor.awaitTermination(1, TimeUnit.MINUTES);
            
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        long totalEndTime = System.nanoTime();
        double totalTime = (totalEndTime - totalStartTime) / 1e9;
        
        // 输出汇总结果
        System.out.println("\n=== 测试完成 ===");
        System.out.printf("总运行时间: %.2f 秒%n", totalTime);
        System.out.println("\n性能汇总:");
        
        double totalMflops = 0.0;
        for (LUResult result : results) {
            System.out.println("  " + result);
            totalMflops += result.mflops;
        }
        
        if (!results.isEmpty()) {
            double avgMflops = totalMflops / results.size();
            System.out.printf("%n平均性能: %.2f Mflops%n", avgMflops);
            
            // 输出性能比较
            System.out.println("\n性能比较:");
            results.sort((a, b) -> Double.compare(b.mflops, a.mflops));
            for (LUResult result : results) {
                System.out.printf("  %s: %.2f Mflops (加速比: %.2f)%n",
                        result.datasetName, result.mflops,
                        result.mflops / results.get(results.size() - 1).mflops);
            }
        }
        
        // 输出建议
        System.out.println("\n建议:");
        if (!results.isEmpty()) {
            LUResult best = Collections.max(results, Comparator.comparingDouble(r -> r.mflops));
            System.out.printf("  最佳配置: %s (矩阵大小: %d, 线程数: %d)%n",
                    best.datasetName, best.matrixSize, best.threadsUsed);
            System.out.printf("  最佳性能: %.2f Mflops%n", best.mflops);
            
            // 分析线程效率
            System.out.println("\n线程效率分析:");
            for (LUResult result : results) {
                double singleThreadEstimate = result.mflops / result.threadsUsed;
                double efficiency = (singleThreadEstimate * result.threadsUsed) / result.mflops;
                System.out.printf("  %s: 线程数=%d, 效率=%.2f%%%n",
                        result.datasetName, result.threadsUsed, efficiency * 100);
            }
        }
    }
    
    // 示例配置文件内容
    public static void createSampleConfigFile(String filename) {
        try (java.io.PrintWriter writer = new java.io.PrintWriter(filename)) {
            writer.println("# 并行LU分解测试配置文件");
            writer.println("# 注释以#开头");
            writer.println();
            writer.println("# 全局配置");
            writer.println("datasets=3");
            writer.println("totalThreads=8");
            writer.println("minTime=2.0");
            writer.println("verbose=true");
            writer.println();
            writer.println("# 数据集0配置");
            writer.println("dataset0.size=500");
            writer.println("dataset0.seed=12345");
            writer.println("dataset0.name=small");
            writer.println("dataset0.threads=2");
            writer.println();
            writer.println("# 数据集1配置");
            writer.println("dataset1.size=1000");
            writer.println("dataset1.seed=23456");
            writer.println("dataset1.name=medium");
            writer.println("dataset1.threads=3");
            writer.println();
            writer.println("# 数据集2配置");
            writer.println("dataset2.size=2000");
            writer.println("dataset2.seed=34567");
            writer.println("dataset2.name=large");
            writer.println("dataset2.threads=3");
            
            System.out.println("示例配置文件已创建: " + filename);
        } catch (Exception e) {
            System.err.println("创建示例配置文件失败: " + e.getMessage());
        }
    }
}

// 支持从CSV文件读取矩阵数据的扩展类
class MatrixFileReader {
    
    // 从CSV文件读取矩阵
    public static double[][] readMatrixFromCSV(String filename) throws Exception {
        List<double[]> rows = new ArrayList<>();
        
        try (Scanner scanner = new Scanner(new java.io.File(filename))) {
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine().trim();
                if (line.isEmpty()) continue;
                
                String[] parts = line.split(",");
                double[] row = new double[parts.length];
                for (int i = 0; i < parts.length; i++) {
                    row[i] = Double.parseDouble(parts[i].trim());
                }
                rows.add(row);
            }
        }
        
        int n = rows.size();
        double[][] matrix = new double[n][n];
        for (int i = 0; i < n; i++) {
            matrix[i] = rows.get(i);
        }
        
        return matrix;
    }
    
    // 生成示例CSV文件
    public static void generateSampleCSV(String filename, int size, int seed) {
        RandomGenerator rand = new RandomGenerator(seed);
        
        try (java.io.PrintWriter writer = new java.io.PrintWriter(filename)) {
            for (int i = 0; i < size; i++) {
                for (int j = 0; j < size; j++) {
                    writer.print(rand.nextDouble());
                    if (j < size - 1) {
                        writer.print(",");
                    }
                }
                writer.println();
            }
            
            System.out.println("示例CSV文件已创建: " + filename + " (大小: " + size + "x" + size + ")");
        } catch (Exception e) {
            System.err.println("创建CSV文件失败: " + e.getMessage());
        }
    }
}
