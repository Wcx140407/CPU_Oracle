import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class FIOBenchmark {
    
    // 常量定义
    private static final int BLOCK_SIZE = 262144; // 256KB
    private static final int MAX_PATH_LEN = 100;
    private static final int MAX_DATASET_NAME = 50;
    private static final long PAGE_SIZE = 4096; // 默认页大小
    
    // 数据集结构
    static class Dataset {
        String name;
        String filename;
        String directory;
        long fileSize;       // 字节数
        boolean writeFlag;   // true: write, false: read
        boolean randomFlag;  // true: random, false: sequential
        
        Dataset(String name, String filename, String directory, 
                long fileSize, boolean writeFlag, boolean randomFlag) {
            this.name = name;
            this.filename = filename;
            this.directory = directory;
            this.fileSize = fileSize;
            this.writeFlag = writeFlag;
            this.randomFlag = randomFlag;
        }
        
        @Override
        public String toString() {
            return String.format("Dataset{name='%s', file='%s/%s', size=%d, op=%s, pattern=%s}",
                    name, directory, filename, fileSize,
                    writeFlag ? "write" : "read",
                    randomFlag ? "random" : "sequential");
        }
    }
    
    // 命令行参数结构
    static class CommandArgs {
        String datasetFile = "";
        int runtime = 10;            // 运行时间(秒)
        int threadNum = 4;           // 总线程数
        int threadPerDataset = 1;    // 每个数据集分配的线程数
        List<Dataset> datasets = new ArrayList<>();
        
        @Override
        public String toString() {
            return String.format("CommandArgs{file='%s', runtime=%d, totalThreads=%d, threadsPerDataset=%d, datasets=%d}",
                    datasetFile, runtime, threadNum, threadPerDataset, datasets.size());
        }
    }
    
    // 线程结果结构
    static class ThreadResult {
        int datasetId;
        int threadId;
        long ioBytes;
        long startTime;  // 纳秒
        long endTime;    // 纳秒
        
        ThreadResult(int datasetId, int threadId) {
            this.datasetId = datasetId;
            this.threadId = threadId;
            this.ioBytes = 0;
            this.startTime = 0;
            this.endTime = 0;
        }
    }
    
    // 单位转换
    private static long unitTrans(String size) {
        if (size == null || size.isEmpty()) {
            throw new IllegalArgumentException("Size string cannot be empty");
        }
        
        size = size.trim().toUpperCase();
        String numberPart = size;
        String unitPart = "B";
        
        // 提取数字部分和单位部分
        int unitIndex = -1;
        for (int i = 0; i < size.length(); i++) {
            if (!Character.isDigit(size.charAt(i)) && size.charAt(i) != '.') {
                unitIndex = i;
                break;
            }
        }
        
        if (unitIndex != -1) {
            numberPart = size.substring(0, unitIndex);
            unitPart = size.substring(unitIndex);
        }
        
        // 解析数字
        double value;
        try {
            value = Double.parseDouble(numberPart);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid number format: " + numberPart);
        }
        
        // 根据单位计算
        switch (unitPart) {
            case "B":
                return (long) value;
            case "K":
            case "KB":
                return (long) (value * 1024);
            case "M":
            case "MB":
                return (long) (value * 1024 * 1024);
            case "G":
            case "GB":
                return (long) (value * 1024 * 1024 * 1024);
            case "T":
            case "TB":
                return (long) (value * 1024 * 1024 * 1024 * 1024);
            default:
                throw new IllegalArgumentException("Unknown unit: " + unitPart + 
                        ". Supported units: B, K/KB, M/MB, G/GB, T/TB");
        }
    }
    
    // 解析数据集文件
    private static List<Dataset> parseDatasetFile(String filename) throws IOException {
        List<Dataset> datasets = new ArrayList<>();
        Path filePath = Paths.get(filename);
        
        if (!Files.exists(filePath)) {
            throw new FileNotFoundException("Dataset file not found: " + filename);
        }
        
        try (BufferedReader reader = Files.newBufferedReader(filePath)) {
            String line;
            boolean firstLine = true;
            
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                
                // 跳过空行和注释
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                
                // 跳过标题行（可选）
                if (firstLine) {
                    if (line.toLowerCase().contains("name") && 
                        line.toLowerCase().contains("filename") &&
                        line.toLowerCase().contains("directory")) {
                        firstLine = false;
                        continue;
                    }
                }
                
                // 解析CSV格式
                String[] parts = line.split(",");
                if (parts.length >= 6) {
                    String name = parts[0].trim();
                    String filenameStr = parts[1].trim();
                    String directory = parts[2].trim();
                    String filesize = parts[3].trim();
                    String operation = parts[4].trim().toLowerCase();
                    String pattern = parts[5].trim().toLowerCase();
                    
                    // 验证参数
                    if (name.isEmpty() || filenameStr.isEmpty() || directory.isEmpty() ||
                        filesize.isEmpty() || operation.isEmpty() || pattern.isEmpty()) {
                        System.err.println("Warning: Skipping invalid line: " + line);
                        continue;
                    }
                    
                    // 转换文件大小
                    long fileSizeBytes;
                    try {
                        fileSizeBytes = unitTrans(filesize);
                    } catch (IllegalArgumentException e) {
                        System.err.println("Warning: Invalid file size format in line: " + line);
                        continue;
                    }
                    
                    // 解析操作类型
                    boolean writeFlag;
                    if (operation.equals("write") || operation.equals("w")) {
                        writeFlag = true;
                    } else if (operation.equals("read") || operation.equals("r")) {
                        writeFlag = false;
                    } else {
                        System.err.println("Warning: Invalid operation in line: " + line);
                        continue;
                    }
                    
                    // 解析访问模式
                    boolean randomFlag;
                    if (pattern.equals("random") || pattern.equals("rand")) {
                        randomFlag = true;
                    } else if (pattern.equals("sequential") || pattern.equals("seq")) {
                        randomFlag = false;
                    } else {
                        System.err.println("Warning: Invalid pattern in line: " + line);
                        continue;
                    }
                    
                    // 创建数据集对象
                    Dataset dataset = new Dataset(name, filenameStr, directory, 
                            fileSizeBytes, writeFlag, randomFlag);
                    datasets.add(dataset);
                    
                    System.out.println("Loaded dataset: " + dataset);
                } else {
                    System.err.println("Warning: Invalid CSV format in line: " + line);
                }
                
                firstLine = false;
            }
        }
        
        if (datasets.isEmpty()) {
            throw new IOException("No valid datasets found in file: " + filename);
        }
        
        return datasets;
    }
    
    // 解析命令行参数
    private static CommandArgs parseCommandArgs(String[] args) {
        CommandArgs cmd = new CommandArgs();
        
        if (args.length == 0) {
            printHelp();
            System.exit(1);
        }
        
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-d":
                case "--dataset":
                    if (i + 1 < args.length) {
                        cmd.datasetFile = args[++i];
                    } else {
                        System.err.println("Error: Missing argument for dataset file");
                        System.exit(1);
                    }
                    break;
                    
                case "-t":
                case "--threads":
                    if (i + 1 < args.length) {
                        try {
                            cmd.threadNum = Integer.parseInt(args[++i]);
                            if (cmd.threadNum <= 0) {
                                throw new NumberFormatException();
                            }
                        } catch (NumberFormatException e) {
                            System.err.println("Error: Thread number must be a positive integer");
                            System.exit(1);
                        }
                    }
                    break;
                    
                case "-p":
                case "--threads-per-dataset":
                    if (i + 1 < args.length) {
                        try {
                            cmd.threadPerDataset = Integer.parseInt(args[++i]);
                            if (cmd.threadPerDataset <= 0) {
                                throw new NumberFormatException();
                            }
                        } catch (NumberFormatException e) {
                            System.err.println("Error: Threads per dataset must be a positive integer");
                            System.exit(1);
                        }
                    }
                    break;
                    
                case "-f":
                case "--runtime":
                    if (i + 1 < args.length) {
                        try {
                            cmd.runtime = Integer.parseInt(args[++i]);
                            if (cmd.runtime <= 0) {
                                throw new NumberFormatException();
                            }
                        } catch (NumberFormatException e) {
                            System.err.println("Error: Runtime must be a positive integer");
                            System.exit(1);
                        }
                    }
                    break;
                    
                case "-h":
                case "--help":
                    printHelp();
                    System.exit(0);
                    break;
                    
                default:
                    System.err.println("Error: Unknown option: " + args[i]);
                    printHelp();
                    System.exit(1);
            }
        }
        
        // 验证必需参数
        if (cmd.datasetFile.isEmpty()) {
            System.err.println("Error: Dataset file is required");
            printHelp();
            System.exit(1);
        }
        
        return cmd;
    }
    
    // 打印帮助信息
    private static void printHelp() {
        System.out.println("FIO Benchmark - Java Version");
        System.out.println("Usage: java FIOBenchmark [OPTIONS]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  -d, --dataset FILE           Dataset configuration file (required)");
        System.out.println("  -t, --threads N              Total number of threads (default: 4)");
        System.out.println("  -p, --threads-per-dataset N  Threads per dataset (default: 1)");
        System.out.println("  -f, --runtime SECONDS        Runtime in seconds (default: 10)");
        System.out.println("  -h, --help                   Show this help message");
        System.out.println();
        System.out.println("Dataset file format (CSV):");
        System.out.println("  name,filename,directory,filesize,operation,pattern");
        System.out.println("  Example: dataset1,test1.dat,/tmp/data,1G,write,sequential");
        System.out.println("           dataset2,test2.dat,/tmp/data,2G,read,random");
        System.out.println();
        System.out.println("Supported size units: B, K/KB, M/MB, G/GB, T/TB");
        System.out.println("Supported operations: read/r, write/w");
        System.out.println("Supported patterns: sequential/seq, random/rand");
    }
    
    // 创建测试文件
    private static String createFile(Dataset dataset) throws IOException {
        // 创建目录
        Path dirPath = Paths.get(dataset.directory);
        if (!Files.exists(dirPath)) {
            Files.createDirectories(dirPath);
        }
        
        // 构建完整文件路径
        Path filePath = dirPath.resolve(dataset.filename);
        String fullPath = filePath.toString();
        
        System.out.println("Creating file: " + fullPath + " (" + 
                formatSize(dataset.fileSize) + ")");
        
        // 使用FileChannel创建文件以提高性能
        try (FileChannel channel = FileChannel.open(filePath, 
                StandardOpenOption.CREATE, 
                StandardOpenOption.WRITE, 
                StandardOpenOption.TRUNCATE_EXISTING)) {
            
            ByteBuffer buffer = ByteBuffer.allocateDirect(BLOCK_SIZE);
            // 填充测试数据
            for (int i = 0; i < BLOCK_SIZE; i++) {
                buffer.put((byte) ('A' + (i % 26)));
            }
            buffer.flip();
            
            long bytesWritten = 0;
            while (bytesWritten < dataset.fileSize) {
                buffer.rewind();
                int toWrite = (int) Math.min(BLOCK_SIZE, dataset.fileSize - bytesWritten);
                buffer.limit(toWrite);
                
                int written = channel.write(buffer);
                if (written <= 0) {
                    throw new IOException("Write failed at position: " + bytesWritten);
                }
                bytesWritten += written;
                
                // 显示进度
                if (bytesWritten % (1024 * 1024 * 100) == 0) { // 每100MB显示一次
                    System.out.printf("  Progress: %.1f%%\n", 
                            (bytesWritten * 100.0) / dataset.fileSize);
                }
            }
        }
        
        // 验证文件大小
        long actualSize = Files.size(filePath);
        if (actualSize != dataset.fileSize) {
            throw new IOException(String.format("File size mismatch: expected %d, got %d", 
                    dataset.fileSize, actualSize));
        }
        
        return fullPath;
    }
    
    // IO测试任务
    static class IOTask implements Callable<ThreadResult> {
        private final Dataset dataset;
        private final int datasetId;
        private final int threadId;
        private final int runtime;
        private final String filePath;
        private final Random random;
        
        public IOTask(Dataset dataset, int datasetId, int threadId, 
                     int runtime, String filePath) {
            this.dataset = dataset;
            this.datasetId = datasetId;
            this.threadId = threadId;
            this.runtime = runtime;
            this.filePath = filePath;
            this.random = new Random(System.nanoTime() + threadId);
        }
        
        @Override
        public ThreadResult call() throws Exception {
            ThreadResult result = new ThreadResult(datasetId, threadId);
            
            try (FileChannel channel = FileChannel.open(Paths.get(filePath),
                    dataset.writeFlag ? 
                    StandardOpenOption.WRITE : 
                    StandardOpenOption.READ)) {
                
                ByteBuffer buffer = ByteBuffer.allocateDirect(BLOCK_SIZE);
                
                // 填充写缓冲区
                if (dataset.writeFlag) {
                    for (int i = 0; i < BLOCK_SIZE; i++) {
                        buffer.put((byte) ('W' + (i % 26)));
                    }
                    buffer.flip();
                }
                
                result.startTime = System.nanoTime();
                long deadline = result.startTime + runtime * 1_000_000_000L;
                long bytesProcessed = 0;
                
                while (System.nanoTime() < deadline) {
                    if (dataset.randomFlag) {
                        // 随机访问
                        long maxOffset = dataset.fileSize - BLOCK_SIZE;
                        if (maxOffset > 0) {
                            long offset = (random.nextLong() & Long.MAX_VALUE) % 
                                         (maxOffset / PAGE_SIZE) * PAGE_SIZE;
                            channel.position(offset);
                        }
                    } else {
                        // 顺序访问
                        long currentPos = channel.position();
                        if (currentPos + BLOCK_SIZE > dataset.fileSize) {
                            channel.position(0);
                        }
                    }
                    
                    if (dataset.writeFlag) {
                        // 写操作
                        buffer.rewind();
                        int written = channel.write(buffer);
                        if (written > 0) {
                            bytesProcessed += written;
                        }
                    } else {
                        // 读操作
                        buffer.clear();
                        int read = channel.read(buffer);
                        if (read > 0) {
                            bytesProcessed += read;
                        }
                    }
                }
                
                result.endTime = System.nanoTime();
                result.ioBytes = bytesProcessed;
                
            } catch (Exception e) {
                System.err.printf("Error in thread %d of dataset %s: %s\n", 
                        threadId, dataset.name, e.getMessage());
                throw e;
            }
            
            return result;
        }
    }
    
    // 执行IO测试
    private static void executeIOTest(CommandArgs cmd) throws Exception {
        System.out.println("\n" + "=".repeat(50));
        System.out.println("Starting IO Benchmark");
        System.out.println("=".repeat(50));
        System.out.println(cmd);
        System.out.println("-".repeat(50));
        
        // 为所有数据集创建文件
        List<String> filePaths = new ArrayList<>();
        for (int i = 0; i < cmd.datasets.size(); i++) {
            Dataset dataset = cmd.datasets.get(i);
            try {
                String filePath = createFile(dataset);
                filePaths.add(filePath);
                System.out.println("Created file for dataset '" + dataset.name + "': " + filePath);
            } catch (Exception e) {
                System.err.println("Failed to create file for dataset " + dataset.name + ": " + e.getMessage());
                throw e;
            }
        }
        
        // 计算线程分配
        int totalThreads = Math.min(cmd.threadNum, 
                cmd.datasets.size() * cmd.threadPerDataset);
        System.out.printf("\nTotal threads to be used: %d\n", totalThreads);
        
        // 创建线程池
        ExecutorService executor = Executors.newFixedThreadPool(totalThreads);
        List<Future<ThreadResult>> futures = new ArrayList<>();
        AtomicInteger threadCounter = new AtomicInteger(0);
        
        // 提交任务
        for (int datasetId = 0; datasetId < cmd.datasets.size(); datasetId++) {
            Dataset dataset = cmd.datasets.get(datasetId);
            String filePath = filePaths.get(datasetId);
            
            for (int t = 0; t < cmd.threadPerDataset; t++) {
                if (threadCounter.get() >= totalThreads) {
                    break;
                }
                
                int threadId = threadCounter.getAndIncrement();
                IOTask task = new IOTask(dataset, datasetId, t, 
                        cmd.runtime, filePath);
                futures.add(executor.submit(task));
                
                System.out.printf("Started thread %d for dataset '%s' (%s %s)\n",
                        t, dataset.name,
                        dataset.writeFlag ? "write" : "read",
                        dataset.randomFlag ? "random" : "sequential");
            }
        }
        
        // 等待所有任务完成
        executor.shutdown();
        System.out.println("\nWaiting for tests to complete...");
        
        if (!executor.awaitTermination(cmd.runtime + 30, TimeUnit.SECONDS)) {
            System.err.println("Warning: Some tasks did not complete in time");
            executor.shutdownNow();
        }
        
        // 收集结果
        List<ThreadResult> results = new ArrayList<>();
        for (Future<ThreadResult> future : futures) {
            try {
                results.add(future.get());
            } catch (Exception e) {
                System.err.println("Error getting thread result: " + e.getMessage());
            }
        }
        
        // 分析并显示结果
        printResults(cmd, results);
        
        // 清理文件（可选）
        System.out.println("\nCleaning up test files...");
        for (String filePath : filePaths) {
            try {
                Files.deleteIfExists(Paths.get(filePath));
            } catch (IOException e) {
                System.err.println("Warning: Failed to delete file: " + filePath);
            }
        }
    }
    
    // 打印结果
    private static void printResults(CommandArgs cmd, List<ThreadResult> results) {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("BENCHMARK RESULTS");
        System.out.println("=".repeat(60));
        
        // 按数据集分组结果
        Map<Integer, List<ThreadResult>> resultsByDataset = new HashMap<>();
        for (ThreadResult result : results) {
            resultsByDataset
                .computeIfAbsent(result.datasetId, k -> new ArrayList<>())
                .add(result);
        }
        
        // 计算并显示每个数据集的统计信息
        for (Map.Entry<Integer, List<ThreadResult>> entry : resultsByDataset.entrySet()) {
            int datasetId = entry.getKey();
            List<ThreadResult> datasetResults = entry.getValue();
            Dataset dataset = cmd.datasets.get(datasetId);
            
            if (datasetResults.isEmpty()) {
                continue;
            }
            
            // 计算统计信息
            long totalBytes = 0;
            long minStartTime = Long.MAX_VALUE;
            long maxEndTime = 0;
            
            for (ThreadResult result : datasetResults) {
                totalBytes += result.ioBytes;
                minStartTime = Math.min(minStartTime, result.startTime);
                maxEndTime = Math.max(maxEndTime, result.endTime);
            }
            
            double elapsedSeconds = (maxEndTime - minStartTime) / 1_000_000_000.0;
            double bandwidthMBps = (totalBytes / elapsedSeconds) / (1024.0 * 1024.0);
            double iops = (totalBytes / BLOCK_SIZE) / elapsedSeconds;
            
            // 显示结果
            System.out.println("\nDataset: " + dataset.name);
            System.out.println("  Operation:     " + 
                    (dataset.writeFlag ? "WRITE" : "READ") + " " +
                    (dataset.randomFlag ? "RANDOM" : "SEQUENTIAL"));
            System.out.println("  File:          " + dataset.directory + "/" + dataset.filename);
            System.out.println("  File Size:     " + formatSize(dataset.fileSize));
            System.out.println("  Threads:       " + datasetResults.size());
            System.out.println("  Run Time:      " + String.format("%.2f", elapsedSeconds) + " seconds");
            System.out.println("  Total IO:      " + formatSize(totalBytes));
            System.out.println("  Bandwidth:     " + String.format("%.2f", bandwidthMBps) + " MB/s");
            System.out.println("  IOPS:          " + String.format("%.2f", iops));
            System.out.println("  Latency:       " + 
                    String.format("%.2f", (elapsedSeconds * 1_000_000_000.0) / (totalBytes / BLOCK_SIZE)) + 
                    " ns per operation");
            
            // 显示每个线程的详细信息（仅在调试时启用）
            if (datasetResults.size() > 1) {
                System.out.println("  Per-thread performance:");
                for (ThreadResult result : datasetResults) {
                    double threadElapsed = (result.endTime - result.startTime) / 1_000_000_000.0;
                    double threadBW = (result.ioBytes / threadElapsed) / (1024.0 * 1024.0);
                    System.out.printf("    Thread %d: %s, BW: %.2f MB/s\n",
                            result.threadId, formatSize(result.ioBytes), threadBW);
                }
            }
        }
        
        // 显示汇总信息
        System.out.println("\n" + "-".repeat(60));
        System.out.println("SUMMARY");
        System.out.println("-".repeat(60));
        
        long totalAllBytes = results.stream().mapToLong(r -> r.ioBytes).sum();
        double totalTime = results.stream()
                .mapToLong(r -> r.endTime - r.startTime)
                .average()
                .orElse(0) / 1_000_000_000.0;
        
        System.out.println("Total datasets tested: " + cmd.datasets.size());
        System.out.println("Total threads used:    " + results.size());
        System.out.println("Total IO processed:    " + formatSize(totalAllBytes));
        System.out.println("Average run time:      " + String.format("%.2f", totalTime) + " seconds");
        System.out.println("Aggregate bandwidth:   " + 
                String.format("%.2f", (totalAllBytes / totalTime) / (1024.0 * 1024.0)) + " MB/s");
        System.out.println("=".repeat(60));
    }
    
    // 格式化文件大小
    private static String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        } else if (bytes < 1024L * 1024 * 1024 * 1024) {
            return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        } else {
            return String.format("%.2f TB", bytes / (1024.0 * 1024.0 * 1024.0 * 1024.0));
        }
    }
    
    // 主函数
    public static void main(String[] args) {
        try {
            // 解析命令行参数
            CommandArgs cmd = parseCommandArgs(args);
            
            // 解析数据集文件
            cmd.datasets = parseDatasetFile(cmd.datasetFile);
            
            // 自动调整线程分配
            if (cmd.threadPerDataset <= 0) {
                cmd.threadPerDataset = cmd.threadNum / cmd.datasets.size();
                if (cmd.threadPerDataset < 1) {
                    cmd.threadPerDataset = 1;
                }
                System.out.printf("Auto-adjusted threads per dataset to: %d\n", cmd.threadPerDataset);
            }
            
            // 执行IO测试
            executeIOTest(cmd);
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    // 示例数据集文件生成工具
    public static void generateSampleDatasetFile(String filename) throws IOException {
        String content = """
                # FIO Benchmark Dataset Configuration
                # Format: name,filename,directory,filesize,operation,pattern
                #
                # Supported operations: read/r, write/w
                # Supported patterns: sequential/seq, random/rand
                # Supported size units: B, K, M, G, T
                
                seq_write,seq_write.dat,/tmp/fio_bench,1G,write,sequential
                rand_write,rand_write.dat,/tmp/fio_bench,500M,write,random
                seq_read,seq_read.dat,/tmp/fio_bench,1G,read,sequential
                rand_read,rand_read.dat,/tmp/fio_bench,500M,read,random
                mixed1,mixed1.dat,/tmp/fio_bench,2G,write,random
                mixed2,mixed2.dat,/tmp/fio_bench,2G,read,sequential
                """;
        
        Files.write(Paths.get(filename), content.getBytes());
        System.out.println("Sample dataset file created: " + filename);
    }
}