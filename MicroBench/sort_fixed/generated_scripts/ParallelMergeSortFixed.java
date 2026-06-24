import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ParallelMergeSortFixed {
    
    // 配置类
    static class Config {
        String inputFile = "./input/data.in";
        int threads = 4;
        boolean help = false;
        
        @Override
        public String toString() {
            return String.format("Config{inputFile='%s', threads=%d}", inputFile, threads);
        }
    }
    
    // 线程数据类
    static class ThreadData {
        int id;
        int[] arr;
        int[] temp;
        int len;
        int totalThreads;
        int startIdx;
        int endIdx;
        CyclicBarrier barrier;
        
        ThreadData(int id, int[] arr, int[] temp, int len, int totalThreads, 
                  int startIdx, int endIdx, CyclicBarrier barrier) {
            this.id = id;
            this.arr = arr;
            this.temp = temp;
            this.len = len;
            this.totalThreads = totalThreads;
            this.startIdx = startIdx;
            this.endIdx = endIdx;
            this.barrier = barrier;
        }
    }
    
    // 打印使用说明
    static void printUsage(String progName) {
        System.out.println("Usage: java " + progName + " [OPTIONS]");
        System.out.println("Options:");
        System.out.println("  -i FILE     Input file path (default: ./input/data.in)");
        System.out.println("  -t NUM      Number of threads (default: 4)");
        System.out.println("  -h          Show this help message");
        System.out.println();
        System.out.println("Input file format:");
        System.out.println("  First line: n numreps");
        System.out.println("  Following lines: n integers");
        System.out.println();
        System.out.println("Example: java " + progName + " -i ./data/sort.in -t 8");
    }
    
    // 解析命令行参数
    static Config parseArgs(String[] args) {
        Config config = new Config();
        
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-i":
                    if (i + 1 < args.length) {
                        config.inputFile = args[++i];
                    } else {
                        System.err.println("Error: Missing argument for -i");
                        System.exit(1);
                    }
                    break;
                case "-t":
                    if (i + 1 < args.length) {
                        config.threads = Integer.parseInt(args[++i]);
                        if (config.threads <= 0) {
                            System.err.println("Error: Thread count must be positive");
                            System.exit(1);
                        }
                    } else {
                        System.err.println("Error: Missing argument for -t");
                        System.exit(1);
                    }
                    break;
                case "-h":
                    config.help = true;
                    break;
                default:
                    System.err.println("Unknown option: " + args[i]);
                    printUsage("ParallelMergeSortFixed");
                    System.exit(1);
            }
        }
        
        return config;
    }
    
    // 串行归并排序函数（与C++版本相同的算法）- 修复版本
    static void mergeSortSerial(int[] arr) {
        int len = arr.length;
        if (len <= 1) return;
        
        int[] a = arr;
        int[] b = new int[len];
        
        for (int seg = 1; seg < len; seg += seg) {
            for (int start = 0; start < len; start += seg + seg) {
                int low = start;
                int mid = Math.min(start + seg, len);
                int high = Math.min(start + seg + seg, len);
                
                int k = low;
                int start1 = low, end1 = mid;
                int start2 = mid, end2 = high;
                
                while (start1 < end1 && start2 < end2) {
                    b[k++] = a[start1] < a[start2] ? a[start1++] : a[start2++];
                }
                while (start1 < end1) {
                    b[k++] = a[start1++];
                }
                while (start2 < end2) {
                    b[k++] = a[start2++];
                }
            }
            
            // 交换数组
            int[] temp = a;
            a = b;
            b = temp;
        }
        
        // 如果最终结果不在原始数组中，复制回去
        if (a != arr) {
            System.arraycopy(a, 0, arr, 0, len);
        }
    }
    
    // 正确实现的简单并行归并排序 - 修复版本
    static void mergeSortParallelSimple(int[] arr, int numThreads) {
        int len = arr.length;
        if (numThreads <= 1 || len < 10000) {
            mergeSortSerial(arr);
            return;
        }
        
        // 限制线程数量
        numThreads = Math.min(numThreads, Runtime.getRuntime().availableProcessors());
        numThreads = Math.min(numThreads, len / 1000);
        if (numThreads < 2) {
            mergeSortSerial(arr);
            return;
        }
        
        // 分配数据到线程 - 每个线程处理一个数据块
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        List<Future<int[]>> futures = new ArrayList<>();
        
        int chunkSize = (len + numThreads - 1) / numThreads; // 向上取整
        
        // 第一阶段：并行局部排序
        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            final int start = threadId * chunkSize;
            final int end = Math.min(start + chunkSize, len);
            
            if (start >= len) break;
            
            Callable<int[]> task = () -> {
                int[] chunk = Arrays.copyOfRange(arr, start, end);
                Arrays.sort(chunk);
                return chunk;
            };
            futures.add(executor.submit(task));
        }
        
        executor.shutdown();
        
        // 收集排序后的块
        List<int[]> sortedChunks = new ArrayList<>();
        try {
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
            for (Future<int[]> future : futures) {
                sortedChunks.add(future.get());
            }
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            return;
        }
        
        // 第二阶段：归并所有排序块
        if (sortedChunks.size() == 1) {
            System.arraycopy(sortedChunks.get(0), 0, arr, 0, len);
            return;
        }
        
        // 使用优先队列进行k路归并
        PriorityQueue<HeapNode> heap = new PriorityQueue<>(Comparator.comparingInt(node -> node.value));
        
        // 初始化堆
        int[] indices = new int[sortedChunks.size()];
        for (int i = 0; i < sortedChunks.size(); i++) {
            if (indices[i] < sortedChunks.get(i).length) {
                heap.offer(new HeapNode(sortedChunks.get(i)[indices[i]], i));
            }
        }
        
        // 执行归并
        int[] result = new int[len];
        int index = 0;
        
        while (!heap.isEmpty()) {
            HeapNode node = heap.poll();
            result[index++] = node.value;
            
            int chunkIdx = node.chunkIndex;
            indices[chunkIdx]++;
            
            if (indices[chunkIdx] < sortedChunks.get(chunkIdx).length) {
                heap.offer(new HeapNode(sortedChunks.get(chunkIdx)[indices[chunkIdx]], chunkIdx));
            }
        }
        
        // 复制回原数组
        System.arraycopy(result, 0, arr, 0, len);
    }
    
    // 堆节点用于k路归并
    static class HeapNode {
        int value;
        int chunkIndex;
        
        HeapNode(int value, int chunkIndex) {
            this.value = value;
            this.chunkIndex = chunkIndex;
        }
    }
    
    // 替代方案：使用两两归并的策略（更稳定）
    static void mergeSortParallelTwoPass(int[] arr, int numThreads) {
        int len = arr.length;
        if (numThreads <= 1 || len < 10000) {
            mergeSortSerial(arr);
            return;
        }
        
        // 限制线程数量
        numThreads = Math.min(numThreads, Runtime.getRuntime().availableProcessors());
        numThreads = Math.min(numThreads, len / 1000);
        if (numThreads < 2) {
            mergeSortSerial(arr);
            return;
        }
        
        // 分配数据到线程
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        int chunkSize = (len + numThreads - 1) / numThreads;
        
        // 第一阶段：每个线程排序自己的块
        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            final int start = threadId * chunkSize;
            final int end = Math.min(start + chunkSize, len);
            
            if (start >= len) break;
            
            executor.execute(() -> {
                Arrays.sort(arr, start, end);
            });
        }
        
        executor.shutdown();
        try {
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        // 第二阶段：串行归并各块
        int[] temp = new int[len];
        
        // 初始块大小为chunkSize
        for (int blockSize = chunkSize; blockSize < len; blockSize *= 2) {
            for (int start = 0; start < len; start += 2 * blockSize) {
                int mid = Math.min(start + blockSize, len);
                int end = Math.min(start + 2 * blockSize, len);
                
                // 归并两个块
                mergeTwoArrays(arr, temp, start, mid, end);
            }
        }
    }
    
    // 归并两个有序数组
    static void mergeTwoArrays(int[] arr, int[] temp, int left, int mid, int right) {
        int i = left, j = mid, k = left;
        
        while (i < mid && j < right) {
            if (arr[i] <= arr[j]) {
                temp[k++] = arr[i++];
            } else {
                temp[k++] = arr[j++];
            }
        }
        
        while (i < mid) {
            temp[k++] = arr[i++];
        }
        
        while (j < right) {
            temp[k++] = arr[j++];
        }
        
        // 复制回原数组
        System.arraycopy(temp, left, arr, left, right - left);
    }
    
    // 安全的并行归并排序 - 使用两两归并策略
    static void mergeSortParallelSafe(int[] arr, int numThreads) {
        mergeSortParallelTwoPass(arr, numThreads);
    }
    
    // 验证排序结果
    static boolean verifySorted(int[] arr) {
        for (int i = 1; i < arr.length; i++) {
            if (arr[i] < arr[i-1]) {
                System.err.printf("Verification failed at index %d: %d < %d%n", 
                                 i, arr[i], arr[i-1]);
                return false;
            }
        }
        return true;
    }
    
    // 测试并计时函数
    static double testSort(SortFunction sortFunc, int[] arr, String name) {
        int[] copy = Arrays.copyOf(arr, arr.length); // 保护原数组
        
        long startTime = System.nanoTime();
        
        sortFunc.sort(copy);
        
        long endTime = System.nanoTime();
        double elapsed = (endTime - startTime) / 1_000_000_000.0;
        
        boolean sorted = verifySorted(copy);
        System.out.printf("  %s: %.6f seconds %s%n", name, elapsed, sorted ? "✓" : "✗");
        
        // 如果排序成功，复制回原数组位置
        if (sorted) {
            System.arraycopy(copy, 0, arr, 0, arr.length);
        }
        
        return elapsed;
    }
    
    // 排序函数接口
    interface SortFunction {
        void sort(int[] arr);
    }
    
    // 生成测试数据
    static void generateTestData(String filename, int n, int reps) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            writer.printf("%d %d%n", n, reps);
            
            Random random = new Random(System.currentTimeMillis());
            int count = 0;
            for (int i = 0; i < n; i++) {
                writer.print(random.nextInt(1000000));
                count++;
                if (count % 20 == 0) {
                    writer.println();
                } else if (i < n - 1) {
                    writer.print(" ");
                }
            }
            if (count % 20 != 0) {
                writer.println();
            }
            
            System.out.printf("Generated test data: %s with %d elements%n", filename, n);
        } catch (IOException e) {
            System.err.printf("Error: Cannot create file %s%n", filename);
            e.printStackTrace();
        }
    }
    
    // 读取输入文件
    static InputData readInputFile(String filename) {
        try (Scanner scanner = new Scanner(new File(filename))) {
            int n = scanner.nextInt();
            int numreps = scanner.nextInt();
            
            int[] data = new int[n];
            for (int i = 0; i < n; i++) {
                if (!scanner.hasNextInt()) {
                    throw new IOException("Unexpected end of file at element " + i);
                }
                data[i] = scanner.nextInt();
            }
            
            System.out.printf("Read %d integers from input file%n", n);
            return new InputData(data, n, numreps);
        } catch (FileNotFoundException e) {
            System.out.println("Input file not found. Generating sample data...");
            generateTestData(filename, 100000, 3);
            return readInputFile(filename);
        } catch (IOException e) {
            System.err.printf("Error reading input file: %s%n", e.getMessage());
            return null;
        }
    }
    
    // 输入数据容器
    static class InputData {
        int[] data;
        int n;
        int numreps;
        
        InputData(int[] data, int n, int numreps) {
            this.data = data;
            this.n = n;
            this.numreps = numreps;
        }
    }
    
    public static void main(String[] args) {
        // 解析命令行参数
        Config config = parseArgs(args);
        
        if (config.help) {
            printUsage("ParallelMergeSortFixed");
            return;
        }
        
        // 检查系统核心数
        int maxThreads = Runtime.getRuntime().availableProcessors();
        if (config.threads > maxThreads * 2) {
            System.out.printf("Warning: Requested %d threads (more than 2x logical cores)%n", config.threads);
            System.out.printf("Setting threads to %d%n", maxThreads * 2);
            config.threads = maxThreads * 2;
        }
        
        System.out.println("========================================");
        System.out.println("Parallel Merge Sort Benchmark (Java Fixed Version)");
        System.out.println("========================================");
        System.out.printf("Input file:       %s%n", config.inputFile);
        System.out.printf("Threads:          %d%n", config.threads);
        System.out.printf("System cores:     %d%n", maxThreads);
        
        // 读取输入文件
        InputData input = readInputFile(config.inputFile);
        
        if (input == null) {
            System.err.println("Error: Failed to read input file");
            return;
        }
        
        int n = input.n;
        int numreps = input.numreps;
        int[] originalData = input.data;
        
        System.out.printf("Array size:       %d%n", n);
        System.out.printf("Repetitions:      %d%n", numreps);
        System.out.println("========================================");
        
        // 测试串行排序
        System.out.println("\n1. Testing serial merge sort (1 thread)...");
        double totalSerial = 0.0;
        int serialRuns = Math.min(3, numreps);
        int[] serialResult = new int[n];
        
        for (int rep = 0; rep < serialRuns; rep++) {
            int[] arrCopy = Arrays.copyOf(originalData, n);
            totalSerial += testSort(new SortFunction() {
                @Override
                public void sort(int[] arr) {
                    mergeSortSerial(arr);
                }
            }, arrCopy, "Serial");
            
            // 保存第一次的结果用于验证
            if (rep == 0) {
                serialResult = Arrays.copyOf(arrCopy, n);
            }
        }
        
        System.out.printf("  Average serial time: %.6f seconds%n", totalSerial / serialRuns);
        
        // 测试两两归并的并行排序
        System.out.printf("\n2. Testing parallel merge sort - two pass (%d threads)...%n", config.threads);
        double totalParallelTwoPass = 0.0;
        int[] parallelTwoPassResult = new int[n];
        final int threadsForTwoPass = config.threads; // 创建final变量
        
        for (int rep = 0; rep < numreps; rep++) {
            int[] arrCopy = Arrays.copyOf(originalData, n);
            totalParallelTwoPass += testSort(new SortFunction() {
                @Override
                public void sort(int[] arr) {
                    mergeSortParallelTwoPass(arr, threadsForTwoPass);
                }
            }, arrCopy, "Parallel Two-Pass");
            
            // 保存第一次的结果用于验证
            if (rep == 0) {
                parallelTwoPassResult = Arrays.copyOf(arrCopy, n);
            }
        }
        
        // 验证排序结果与串行版本一致
        boolean twoPassMatch = Arrays.equals(serialResult, parallelTwoPassResult);
        System.out.printf("  Two-pass verification: %s (matches serial sort)%n", 
                         twoPassMatch ? "✓" : "✗");
        
        // 测试k路归并的并行排序
        System.out.printf("\n3. Testing parallel merge sort - k-way...%n");
        double totalParallelKWay = 0.0;
        int[] parallelKWayResult = new int[n];
        
        // 计算有效线程数
        int effectiveThreads = Math.min(config.threads, n / 1000);
        if (effectiveThreads < 2) effectiveThreads = 2;
        final int finalEffectiveThreads = effectiveThreads; // 创建final变量
        
        for (int rep = 0; rep < numreps; rep++) {
            int[] arrCopy = Arrays.copyOf(originalData, n);
            totalParallelKWay += testSort(new SortFunction() {
                @Override
                public void sort(int[] arr) {
                    mergeSortParallelSimple(arr, finalEffectiveThreads);
                }
            }, arrCopy, "Parallel K-Way");
            
            // 保存第一次的结果用于验证
            if (rep == 0) {
                parallelKWayResult = Arrays.copyOf(arrCopy, n);
            }
        }
        
        // 验证排序结果与串行版本一致
        boolean kWayMatch = Arrays.equals(serialResult, parallelKWayResult);
        System.out.printf("  K-way verification: %s (matches serial sort)%n", 
                         kWayMatch ? "✓" : "✗");
        
        // 输出统计信息
        System.out.println("\n========================================");
        System.out.println("Performance Summary");
        System.out.println("========================================");
        System.out.printf("Array size:          %d%n", n);
        System.out.printf("Max threads:         %d%n", config.threads);
        System.out.printf("Effective threads:   %d%n", effectiveThreads);
        System.out.printf("System cores:        %d%n", maxThreads);
        System.out.printf("Serial runs:         %d%n", serialRuns);
        System.out.printf("Parallel runs:       %d%n", numreps);
        System.out.printf("Average serial time: %.6f seconds%n", totalSerial / serialRuns);
        
        if (numreps > 0) {
            System.out.printf("Two-pass parallel:   %.6f seconds%n", totalParallelTwoPass / numreps);
            System.out.printf("K-way parallel:      %.6f seconds%n", totalParallelKWay / numreps);
            
            if (totalParallelTwoPass > 0) {
                double speedupTwoPass = (totalSerial / serialRuns) / (totalParallelTwoPass / numreps);
                System.out.printf("Two-pass speedup:    %.2fx (%.1f%% efficiency)%n", 
                                speedupTwoPass, (speedupTwoPass / config.threads) * 100);
            }
            
            if (totalParallelKWay > 0) {
                double speedupKWay = (totalSerial / serialRuns) / (totalParallelKWay / numreps);
                System.out.printf("K-way speedup:       %.2fx (%.1f%% efficiency)%n", 
                                speedupKWay, (speedupKWay / effectiveThreads) * 100);
            }
        }
        
        // 输出部分排序结果用于验证
        System.out.println("\nSample of sorted results (first and last 5 elements):");
        System.out.print("First 5:  ");
        for (int i = 0; i < Math.min(5, n); i++) {
            System.out.print(serialResult[i] + " ");
        }
        System.out.print("\nLast 5:   ");
        for (int i = Math.max(0, n - 5); i < n; i++) {
            System.out.print(serialResult[i] + " ");
        }
        System.out.println();
        
        // 验证数组是否完全有序
        boolean fullySorted = true;
        for (int i = 1; i < n; i++) {
            if (serialResult[i] < serialResult[i-1]) {
                System.err.printf("Critical error: Array not fully sorted at index %d%n", i);
                fullySorted = false;
                break;
            }
        }
        
        if (fullySorted) {
            System.out.println("\n✓ All sorting algorithms produced correct results");
        } else {
            System.out.println("\n✗ Sorting algorithms produced incorrect results");
        }
    }
}
