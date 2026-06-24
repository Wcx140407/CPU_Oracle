import java.util.Random;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ConcurrentSOR {
    
    // Random类 - 与C++中相同的随机数生成器实现
    static class RandomStruct {
        private int[] m = new int[17];
        private int seed;
        private int i = 4;  // 原本 = 4
        private int j = 16; // 原本 = 16
        private boolean haveRange = false;
        private double left = 0.0;
        private double right = 1.0;
        private double width = 1.0;
        
        private static final int MDIG = 32;
        private static final int ONE = 1;
        private static final int m1 = (ONE << (MDIG-2)) + ((ONE << (MDIG-2)) - ONE);
        private static final int m2 = ONE << MDIG/2;
        private static final double dm1 = 1.0 / (double) m1;
        
        public RandomStruct(int seed) {
            initialize(seed);
        }
        
        public RandomStruct(int seed, double left, double right) {
            initialize(seed);
            this.left = left;
            this.right = right;
            this.width = right - left;
            this.haveRange = true;
        }
        
        private void initialize(int seed) {
            this.seed = seed;
            int jseed = Math.abs(seed);
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
            i = 4;
            j = 16;
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
    
    // Stopwatch类 - 计时器实现
    static class Stopwatch {
        private boolean running = false;
        private double lastTime = 0.0;
        private double total = 0.0;
        
        public void reset() {
            running = false;
            lastTime = 0.0;
            total = 0.0;
        }
        
        public void start() {
            if (!running) {
                running = true;
                total = 0.0;
                lastTime = seconds();
            }
        }
        
        public void resume() {
            if (!running) {
                lastTime = seconds();
                running = true;
            }
        }
        
        public void stop() {
            if (running) {
                total += seconds() - lastTime;
                running = false;
            }
        }
        
        public double read() {
            if (running) {
                double t = seconds();
                total += t - lastTime;
                lastTime = t;
            }
            return total;
        }
        
        private static double seconds() {
            return System.nanoTime() / 1_000_000_000.0;
        }
    }
    
    // 线程数据传递结构
    static class ThreadData {
        int threadId;
        int numThreads;
        int n;
        double omega;
        int cycles;
        double[][] G;
        int startRow;
        int endRow;
        CyclicBarrier barrier;
        
        public ThreadData(int threadId, int numThreads, int n, double omega, 
                         int cycles, double[][] G, int startRow, int endRow, 
                         CyclicBarrier barrier) {
            this.threadId = threadId;
            this.numThreads = numThreads;
            this.n = n;
            this.omega = omega;
            this.cycles = cycles;
            this.G = G;
            this.startRow = startRow;
            this.endRow = endRow;
            this.barrier = barrier;
        }
    }
    
    // SOR计算相关的静态方法
    public static double SOR_num_flops(int M, int N, int num_iterations) {
        double Md = (double) M;
        double Nd = (double) N;
        double num_iterD = (double) num_iterations;
        return (Md - 1) * (Nd - 1) * num_iterD * 6.0;
    }
    
    // 线程执行函数
    static class SORThread implements Runnable {
        private ThreadData data;
        
        public SORThread(ThreadData data) {
            this.data = data;
        }
        
        @Override
        public void run() {
            int n = data.n;
            double omega = data.omega;
            int cycles = data.cycles;
            int startRow = data.startRow;
            int endRow = data.endRow;
            
            double omega_over_four = omega * 0.25;
            double one_minus_omega = 1.0 - omega;
            int nm1 = n - 1;
            
            // 执行指定次数的迭代
            for (int p = 0; p < cycles; p++) {
                try {
                    // 等待所有线程完成当前迭代
                    data.barrier.await();
                    
                    // 更新分配给当前线程的行
                    for (int i = startRow; i < endRow; i++) {
                        if (i <= 0 || i >= n - 1) continue; // 跳过边界
                        
                        double[] Gi = data.G[i];
                        double[] Gim1 = data.G[i - 1];
                        double[] Gip1 = data.G[i + 1];
                        
                        for (int j = 1; j < nm1; j++) {
                            Gi[j] = omega_over_four * (Gim1[j] + Gip1[j] + Gi[j - 1] + Gi[j + 1])
                                    + one_minus_omega * Gi[j];
                        }
                    }
                } catch (InterruptedException | BrokenBarrierException e) {
                    e.printStackTrace();
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
    
    // 并发SOR执行函数
    public static void SOR_execute_concurrent(int M, int N, double omega, 
                                             double[][] G, int num_iterations, 
                                             int num_threads) {
        ExecutorService executor = Executors.newFixedThreadPool(num_threads);
        CyclicBarrier barrier = new CyclicBarrier(num_threads);
        
        // 计算每个线程处理的行数
        int rows_per_thread = M / num_threads;
        int extra_rows = M % num_threads;
        
        // 创建并启动线程
        for (int t = 0; t < num_threads; t++) {
            int startRow = t * rows_per_thread;
            int endRow = (t + 1) * rows_per_thread;
            
            // 处理额外的行
            if (t == num_threads - 1) {
                endRow += extra_rows;
            }
            
            ThreadData data = new ThreadData(t, num_threads, N, omega, 
                                            num_iterations, G, startRow, 
                                            endRow, barrier);
            executor.execute(new SORThread(data));
        }
        
        executor.shutdown();
        try {
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    
    // 生成随机矩阵
    public static double[][] RandomMatrix(int M, int N, RandomStruct R) {
        double[][] A = new double[M][N];
        for (int i = 0; i < M; i++) {
            for (int j = 0; j < N; j++) {
                A[i][j] = R.nextDouble();
            }
        }
        return A;
    }
    
    // 测量并发SOR性能
    public static double kernel_measureSOR_concurrent(int N, double min_time, 
                                                     RandomStruct R, int num_threads) {
        double[][] G = RandomMatrix(N, N, R);
        double result = 0.0;
        
        Stopwatch Q = new Stopwatch();
        int cycles = 1;
        
        // 预热一次
        SOR_execute_concurrent(N, N, 1.25, G, 1, num_threads);
        
        while (true) {
            Q.start();
            SOR_execute_concurrent(N, N, 1.25, G, cycles, num_threads);
            Q.stop();
            
            if (Q.read() >= min_time) break;
            cycles *= 2;
        }
        
        // 计算Mflops
        result = SOR_num_flops(N, N, cycles) / Q.read() * 1.0e-6;
        return result;
    }
    
    // 打印使用说明
    public static void printUsage(String programName) {
        System.out.println("Usage: java " + programName + " [OPTIONS]");
        System.out.println("Options:");
        System.out.println("  -s SIZE      Set SOR matrix size (N x N), default: 1000");
        System.out.println("  -t THREADS   Set number of threads, default: 4");
        System.out.println("  -i ITER      Set base iteration count, default: 1");
        System.out.println("  -o OMEGA     Set SOR omega parameter, default: 1.25");
        System.out.println("  -m TIME      Set minimum measurement time (seconds), default: 2.0");
        System.out.println("  -r SEED      Set random seed, default: 101010");
        System.out.println("  -h           Show this help message");
        System.out.println();
        System.out.println("Example: java " + programName + " -s 500 -t 8 -m 5.0");
    }
    
    public static void main(String[] args) {
        // 默认参数
        int SOR_size = 1000;
        int num_threads = 4;
        int base_iterations = 1;
        double omega = 1.25;
        double min_time = 2.0;
        int random_seed = 101010;
        
        // 解析命令行参数
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-s":
                    if (i + 1 < args.length) {
                        SOR_size = Integer.parseInt(args[++i]);
                        if (SOR_size <= 0) {
                            System.err.println("Error: Size must be positive");
                            return;
                        }
                    }
                    break;
                case "-t":
                    if (i + 1 < args.length) {
                        num_threads = Integer.parseInt(args[++i]);
                        if (num_threads <= 0) {
                            System.err.println("Error: Thread count must be positive");
                            return;
                        }
                    }
                    break;
                case "-i":
                    if (i + 1 < args.length) {
                        base_iterations = Integer.parseInt(args[++i]);
                        if (base_iterations <= 0) {
                            System.err.println("Error: Iteration count must be positive");
                            return;
                        }
                    }
                    break;
                case "-o":
                    if (i + 1 < args.length) {
                        omega = Double.parseDouble(args[++i]);
                        if (omega <= 0 || omega >= 2) {
                            System.err.println("Warning: Omega parameter should be between 0 and 2");
                        }
                    }
                    break;
                case "-m":
                    if (i + 1 < args.length) {
                        min_time = Double.parseDouble(args[++i]);
                        if (min_time <= 0) {
                            System.err.println("Error: Measurement time must be positive");
                            return;
                        }
                    }
                    break;
                case "-r":
                    if (i + 1 < args.length) {
                        random_seed = Integer.parseInt(args[++i]);
                    }
                    break;
                case "-h":
                    printUsage("ConcurrentSOR");
                    return;
                default:
                    System.err.println("Unknown option: " + args[i]);
                    printUsage("ConcurrentSOR");
                    return;
            }
        }
        
        // 检查逻辑核心数
        int max_threads = Runtime.getRuntime().availableProcessors();
        /*if (num_threads > max_threads) {
            System.out.printf("Warning: Requested %d threads but system has only %d logical cores%n",
                    num_threads, max_threads);
            System.out.printf("Setting threads to %d%n", max_threads);
            num_threads = max_threads;
        }*/
        
        System.out.println("========================================");
        System.out.println("Concurrent SOR Benchmark (Java)");
        System.out.println("========================================");
        System.out.printf("Matrix size:      %d x %d%n", SOR_size, SOR_size);
        System.out.printf("Number of threads: %d%n", num_threads);
        System.out.printf("Omega parameter:   %.2f%n", omega);
        System.out.printf("Random seed:       %d%n", random_seed);
        System.out.printf("Min measurement time: %.1f seconds%n", min_time);
        System.out.printf("System logical cores: %d%n", max_threads);
        System.out.println("========================================");
        
        // 初始化随机数生成器
        RandomStruct R = new RandomStruct(random_seed);
        
        // 运行并发基准测试
        double start_time = System.nanoTime() / 1_000_000_000.0;
        double mflops = kernel_measureSOR_concurrent(SOR_size, min_time, R, num_threads);
        double end_time = System.nanoTime() / 1_000_000_000.0;
        
        System.out.printf("SOR Mflops:       %8.2f%n", mflops);
        System.out.printf("Total time:       %8.2f seconds%n", end_time - start_time);
    }
}
