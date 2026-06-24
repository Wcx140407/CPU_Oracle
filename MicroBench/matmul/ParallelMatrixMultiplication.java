import java.io.*;
import java.util.Random;
import java.util.concurrent.*;

public class ParallelMatrixMultiplication {
    
    // 矩阵大小 n x n
    private int n;
    // 线程数
    private int numThreads;
    // 分块大小
    private int blockSize;
    // 重复次数
    private int numReps;
    // 矩阵
    private double[][] A;
    private double[][] B;
    private double[][] C;
    // 随机数生成器
    private Random random;
    
    // 构造函数
    public ParallelMatrixMultiplication(int n, int numThreads, int numReps, int blockSize) {
        this.n = n;
        this.numThreads = numThreads;
        this.numReps = numReps;
        this.blockSize = Math.min(blockSize, n);
        this.random = new Random();
        initializeMatrices();
    }
    
    // 初始化矩阵
    private void initializeMatrices() {
        A = new double[n][n];
        B = new double[n][n];
        C = new double[n][n];
        
        // 使用随机值初始化矩阵
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                A[i][j] = random.nextDouble();
                B[i][j] = random.nextDouble();
                C[i][j] = 0.0;
            }
        }
    }
    
    // 从文件读取矩阵数据
    public void loadMatricesFromFile(String fileA, String fileB) throws IOException {
        // 读取矩阵A
        readMatrixFromFile(fileA, A);
        // 读取矩阵B
        readMatrixFromFile(fileB, B);
        // 重置结果矩阵C
        resetMatrix(C);
    }
    
    // 从文件读取矩阵
    private void readMatrixFromFile(String filename, double[][] matrix) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(filename));
        String line;
        int row = 0;
        
        while ((line = reader.readLine()) != null && row < n) {
            String[] values = line.trim().split("\\s+");
            for (int col = 0; col < n && col < values.length; col++) {
                matrix[row][col] = Double.parseDouble(values[col]);
            }
            row++;
        }
        reader.close();
    }
    
    // 重置矩阵为0
    private void resetMatrix(double[][] matrix) {
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                matrix[i][j] = 0.0;
            }
        }
    }
    
    // 分块矩阵乘法任务类
    class BlockMultiplicationTask implements Runnable {
        private int startBlockI;
        private int endBlockI;
        private int startBlockJ;
        private int endBlockJ;
        private int startBlockK;
        private int endBlockK;
        
        public BlockMultiplicationTask(int startBlockI, int endBlockI, 
                                      int startBlockJ, int endBlockJ,
                                      int startBlockK, int endBlockK) {
            this.startBlockI = startBlockI;
            this.endBlockI = endBlockI;
            this.startBlockJ = startBlockJ;
            this.endBlockJ = endBlockJ;
            this.startBlockK = startBlockK;
            this.endBlockK = endBlockK;
        }
        
        @Override
        public void run() {
            for (int bi = startBlockI; bi < endBlockI; bi += blockSize) {
                for (int bj = startBlockJ; bj < endBlockJ; bj += blockSize) {
                    for (int bk = startBlockK; bk < endBlockK; bk += blockSize) {
                        int iLimit = Math.min(bi + blockSize, n);
                        int jLimit = Math.min(bj + blockSize, n);
                        int kLimit = Math.min(bk + blockSize, n);
                        
                        for (int i = bi; i < iLimit; i++) {
                            for (int j = bj; j < jLimit; j++) {
                                double sum = C[i][j];
                                for (int k = bk; k < kLimit; k++) {
                                    sum += A[i][k] * B[k][j];
                                }
                                C[i][j] = sum;
                            }
                        }
                    }
                }
            }
        }
    }
    
    // 并行矩阵乘法
    public void parallelMultiply() throws InterruptedException {
        // 使用线程池
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        
        // 计算每个线程处理的行块范围
        int blocksPerDim = (n + blockSize - 1) / blockSize;
        int blocksPerThread = blocksPerDim / numThreads;
        
        // 创建并提交任务
        for (int t = 0; t < numThreads; t++) {
            int startBlock = t * blocksPerThread;
            int endBlock = (t == numThreads - 1) ? blocksPerDim : (t + 1) * blocksPerThread;
            
            int startBlockI = startBlock * blockSize;
            int endBlockI = Math.min(endBlock * blockSize, n);
            int startBlockJ = 0;
            int endBlockJ = n;
            int startBlockK = 0;
            int endBlockK = n;
            
            executor.execute(new BlockMultiplicationTask(
                startBlockI, endBlockI, startBlockJ, endBlockJ, startBlockK, endBlockK
            ));
        }
        
        // 等待所有任务完成
        executor.shutdown();
        executor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
    }
    
    // 串行验证（用于结果校验）
    public void verifyResult() {
        System.out.println("验证结果（前5x5区域）:");
        for (int i = 0; i < Math.min(5, n); i++) {
            for (int j = 0; j < Math.min(5, n); j++) {
                double sum = 0.0;
                for (int k = 0; k < n; k++) {
                    sum += A[i][k] * B[k][j];
                }
                System.out.printf("C[%d][%d] = %.6f, expected = %.6f%n", 
                                 i, j, C[i][j], sum);
            }
        }
    }
    
    // 执行性能测试
    public void runPerformanceTest() throws InterruptedException {
        System.out.println("开始并行矩阵乘法...");
        
        double totalElapsed = 0.0;
        
        for (int rep = 0; rep < numReps; rep++) {
            // 重置结果矩阵
            resetMatrix(C);
            
            long startTime = System.nanoTime();
            parallelMultiply();
            long endTime = System.nanoTime();
            
            double elapsed = (endTime - startTime) / 1e9;
            System.out.printf("第 %d 次运行时间: %.6f 秒%n", rep + 1, elapsed);
            totalElapsed += elapsed;
        }
        
        // 打印统计信息
        System.out.println("\n统计结果:");
        System.out.printf("  总时间: %.6f 秒%n", totalElapsed);
        System.out.printf("  平均时间: %.6f 秒%n", totalElapsed / numReps);
        System.out.printf("  性能: %.2f GFLOPs%n", 
                          (2.0 * n * n * n * numReps / totalElapsed / 1e9));
    }
    
    // 保存结果到文件
    public void saveResultToFile(String filename) throws IOException {
        BufferedWriter writer = new BufferedWriter(new FileWriter(filename));
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                writer.write(String.format("%.6f ", C[i][j]));
            }
            writer.newLine();
        }
        writer.close();
        System.out.println("结果已保存到文件: " + filename);
    }
    
    // 生成测试数据文件
    public void generateTestDataFiles(String fileA, String fileB) throws IOException {
        generateMatrixToFile(fileA, A);
        generateMatrixToFile(fileB, B);
        System.out.println("测试数据已生成到文件: " + fileA + " 和 " + fileB);
    }
    
    // 将矩阵写入文件
    private void generateMatrixToFile(String filename, double[][] matrix) throws IOException {
        BufferedWriter writer = new BufferedWriter(new FileWriter(filename));
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                writer.write(String.format("%.6f ", matrix[i][j]));
            }
            writer.newLine();
        }
        writer.close();
    }
    
    // 打印矩阵配置信息
    public void printConfig() {
        System.out.println("矩阵乘法配置:");
        System.out.println("  矩阵大小: " + n + " x " + n);
        System.out.println("  线程数: " + numThreads);
        System.out.println("  重复次数: " + numReps);
        System.out.println("  分块大小: " + blockSize);
        System.out.println("  矩阵A: " + (A != null ? "已初始化" : "未初始化"));
        System.out.println("  矩阵B: " + (B != null ? "已初始化" : "未初始化"));
    }
    
    // 主函数 - 程序入口点
    public static void main(String[] args) throws IOException, InterruptedException {
        // 默认参数
        int n = 32 * 32;
        int numThreads = 4;
        int numReps = 10;
        int blockSize = 64;
        
        String inputFileA = null;
        String inputFileB = null;
        String outputFile = null;
        
        // 命令行参数解析
        // 用法: java ParallelMatrixMultiplication [矩阵大小] [线程数] [重复次数] [分块大小] [输入文件A] [输入文件B] [输出文件]
        // 示例: java ParallelMatrixMultiplication 1024 8 5 64 inputA.txt inputB.txt result.txt
        if (args.length >= 1) {
            n = Integer.parseInt(args[0]);
        }
        if (args.length >= 2) {
            numThreads = Integer.parseInt(args[1]);
        }
        if (args.length >= 3) {
            numReps = Integer.parseInt(args[2]);
        }
        if (args.length >= 4) {
            blockSize = Integer.parseInt(args[3]);
        }
        if (args.length >= 6) {
            inputFileA = args[4];
            inputFileB = args[5];
        }
        if (args.length >= 7) {
            outputFile = args[6];
        }
        
        // 创建并行矩阵乘法对象
        ParallelMatrixMultiplication pmm = new ParallelMatrixMultiplication(n, numThreads, numReps, blockSize);
        
        // 打印配置信息
        pmm.printConfig();
        
        // 如果提供了输入文件，从文件读取数据
        if (inputFileA != null && inputFileB != null) {
            System.out.println("从文件读取矩阵数据...");
            pmm.loadMatricesFromFile(inputFileA, inputFileB);
        } else {
            System.out.println("使用随机生成的矩阵数据");
            // 生成测试数据文件（可选）
            if (n <= 512) { // 对于小矩阵生成测试文件
                pmm.generateTestDataFiles("test_matrix_A.txt", "test_matrix_B.txt");
            }
        }
        
        // 执行性能测试
        pmm.runPerformanceTest();
        
        // 验证结果（仅对小矩阵）
        if (n <= 256) {
            pmm.verifyResult();
        }
        
        // 保存结果到文件（如果指定了输出文件）
        if (outputFile != null) {
            pmm.saveResultToFile(outputFile);
        }
    }
}
