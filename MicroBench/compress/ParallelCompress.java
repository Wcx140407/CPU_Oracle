import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.*;

/**
 * 并行压缩测试程序（完整LZW实现）
 * 支持自定义输入数据集和多线程处理
 * 
 * 使用方法：
 *   java ParallelCompress <线程数> <配置文件名> [循环次数]
 * 
 * 参数说明：
 *   <线程数>：并行处理的线程数量
 *   <配置文件名>：包含数据文件列表的配置文件
 *   [循环次数]：每个文件的处理轮数（可选，默认1次）
 * 
 * 配置文件格式：
 *   第一行：标题（可选，会被忽略）
 *   后续行：每个数据文件的编号，每行一个数字
 * 
 * 示例配置文件dataset.conf：
 *   Dataset Configuration
 *   1
 *   2
 *   3
 *   4
 *   5
 * 
 * 数据文件组织：
 *   程序会在当前目录下的dataset/子目录中查找文件
 *   例如：dataset/1, dataset/2, dataset/3等
 */
public class ParallelCompress {
    
    // LZW压缩常量
    private static final int BITS = 16;
    private static final int INIT_BITS = 9;
    private static final int FIRST = 257;
    private static final int CLEAR = 256;
    private static final byte MAGIC_1 = 0x1F;
    private static final byte MAGIC_2 = (byte)0x9D;
    private static final int BIT_MASK = 0x1F;
    private static final int BLOCK_MODE = 0x80;
    
    // 缓冲区大小
    private static final int IBUFSIZ = 8192;
    private static final int OBUFSIZ = 8192;
    
    // 并行处理相关变量
    private static int numThreads = 4;
    private static int roundsPerFile = 1;
    private static final AtomicInteger activeThreads = new AtomicInteger(0);
    private static final AtomicInteger processedFiles = new AtomicInteger(0);
    private static int totalFiles = 0;
    
    // 线程安全的队列和锁
    private static final BlockingQueue<Task> taskQueue = new LinkedBlockingQueue<>();
    private static final Lock progressLock = new ReentrantLock();
    private static final Condition allTasksDone = progressLock.newCondition();
    private static volatile boolean shutdownRequested = false;
    
    // 用于统计时间
    private static long startTime;
    private static long endTime;
    
    /**
     * 任务类，表示一个文件处理任务
     */
    static class Task {
        int fileId;          // 文件编号
        int rounds;          // 处理轮数
        long startTime;      // 开始时间
        long endTime;        // 结束时间
        
        Task(int fileId, int rounds) {
            this.fileId = fileId;
            this.rounds = rounds;
        }
    }
    
    /**
     * 完整的LZW压缩实现
     */
    static class LZWCompressor {
        // 哈希表大小
        private static final int HSIZE = 69001;
        
        // 哈希表
        private int[] htab;
        private short[] codetab;
        
        // 最大代码值
        private int maxbits;
        private int maxmaxcode;
        private int maxcode;
        
        // 当前代码位数
        private int n_bits;
        
        // 输出缓冲区
        private byte[] outbuf;
        private int outbits;
        private int boff;
        
        // 统计
        private long bytesIn;
        private long bytesOut;
        
        public LZWCompressor(int maxbits) {
            this.maxbits = maxbits;
            this.maxmaxcode = 1 << maxbits;
            this.htab = new int[HSIZE];
            this.codetab = new short[HSIZE];
            this.outbuf = new byte[OBUFSIZ + 2048];
        }
        
        /**
         * 压缩字节数组
         */
        public byte[] compress(byte[] input) {
            initCompress();
            
            int rpos = 0;
            int rsize = input.length;
            int rlop = 0;
            
            // 初始状态
            int free_ent = FIRST;
            int checkpoint = 10000; // CHECK_GAP
            int ratio = 0;
            int stcode = 1;
            int extcode = (1 << INIT_BITS) + 1;
            n_bits = INIT_BITS;
            
            // 第一个字符
            long fcode = 0;
            int fcode_ent = (rsize > 0) ? (input[0] & 0xFF) : 0;
            int fcode_c = 0;
            
            if (rsize > 0) {
                rpos = 1;
            }
            
            clearHash();
            
            do {
                if (free_ent >= extcode && fcode_ent < FIRST) {
                    if (n_bits < maxbits) {
                        boff = outbits = (outbits - 1) + ((n_bits << 3) -
                                ((outbits - boff - 1 + (n_bits << 3)) % (n_bits << 3)));
                        if (++n_bits < maxbits)
                            extcode = (1 << n_bits) + 1;
                        else
                            extcode = 1 << n_bits;
                    } else {
                        extcode = (1 << 16) + OBUFSIZ;
                        stcode = 0;
                    }
                }
                
                if (!stcode && bytesIn >= checkpoint && fcode_ent < FIRST) {
                    long rat;
                    checkpoint = bytesIn + 10000; // CHECK_GAP
                    
                    if (bytesIn > 0x007fffff) {
                        rat = (bytesOut + (outbits >> 3)) >> 8;
                        if (rat == 0)
                            rat = 0x7fffffff;
                        else
                            rat = bytesIn / rat;
                    } else {
                        rat = (bytesIn << 8) / (bytesOut + (outbits >> 3));
                    }
                    
                    if (rat >= ratio)
                        ratio = (int)rat;
                    else {
                        ratio = 0;
                        clearHash();
                        outputCode(CLEAR, n_bits);
                        boff = outbits = (outbits - 1) + ((n_bits << 3) -
                                ((outbits - boff - 1 + (n_bits << 3)) % (n_bits << 3)));
                        extcode = (1 << (n_bits = INIT_BITS)) + 1;
                        free_ent = FIRST;
                        stcode = 1;
                    }
                }
                
                if (outbits >= (OBUFSIZ << 3)) {
                    flushOutputBuffer();
                }
                
                // 处理输入数据
                int i = rsize - rlop;
                if (i > extcode - free_ent) i = extcode - free_ent;
                if (i > ((outbuf.length - 32) * 8 - outbits) / n_bits)
                    i = ((outbuf.length - 32) * 8 - outbits) / n_bits;
                if (!stcode && i > checkpoint - bytesIn)
                    i = (int)(checkpoint - bytesIn);
                
                rlop += i;
                bytesIn += i;
                
                goto_next:
                if (rpos >= rlop)
                    goto endloop;
                
                fcode_c = input[rpos++] & 0xFF;
                
                // 查找哈希表
                long hp = (((long)fcode_c) << (BITS - 8)) ^ fcode_ent;
                int index = (int)(hp % HSIZE);
                
                long search_fcode = ((long)fcode_ent << 8) | fcode_c;
                
                while (htab[index] != -1) {
                    if (htab[index] == search_fcode) {
                        fcode_ent = codetab[index];
                        goto goto_next;
                    }
                    index = (index + 1) % HSIZE; // 线性探测
                }
                
                // 输出代码
                outputCode(fcode_ent, n_bits);
                
                // 添加到哈希表
                if (stcode) {
                    codetab[index] = (short)free_ent++;
                    htab[index] = (int)search_fcode;
                }
                
                fcode_ent = fcode_c;
                goto goto_next;
                
                endloop:
                if (fcode_ent >= FIRST && rpos < rsize)
                    continue;
                
                if (rpos > rlop) {
                    bytesIn += rpos - rlop;
                    rlop = rpos;
                }
            } while (rlop < rsize);
            
            // 输出最后一个代码
            if (bytesIn > 0)
                outputCode(fcode_ent, n_bits);
            
            return getOutput();
        }
        
        /**
         * 初始化压缩
         */
        private void initCompress() {
            clearHash();
            Arrays.fill(outbuf, 0, outbuf.length, (byte)0);
            outbuf[0] = MAGIC_1;
            outbuf[1] = MAGIC_2;
            outbuf[2] = (byte)(maxbits | BLOCK_MODE);
            boff = outbits = 3 << 3;
            bytesIn = 0;
            bytesOut = 0;
        }
        
        /**
         * 清空哈希表
         */
        private void clearHash() {
            Arrays.fill(htab, -1);
            Arrays.fill(codetab, 0, 256, (short)0);
        }
        
        /**
         * 输出一个代码
         */
        private void outputCode(int code, int n_bits) {
            int bitPos = outbits & 0x7;
            int bytePos = outbits >> 3;
            
            long value = ((long)code) << bitPos;
            
            outbuf[bytePos] |= (byte)(value & 0xFF);
            if (bytePos + 1 < outbuf.length)
                outbuf[bytePos + 1] |= (byte)((value >> 8) & 0xFF);
            if (bytePos + 2 < outbuf.length)
                outbuf[bytePos + 2] |= (byte)((value >> 16) & 0xFF);
            
            outbits += n_bits;
        }
        
        /**
         * 刷新输出缓冲区
         */
        private void flushOutputBuffer() {
            int bytesToWrite = OBUFSIZ;
            bytesOut += bytesToWrite;
            outbits -= OBUFSIZ << 3;
            boff = -(((OBUFSIZ << 3) - boff) % (n_bits << 3));
            
            // 移动剩余数据
            System.arraycopy(outbuf, OBUFSIZ, outbuf, 0, (outbits >> 3) + 1);
            Arrays.fill(outbuf, (outbits >> 3) + 1, OBUFSIZ, (byte)0);
        }
        
        /**
         * 获取压缩后的数据
         */
        private byte[] getOutput() {
            int outputSize = (outbits + 7) >> 3;
            byte[] result = new byte[outputSize];
            System.arraycopy(outbuf, 0, result, 0, outputSize);
            bytesOut += outputSize;
            return result;
        }
        
        public long getBytesIn() { return bytesIn; }
        public long getBytesOut() { return bytesOut; }
    }
    
    /**
     * 完整的LZW解压实现
     */
    static class LZWDecompressor {
        // 最大代码表大小
        private int maxbits;
        private int maxmaxcode;
        
        // 代码表
        private short[] prefix = new short[65536];
        private byte[] suffix = new byte[65536];
        
        // 解压栈
        private byte[] stack = new byte[8000];
        
        public LZWDecompressor(int maxbits) {
            this.maxbits = maxbits;
            this.maxmaxcode = 1 << maxbits;
        }
        
        /**
         * 解压字节数组
         */
        public byte[] decompress(byte[] input) throws IOException {
            if (input.length < 3 || input[0] != MAGIC_1 || input[1] != MAGIC_2) {
                throw new IOException("Invalid compressed file format");
            }
            
            int inbits = (input[2] & BIT_MASK);
            boolean block_mode = (input[2] & BLOCK_MODE) != 0;
            
            if (inbits > BITS) {
                throw new IOException(String.format(
                        "Compressed with %d bits, can only handle %d bits", inbits, BITS));
            }
            
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            int bitpos = 3 << 3; // 跳过3字节头
            
            int n_bits = INIT_BITS;
            int maxcode = (1 << n_bits) - 1;
            int bitmask = (1 << n_bits) - 1;
            int oldcode = -1;
            int finchar = 0;
            int free_ent = block_mode ? FIRST : 256;
            
            // 初始化前256个条目
            for (int i = 0; i < 256; i++) {
                suffix[i] = (byte)i;
            }
            
            clearPrefix();
            
            while (bitpos < (input.length << 3) - n_bits) {
                if (free_ent > maxcode) {
                    n_bits++;
                    if (n_bits == inbits)
                        maxcode = maxmaxcode;
                    else
                        maxcode = (1 << n_bits) - 1;
                    bitmask = (1 << n_bits) - 1;
                }
                
                int code = getCode(input, bitpos, n_bits, bitmask);
                bitpos += n_bits;
                
                if (oldcode == -1) {
                    if (code >= 256) {
                        throw new IOException("Corrupt input: first code >= 256");
                    }
                    output.write(code);
                    finchar = code;
                    oldcode = code;
                    continue;
                }
                
                if (code == CLEAR && block_mode) {
                    clearPrefix();
                    free_ent = FIRST - 1;
                    n_bits = INIT_BITS;
                    maxcode = (1 << n_bits) - 1;
                    bitmask = (1 << n_bits) - 1;
                    continue;
                }
                
                int incode = code;
                int stackp = stack.length;
                
                if (code >= free_ent) {
                    if (code > free_ent) {
                        throw new IOException("Corrupt input: code > free_ent");
                    }
                    stack[--stackp] = (byte)finchar;
                    code = oldcode;
                }
                
                while (code >= 256) {
                    stack[--stackp] = suffix[code];
                    code = prefix[code];
                }
                
                finchar = suffix[code];
                stack[--stackp] = (byte)finchar;
                
                // 输出栈中的字符
                int count = stack.length - stackp;
                output.write(stack, stackp, count);
                
                if (free_ent < maxmaxcode) {
                    prefix[free_ent] = (short)oldcode;
                    suffix[free_ent] = (byte)finchar;
                    free_ent++;
                }
                
                oldcode = incode;
            }
            
            return output.toByteArray();
        }
        
        /**
         * 从位流中读取代码
         */
        private int getCode(byte[] input, int bitpos, int n_bits, int bitmask) {
            int bytepos = bitpos >> 3;
            int bitoffset = bitpos & 0x7;
            
            int code = 0;
            for (int i = 0; i < 3 && bytepos + i < input.length; i++) {
                code |= (input[bytepos + i] & 0xFF) << (i * 8);
            }
            code >>= bitoffset;
            code &= bitmask;
            
            return code;
        }
        
        /**
         * 清空前缀表
         */
        private void clearPrefix() {
            Arrays.fill(prefix, 0, 256, (short)0);
        }
    }
    
    /**
     * 线程局部数据类
     */
    static class ThreadLocalData {
        boolean doDecomp = false;
        boolean force = true;
        boolean quiet = true;
        int maxbits = BITS;
        LZWCompressor compressor;
        LZWDecompressor decompressor;
        int fileId = 0;
        
        ThreadLocalData() {
            this.compressor = new LZWCompressor(maxbits);
            this.decompressor = new LZWDecompressor(maxbits);
        }
    }
    
    /**
     * 工作线程类
     */
    static class WorkerThread implements Runnable {
        private final int threadNum;
        private final ThreadLocalData localData;
        
        WorkerThread(int threadNum) {
            this.threadNum = threadNum;
            this.localData = new ThreadLocalData();
        }
        
        @Override
        public void run() {
            System.out.printf("Thread %d started%n", threadNum);
            
            try {
                while (!shutdownRequested) {
                    Task task = taskQueue.poll(1, TimeUnit.SECONDS);
                    
                    if (task == null) {
                        progressLock.lock();
                        try {
                            if (taskQueue.isEmpty() && activeThreads.get() == 0) {
                                allTasksDone.signal();
                                break;
                            }
                        } finally {
                            progressLock.unlock();
                        }
                        continue;
                    }
                    
                    activeThreads.incrementAndGet();
                    task.startTime = System.currentTimeMillis();
                    
                    System.out.printf("Thread %d starting file %d, time: %d%n", 
                            threadNum, task.fileId, task.startTime);
                    
                    processFileTask(task);
                    
                    task.endTime = System.currentTimeMillis();
                    System.out.printf("Thread %d finished file %d, time: %d, duration: %dms%n", 
                            threadNum, task.fileId, task.endTime, 
                            task.endTime - task.startTime);
                    
                    int processed = processedFiles.incrementAndGet();
                    if (processed % 10 == 0 || processed == totalFiles) {
                        System.out.printf("Progress: %d/%d files processed (%.1f%%)%n", 
                                processed, totalFiles, 100.0 * processed / totalFiles);
                    }
                    
                    activeThreads.decrementAndGet();
                    
                    progressLock.lock();
                    try {
                        if (taskQueue.isEmpty() && activeThreads.get() == 0) {
                            allTasksDone.signal();
                        }
                    } finally {
                        progressLock.unlock();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.printf("Thread %d interrupted%n", threadNum);
            } catch (Exception e) {
                System.err.printf("Thread %d error: %s%n", threadNum, e.getMessage());
                e.printStackTrace();
            } finally {
                System.out.printf("Thread %d exited%n", threadNum);
            }
        }
        
        /**
         * 处理单个文件任务
         */
        private void processFileTask(Task task) {
            localData.fileId = task.fileId;
            
            String filename = "dataset/" + task.fileId;
            
            for (int n = 0; n < task.rounds; n++) {
                // 压缩模式
                localData.doDecomp = false;
                try {
                    compressFile(filename, localData);
                } catch (IOException e) {
                    System.err.printf("Thread %d: Compression error for file %d: %s%n", 
                            threadNum, task.fileId, e.getMessage());
                }
                
                // 解压模式
                localData.doDecomp = true;
                try {
                    decompressFile(filename + ".Z", localData);
                } catch (IOException e) {
                    System.err.printf("Thread %d: Decompression error for file %d: %s%n", 
                            threadNum, task.fileId, e.getMessage());
                }
            }
        }
        
        /**
         * 压缩文件
         */
        private void compressFile(String filename, ThreadLocalData data) throws IOException {
            File inputFile = new File(filename);
            File outputFile = new File(filename + ".Z");
            
            if (!inputFile.exists()) {
                throw new IOException("Input file does not exist: " + filename);
            }
            
            if (outputFile.exists() && !data.force) {
                throw new IOException("Output file already exists: " + outputFile.getName());
            }
            
            // 读取整个文件（对于大文件应该分块处理）
            byte[] inputData = Files.readAllBytes(inputFile.toPath());
            
            // 使用LZW压缩
            byte[] compressedData = data.compressor.compress(inputData);
            
            // 写入压缩文件
            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                fos.write(compressedData);
            }
            
            long bytesIn = data.compressor.getBytesIn();
            long bytesOut = data.compressor.getBytesOut();
            
            if (!data.quiet) {
                double ratio = (bytesIn > 0) ? 
                        (100.0 * (bytesIn - bytesOut) / bytesIn) : 0.0;
                System.out.printf("Thread %d: Compressed %s -> %s (%.2f%%, %d -> %d bytes)%n", 
                        threadNum, filename, outputFile.getName(), ratio, bytesIn, bytesOut);
            }
        }
        
        /**
         * 解压文件
         */
        private void decompressFile(String filename, ThreadLocalData data) throws IOException {
            File inputFile = new File(filename);
            String outputFilename = filename.endsWith(".Z") ? 
                    filename.substring(0, filename.length() - 2) : filename;
            File outputFile = new File(outputFilename);
            
            if (!inputFile.exists()) {
                throw new IOException("Input file does not exist: " + filename);
            }
            
            if (outputFile.exists() && !data.force) {
                throw new IOException("Output file already exists: " + outputFile.getName());
            }
            
            // 读取压缩文件
            byte[] compressedData = Files.readAllBytes(inputFile.toPath());
            
            // 使用LZW解压
            byte[] decompressedData = data.decompressor.decompress(compressedData);
            
            // 写入解压文件
            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                fos.write(decompressedData);
            }
            
            if (!data.quiet) {
                System.out.printf("Thread %d: Decompressed %s -> %s (%d bytes)%n", 
                        threadNum, filename, outputFile.getName(), decompressedData.length);
            }
        }
    }
    
    /**
     * 读取配置文件
     */
    private static List<Integer> readConfigFile(String configFilename) throws IOException {
        List<Integer> fileIds = new ArrayList<>();
        
        try (BufferedReader reader = new BufferedReader(new FileReader(configFilename))) {
            String line;
            int lineNum = 0;
            
            while ((line = reader.readLine()) != null) {
                lineNum++;
                line = line.trim();
                
                if (line.isEmpty() || line.startsWith("#")) {
                    if (lineNum == 1 && !line.isEmpty() && !line.startsWith("#")) {
                        System.out.println("Ignoring header line: " + line);
                    }
                    continue;
                }
                
                try {
                    int fileId = Integer.parseInt(line);
                    fileIds.add(fileId);
                } catch (NumberFormatException e) {
                    if (lineNum == 1) {
                        System.out.println("Ignoring header line: " + line);
                    } else {
                        System.err.printf("Warning: Line %d is not a valid number: %s%n", 
                                lineNum, line);
                    }
                }
            }
        }
        
        return fileIds;
    }
    
    /**
     * 检查数据集目录和文件
     */
    private static boolean checkDatasetFiles(List<Integer> fileIds) {
        File datasetDir = new File("dataset");
        
        if (!datasetDir.exists() || !datasetDir.isDirectory()) {
            System.err.println("Warning: dataset/ directory does not exist or is not a directory");
            System.err.println("Creating dataset/ directory...");
            
            if (!datasetDir.mkdir()) {
                System.err.println("Error: Cannot create dataset/ directory");
                return false;
            }
            
            System.out.println("Created dataset/ directory");
            System.out.println("You need to add your data files manually");
            return false;
        }
        
        System.out.println("Checking dataset files...");
        int missingCount = 0;
        int checkCount = Math.min(fileIds.size(), 5);
        
        for (int i = 0; i < checkCount; i++) {
            File testFile = new File("dataset/" + fileIds.get(i));
            if (testFile.exists()) {
                System.out.printf("  ✓ File exists: dataset/%d (%,d bytes)%n", 
                        fileIds.get(i), testFile.length());
            } else {
                System.out.printf("  ✗ File missing: dataset/%d%n", fileIds.get(i));
                missingCount++;
            }
        }
        
        if (missingCount > 0 && checkCount < fileIds.size()) {
            System.out.printf("  ... and %d more files to check%n", fileIds.size() - checkCount);
        }
        
        if (missingCount == checkCount && checkCount > 0) {
            System.out.println("\nWarning: No data files found in dataset/ directory!");
            System.out.println("You need to create test files. Example commands:");
            System.out.printf("  for i in {%d..%d}; do%n", fileIds.get(0), fileIds.get(fileIds.size()-1));
            System.out.println("    dd if=/dev/urandom of=dataset/$i bs=1M count=1");
            System.out.println("  done");
            
            System.out.print("\nContinue anyway? (y/n): ");
            try {
                BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
                String response = br.readLine();
                return response != null && (response.equalsIgnoreCase("y") || response.equalsIgnoreCase("yes"));
            } catch (IOException e) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * 创建测试文件（用于演示）
     */
    private static void createTestFiles(List<Integer> fileIds) {
        System.out.println("Creating test files...");
        Random random = new Random();
        
        for (int fileId : fileIds) {
            File testFile = new File("dataset/" + fileId);
            if (!testFile.exists()) {
                try (FileOutputStream fos = new FileOutputStream(testFile)) {
                    // 创建不同大小的测试文件
                    int size = 1024 * 1024; // 1MB
                    if (fileId % 3 == 0) size = 512 * 1024; // 512KB
                    if (fileId % 5 == 0) size = 2 * 1024 * 1024; // 2MB
                    
                    byte[] buffer = new byte[size];
                    random.nextBytes(buffer);
                    fos.write(buffer);
                    System.out.printf("  Created: dataset/%d (%,d bytes)%n", fileId, size);
                } catch (IOException e) {
                    System.err.printf("  Error creating dataset/%d: %s%n", fileId, e.getMessage());
                }
            }
        }
    }
    
    /**
     * 测试LZW压缩算法的基本功能
     */
    private static void testLZWAlgorithm() {
        System.out.println("Testing LZW algorithm...");
        
        String testString = "TOBEORNOTTOBEORTOBEORNOT";
        byte[] testData = testString.getBytes();
        
        LZWCompressor compressor = new LZWCompressor(BITS);
        LZWDecompressor decompressor = new LZWDecompressor(BITS);
        
        try {
            byte[] compressed = compressor.compress(testData);
            byte[] decompressed = decompressor.decompress(compressed);
            
            String result = new String(decompressed);
            boolean success = testString.equals(result);
            
            System.out.printf("  Test string: %s%n", testString);
            System.out.printf("  Original size: %d bytes%n", testData.length);
            System.out.printf("  Compressed size: %d bytes%n", compressed.length);
            System.out.printf("  Decompressed: %s%n", result);
            System.out.printf("  Test %s%n", success ? "PASSED" : "FAILED");
            
            if (!success) {
                System.err.println("Warning: LZW algorithm test failed!");
            }
        } catch (Exception e) {
            System.err.printf("LZW test error: %s%n", e.getMessage());
        }
    }
    
    /**
     * 主函数
     */
    public static void main(String[] args) {
        // 检查参数
        if (args.length < 2) {
            printUsage();
            System.exit(1);
        }
        
        try {
            // 解析参数
            numThreads = Integer.parseInt(args[0]);
            if (numThreads <= 0 || numThreads > 64) {
                System.err.println("Error: Number of threads must be between 1 and 64");
                System.exit(1);
            }
            
            String configFilename = args[1];
            
            if (args.length >= 3) {
                roundsPerFile = Integer.parseInt(args[2]);
                if (roundsPerFile <= 0) {
                    roundsPerFile = 1;
                }
            }
            
            // 测试LZW算法
            testLZWAlgorithm();
            
            // 读取配置文件
            System.out.println("\nReading config file: " + configFilename);
            List<Integer> fileIds = readConfigFile(configFilename);
            
            if (fileIds.isEmpty()) {
                System.err.println("\nError: No valid file IDs found in config file!");
                System.err.println("Config file should contain one file ID per line.");
                System.err.println("Example config file content:");
                System.err.println("1");
                System.err.println("2");
                System.err.println("3");
                System.exit(1);
            }
            
            System.out.printf("\nSuccessfully read %d file IDs from config file%n", fileIds.size());
            System.out.print("File IDs: ");
            for (int i = 0; i < Math.min(fileIds.size(), 20); i++) {
                System.out.print(fileIds.get(i) + " ");
            }
            if (fileIds.size() > 20) {
                System.out.printf("... and %d more", fileIds.size() - 20);
            }
            System.out.println();
            
            // 检查数据集
            if (!checkDatasetFiles(fileIds)) {
                System.out.print("\nCreate test files automatically? (y/n): ");
                BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
                String response = br.readLine();
                if (response != null && response.equalsIgnoreCase("y")) {
                    createTestFiles(fileIds);
                } else {
                    System.out.println("Please create test files manually and try again.");
                    System.exit(1);
                }
            }
            
            System.out.println("\n========================================");
            System.out.println("Parallel LZW Compression Program");
            System.out.println("Threads: " + numThreads);
            System.out.println("Config file: " + configFilename);
            System.out.println("Files to process: " + fileIds.size());
            System.out.println("Rounds per file: " + roundsPerFile);
            System.out.println("Max bits: " + BITS);
            System.out.println("========================================\n");
            
            // 创建任务
            totalFiles = fileIds.size();
            for (int fileId : fileIds) {
                taskQueue.offer(new Task(fileId, roundsPerFile));
            }
            
            // 创建并启动工作线程
            ExecutorService executor = Executors.newFixedThreadPool(numThreads);
            for (int i = 0; i < numThreads; i++) {
                executor.submit(new WorkerThread(i + 1));
            }
            
            startTime = System.currentTimeMillis();
            System.out.println("Processing started at: " + startTime);
            System.out.println("Press Ctrl+C to stop\n");
            
            // 添加关闭钩子
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                shutdownRequested = true;
                executor.shutdown();
                try {
                    if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                        executor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    executor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }));
            
            // 监视进度
            while (!shutdownRequested && processedFiles.get() < totalFiles) {
                Thread.sleep(1000);
                
                progressLock.lock();
                try {
                    if (taskQueue.isEmpty() && activeThreads.get() == 0) {
                        allTasksDone.await(2, TimeUnit.SECONDS);
                    }
                } finally {
                    progressLock.unlock();
                }
            }
            
            // 优雅关闭
            shutdownRequested = true;
            executor.shutdown();
            
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
            
            endTime = System.currentTimeMillis();
            long totalTime = endTime - startTime;
            
            System.out.println("\n========================================");
            System.out.println("All tasks completed!");
            System.out.println("Total files processed: " + processedFiles.get());
            System.out.println("Total rounds per file: " + roundsPerFile);
            System.out.println("Total operations: " + (fileIds.size() * roundsPerFile * 2));
            System.out.printf("Total time: %d ms (%.2f seconds)%n", 
                    totalTime, totalTime / 1000.0);
            System.out.printf("Average time per file: %.2f ms%n", 
                    (double)totalTime / fileIds.size());
            System.out.printf("Operations per second: %.2f%n", 
                    (fileIds.size() * roundsPerFile * 2 * 1000.0) / totalTime);
            System.out.println("========================================");
            
        } catch (NumberFormatException e) {
            System.err.println("Error: Invalid number format in arguments");
            printUsage();
            System.exit(1);
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Program interrupted");
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Unexpected error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     * 打印使用说明
     */
    private static void printUsage() {
        System.err.println("Usage: java ParallelCompress <num_threads> <config_file> [rounds_per_file]");
        System.err.println("Example: java ParallelCompress 4 dataset.conf 5");
        System.err.println("\nArguments:");
        System.err.println("  num_threads: Number of worker threads (1-64)");
        System.err.println("  config_file: Configuration file containing file IDs");
        System.err.println("  rounds_per_file: Number of compression/decompression rounds per file (optional, default 1)");
        System.err.println("\nConfig file format:");
        System.err.println("  One file ID per line, e.g.:");
        System.err.println("  1");
        System.err.println("  2");
        System.err.println("  3");
        System.err.println("\nFiles should be in dataset/ directory as dataset/1, dataset/2, etc.");
    }
}