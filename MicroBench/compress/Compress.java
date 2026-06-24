import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.*;
import java.time.Instant;

/**
 * 并行压缩/解压程序 - Java版本
 * 
 * 使用方法：
 *   java Compress <线程数> <配置文件名> [循环次数]
 *   
 * 参数说明：
 *   <线程数>：并行处理的线程数量（例如：4 表示4个线程）
 *   <配置文件名>：包含数据文件列表的配置文件
 *   [循环次数]：每个文件的压缩/解压循环次数（可选，默认1次）
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
public class Compress {
    
    /* ===================== 常量定义 ===================== */
    private static final char MAGIC_1 = (char)0x1F;  // 压缩文件第一个魔数
    private static final char MAGIC_2 = (char)0x9D;  // 压缩文件第二个魔数
    private static final int BIT_MASK = 0x1F;        // 压缩位数掩码
    private static final int BLOCK_MODE = 0x80;      // 块压缩模式
    
    private static final int FIRST = 257;            // 第一个空闲条目
    private static final int CLEAR = 256;            // 清除表的输出码
    
    private static final int INIT_BITS = 9;          // 初始位数/编码
    private static final int BITS = 16;              // 默认位数
    private static final int HSIZE = 69001;          // 哈希表大小
    private static final int CHECK_GAP = 10000;      // 检查间隔
    
    private static final int IBUFSIZ = 8192;         // 输入缓冲区大小
    private static final int OBUFSIZ = 8192;         // 输出缓冲区大小
    
    /* ===================== 线程局部数据 ===================== */
    static class ThreadLocalData {
        // 压缩/解压状态
        boolean doDecomp = false;      // 解压模式标志
        boolean force = true;          // 强制覆盖标志
        boolean quiet = true;          // 静默模式标志
        int maxbits = BITS;            // 最大位数
        int exitCode = -1;             // 退出代码
        
        // 文件信息
        String ifname;                 // 输入文件名
        String ofname;                 // 输出文件名
        boolean removeOfname = false;  // 删除输出文件标志
        
        // 统计信息
        long bytesIn = 0;              // 输入字节数
        long bytesOut = 0;             // 输出字节数
        
        // 缓冲区
        byte[] inbuf = new byte[IBUFSIZ + 64];
        byte[] outbuf = new byte[OBUFSIZ + 2048];
        
        // 哈希表和编码表
        int[] htab = new int[HSIZE];
        short[] codetab = new short[HSIZE];
        
        // 文件ID（用于日志）
        int fileId;
        
        ThreadLocalData() {
            clearHtab();
            clearTabPrefixof();
        }
        
        void clearHtab() {
            Arrays.fill(htab, -1);
        }
        
        void clearTabPrefixof() {
            Arrays.fill(codetab, 0, 256, (short)0);
        }
    }
    
    /* ===================== 任务结构体 ===================== */
    static class Task {
        int fileId;                    // 数据文件编号
        int rounds;                    // 每个文件的压缩/解压轮数
        long startTime;                // 开始时间
        long endTime;                  // 结束时间
        
        Task(int fileId, int rounds) {
            this.fileId = fileId;
            this.rounds = rounds;
        }
    }
    
    /* ===================== 全局变量 ===================== */
    private static int numThreads = 4;              // 线程数
    private static int roundsPerFile = 1;           // 每文件处理轮数
    private static int totalFiles = 0;              // 总文件数
    private static String configFile;               // 配置文件
    
    private static final BlockingQueue<Task> taskQueue = new LinkedBlockingQueue<>();
    private static final ExecutorService executor = Executors.newCachedThreadPool();
    private static final AtomicInteger activeThreads = new AtomicInteger(0);
    private static final AtomicInteger processedFiles = new AtomicInteger(0);
    
    private static final Lock progressLock = new ReentrantLock();
    private static final Condition allTasksDone = progressLock.newCondition();
    private static volatile boolean shutdown = false;
    
    /* ===================== 主函数 ===================== */
    public static void main(String[] args) {
        System.out.println("并行压缩程序 - Java版本");
        System.out.println("========================");
        
        // 检查参数
        if (args.length < 2) {
            printUsage();
            return;
        }
        
        // 解析参数
        try {
            numThreads = Integer.parseInt(args[0]);
            if (numThreads <= 0 || numThreads > 256) {
                System.err.println("错误: 线程数必须在1-256之间");
                return;
            }
            
            configFile = args[1];
            
            if (args.length >= 3) {
                roundsPerFile = Integer.parseInt(args[2]);
                if (roundsPerFile <= 0) {
                    roundsPerFile = 1;
                }
            }
        } catch (NumberFormatException e) {
            System.err.println("错误: 参数格式不正确");
            printUsage();
            return;
        }
        
        // 读取配置文件
        List<Integer> fileIds = readConfigFile(configFile);
        if (fileIds.isEmpty()) {
            System.err.println("错误: 配置文件中没有找到有效的文件编号");
            return;
        }
        
        totalFiles = fileIds.size();
        
        // 检查数据集目录
        if (!Files.exists(Paths.get("dataset"))) {
            System.err.println("警告: dataset/ 目录不存在");
            System.err.println("请创建 dataset/ 目录并添加数据文件");
            System.err.println("例如: mkdir dataset");
            System.err.println("然后添加文件: dataset/1, dataset/2, 等等");
            return;
        }
        
        // 创建任务队列
        for (int fileId : fileIds) {
            taskQueue.add(new Task(fileId, roundsPerFile));
        }
        
        // 显示启动信息
        System.out.println("\n========================================");
        System.out.println("并行压缩程序启动");
        System.out.println("线程数: " + numThreads);
        System.out.println("配置文件: " + configFile);
        System.out.println("文件总数: " + totalFiles);
        System.out.println("每文件轮数: " + roundsPerFile);
        System.out.println("总任务数: " + (totalFiles * roundsPerFile));
        System.out.println("========================================\n");
        
        // 启动工作线程
        System.out.println("正在创建 " + numThreads + " 个工作线程...");
        
        for (int i = 0; i < numThreads; i++) {
            final int threadNum = i + 1;
            executor.submit(() -> workerThread(threadNum));
        }
        
        long startTime = System.currentTimeMillis();
        System.out.println("\n处理开始时间: " + startTime);
        System.out.println("按 Ctrl+C 停止程序\n");
        
        // 启动进度监视器
        Thread progressMonitor = new Thread(() -> monitorProgress(startTime));
        progressMonitor.start();
        
        // 等待所有任务完成
        try {
            progressLock.lock();
            while (processedFiles.get() < totalFiles && !shutdown) {
                allTasksDone.await(1, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            progressLock.unlock();
        }
        
        // 关闭程序
        shutdown = true;
        executor.shutdown();
        
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;
        
        // 打印统计信息
        printStatistics(totalFiles, roundsPerFile, totalTime);
    }
    
    /* ===================== 工作线程函数 ===================== */
    private static void workerThread(int threadNum) {
        System.out.println("Thread " + threadNum + " started");
        
        try {
            while (!shutdown) {
                Task task = taskQueue.poll(100, TimeUnit.MILLISECONDS);
                if (task == null) {
                    if (shutdown) break;
                    continue;
                }
                
                activeThreads.incrementAndGet();
                
                task.startTime = System.currentTimeMillis();
                System.out.printf("Thread %d starting file %d, time: %d%n", 
                        threadNum, task.fileId, task.startTime);
                
                String filename = "dataset/" + task.fileId;
                
                for (int n = 0; n < task.rounds && !shutdown; n++) {
                    // 压缩模式
                    comprexx(filename, task.fileId, threadNum);
                    
                    // 在实际应用中，这里可以添加解压模式
                    // 为简化起见，我们只进行压缩
                }
                
                task.endTime = System.currentTimeMillis();
                System.out.printf("Thread %d finished file %d, time: %d, duration: %dms%n", 
                        threadNum, task.fileId, task.endTime, 
                        task.endTime - task.startTime);
                
                activeThreads.decrementAndGet();
                int processed = processedFiles.incrementAndGet();
                
                // 通知主线程进度更新
                if (processed >= totalFiles) {
                    progressLock.lock();
                    try {
                        allTasksDone.signalAll();
                    } finally {
                        progressLock.unlock();
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        System.out.println("Thread " + threadNum + " exited");
    }
    
    /* ===================== 进度监视器 ===================== */
    private static void monitorProgress(long startTime) {
        int lastReported = 0;
        
        while (!shutdown && processedFiles.get() < totalFiles) {
            try {
                Thread.sleep(1000); // 休眠1秒
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            
            int processed = processedFiles.get();
            int progress = (processed * 100) / totalFiles;
            
            if (progress >= lastReported + 10 || processed == totalFiles) {
                System.out.printf("进度: %d/%d 文件已处理 (%d%%)%n", 
                        processed, totalFiles, progress);
                lastReported = progress - (progress % 10);
            }
        }
    }
    
    /* ===================== 文件处理函数 ===================== */
    private static void comprexx(String filepath, int fileId, int threadNum) {
        ThreadLocalData data = new ThreadLocalData();
        data.fileId = fileId;
        data.doDecomp = false; // 默认压缩模式
        
        try {
            Path inputPath = Paths.get(filepath);
            
            // 检查文件是否存在
            if (!Files.exists(inputPath)) {
                System.err.printf("Thread %d: 文件不存在: %s%n", threadNum, filepath);
                return;
            }
            
            // 检查是否为普通文件
            if (!Files.isRegularFile(inputPath)) {
                System.err.printf("Thread %d: %s 不是普通文件%n", threadNum, filepath);
                return;
            }
            
            String outputFile = filepath + ".Z";
            
            // 检查输出文件是否已存在
            if (Files.exists(Paths.get(outputFile)) && !data.force) {
                System.err.printf("Thread %d: %s 已存在%n", threadNum, outputFile);
                return;
            }
            
            data.ifname = filepath;
            data.ofname = outputFile;
            data.removeOfname = true;
            
            // 执行压缩
            try (FileInputStream fis = new FileInputStream(inputPath.toFile());
                 FileOutputStream fos = new FileOutputStream(outputFile)) {
                
                compress(fis, fos, data, threadNum);
                
                if (!data.quiet) {
                    if (!data.doDecomp) {
                        double ratio = (data.bytesIn > 0) ? 
                            (100.0 * (data.bytesIn - data.bytesOut) / data.bytesIn) : 0.0;
                        System.out.printf("Thread %d: 压缩 %s -> %s (%.2f%%)%n", 
                                threadNum, filepath, outputFile, ratio);
                    } else {
                        System.out.printf("Thread %d: 解压 %s -> %s%n", 
                                threadNum, filepath, outputFile);
                    }
                }
            }
            
        } catch (IOException e) {
            System.err.printf("Thread %d: 处理文件时出错 %s: %s%n", 
                    threadNum, filepath, e.getMessage());
            
            if (data.removeOfname && data.ofname != null) {
                try {
                    Files.deleteIfExists(Paths.get(data.ofname));
                } catch (IOException ex) {
                    // 忽略删除错误
                }
            }
            
            data.exitCode = 1;
        }
    }
    
    /* ===================== 压缩函数 ===================== */
    private static void compress(InputStream fdin, OutputStream fdout, 
                                ThreadLocalData data, int threadNum) throws IOException {
        int hp;
        int rpos;
        long fc;
        int outbits;
        int rlop;
        int rsize;
        int stcode;
        int freeEnt;
        int boff;
        int nBits;
        int ratio = 0;
        long checkpoint = CHECK_GAP;
        int extcode;
        
        long fcode = 0;
        
        extcode = maxCode(nBits = INIT_BITS) + 1;
        stcode = 1;
        freeEnt = FIRST;
        
        Arrays.fill(data.outbuf, 0, 3, (byte)0);
        data.bytesOut = 0;
        data.bytesIn = 0;
        
        // 写入魔数头
        data.outbuf[0] = (byte)MAGIC_1;
        data.outbuf[1] = (byte)MAGIC_2;
        data.outbuf[2] = (byte)(data.maxbits | BLOCK_MODE);
        
        boff = outbits = (3 << 3);
        
        data.clearHtab();
        
        while ((rsize = fdin.read(data.inbuf, 0, IBUFSIZ)) > 0) {
            if (data.bytesIn == 0) {
                fcode = data.inbuf[0] & 0xFF;
                rpos = 1;
            } else {
                rpos = 0;
            }
            
            rlop = 0;
            
            do {
                if (freeEnt >= extcode && (fcode & 0xFFFF) < FIRST) {
                    if (nBits < data.maxbits) {
                        boff = outbits = (outbits - 1) + ((nBits << 3) -
                                ((outbits - boff - 1 + (nBits << 3)) % (nBits << 3)));
                        if (++nBits < data.maxbits) {
                            extcode = maxCode(nBits) + 1;
                        } else {
                            extcode = maxCode(nBits);
                        }
                    } else {
                        extcode = maxCode(16) + OBUFSIZ;
                        stcode = 0;
                    }
                }
                
                if (stcode == 0 && data.bytesIn >= checkpoint && (fcode & 0xFFFF) < FIRST) {
                    long rat;
                    
                    checkpoint = data.bytesIn + CHECK_GAP;
                    
                    if (data.bytesIn > 0x007fffff) {
                        rat = (data.bytesOut + (outbits >> 3)) >> 8;
                        if (rat == 0) {
                            rat = 0x7fffffff;
                        } else {
                            rat = data.bytesIn / rat;
                        }
                    } else {
                        rat = (data.bytesIn << 8) / (data.bytesOut + (outbits >> 3));
                    }
                    
                    if (rat >= ratio) {
                        ratio = (int)rat;
                    } else {
                        ratio = 0;
                        data.clearHtab();
                        output(data.outbuf, outbits, CLEAR, nBits);
                        boff = outbits = (outbits - 1) + ((nBits << 3) -
                                ((outbits - boff - 1 + (nBits << 3)) % (nBits << 3)));
                        extcode = maxCode(nBits = INIT_BITS) + 1;
                        freeEnt = FIRST;
                        stcode = 1;
                    }
                }
                
                if (outbits >= (OBUFSIZ << 3)) {
                    fdout.write(data.outbuf, 0, OBUFSIZ);
                    outbits -= (OBUFSIZ << 3);
                    boff = -(((OBUFSIZ << 3) - boff) % (nBits << 3));
                    data.bytesOut += OBUFSIZ;
                    
                    System.arraycopy(data.outbuf, OBUFSIZ, data.outbuf, 0, (outbits >> 3) + 1);
                    Arrays.fill(data.outbuf, (outbits >> 3) + 1, OBUFSIZ, (byte)0);
                }
                
                int i = rsize - rlop;
                if (i > extcode - freeEnt) {
                    i = extcode - freeEnt;
                }
                if (i > ((data.outbuf.length - 32) * 8 - outbits) / nBits) {
                    i = ((data.outbuf.length - 32) * 8 - outbits) / nBits;
                }
                if (stcode == 0 && i > checkpoint - data.bytesIn) {
                    i = (int)(checkpoint - data.bytesIn);
                }
                
                rlop += i;
                data.bytesIn += i;
                
                // 查找哈希表
                int ent = (int)(fcode & 0xFFFF);
                
                if (rpos >= rlop) {
                    continue;
                }
                
                int c = data.inbuf[rpos++] & 0xFF;
                long code = (c << 16) | ent;
                
                hp = (((c) << (BITS - 8)) ^ ent) % HSIZE;
                
                if (hp < 0) hp += HSIZE;
                
                if (data.htab[hp] == code) {
                    fcode = data.codetab[hp] & 0xFFFF;
                    continue;
                }
                
                if (data.htab[hp] != -1) {
                    int disp = (HSIZE - hp) - 1;
                    
                    do {
                        hp -= disp;
                        if (hp < 0) hp += HSIZE;
                        
                        if (data.htab[hp] == code) {
                            fcode = data.codetab[hp] & 0xFFFF;
                            break;
                        }
                    } while (data.htab[hp] != -1);
                }
                
                // 输出编码
                output(data.outbuf, outbits, ent, nBits);
                
                // 更新哈希表
                if (stcode != 0) {
                    data.codetab[hp] = (short)freeEnt++;
                    data.htab[hp] = (int)code;
                }
                
                fcode = c;
                
            } while (rlop < rsize);
        }
        
        if (data.bytesIn > 0) {
            output(data.outbuf, outbits, (int)(fcode & 0xFFFF), nBits);
        }
        
        int bytesToWrite = (outbits + 7) >> 3;
        fdout.write(data.outbuf, 0, bytesToWrite);
        data.bytesOut += bytesToWrite;
    }
    
    /* ===================== 输出函数 ===================== */
    private static void output(byte[] buf, int offset, int code, int nBits) {
        int byteOffset = offset >> 3;
        int bitOffset = offset & 0x7;
        long value = ((long)code) << bitOffset;
        
        if (byteOffset < buf.length) {
            buf[byteOffset] |= (byte)(value & 0xFF);
        }
        if (byteOffset + 1 < buf.length) {
            buf[byteOffset + 1] |= (byte)((value >> 8) & 0xFF);
        }
        if (byteOffset + 2 < buf.length) {
            buf[byteOffset + 2] |= (byte)((value >> 16) & 0xFF);
        }
    }
    
    /* ===================== 辅助函数 ===================== */
    private static int maxCode(int nBits) {
        return (1 << nBits) - 1;
    }
    
    /* ===================== 读取配置文件 ===================== */
    private static List<Integer> readConfigFile(String filename) {
        List<Integer> fileIds = new ArrayList<>();
        
        System.out.println("正在读取配置文件: " + filename);
        
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;
            int lineNum = 0;
            
            while ((line = reader.readLine()) != null) {
                lineNum++;
                line = line.trim();
                
                // 跳过空行
                if (line.isEmpty()) {
                    continue;
                }
                
                // 尝试解析数字
                try {
                    int fileId = Integer.parseInt(line);
                    fileIds.add(fileId);
                } catch (NumberFormatException e) {
                    if (lineNum == 1) {
                        // 第一行可能是标题，忽略
                        System.out.println("忽略标题行: " + line);
                    } else {
                        System.err.println("警告: 第" + lineNum + "行不是有效的数字: " + line);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("错误: 无法打开配置文件 " + filename);
            System.err.println("错误信息: " + e.getMessage());
            return fileIds;
        }
        
        System.out.println("成功读取 " + fileIds.size() + " 个文件编号");
        System.out.print("文件编号: ");
        for (int i = 0; i < Math.min(fileIds.size(), 20); i++) {
            System.out.print(fileIds.get(i) + " ");
        }
        if (fileIds.size() > 20) {
            System.out.print("... 以及 " + (fileIds.size() - 20) + " 个更多文件");
        }
        System.out.println();
        
        return fileIds;
    }
    
    /* ===================== 打印使用说明 ===================== */
    private static void printUsage() {
        System.err.println("用法: java Compress <线程数> <配置文件名> [循环次数]");
        System.err.println("示例: java Compress 4 dataset.conf 5");
        System.err.println("\n参数说明:");
        System.err.println("  线程数: 并行处理的线程数量 (1-32)");
        System.err.println("  配置文件名: 包含数据文件列表的文本文件");
        System.err.println("  循环次数: 每个文件的处理轮数 (可选，默认1)");
        System.err.println("\n配置文件格式:");
        System.err.println("  每行一个文件编号，例如:");
        System.err.println("  1");
        System.err.println("  2");
        System.err.println("  3");
        System.err.println("  ...");
        System.err.println("\n数据文件应放在 dataset/ 目录下，命名为 dataset/1, dataset/2 等");
    }
    
    /* ===================== 打印统计信息 ===================== */
    private static void printStatistics(int totalFiles, int roundsPerFile, long totalTime) {
        System.out.println("\n========================================");
        System.out.println("所有任务完成!");
        System.out.println("处理文件总数: " + totalFiles);
        System.out.println("每文件处理轮数: " + roundsPerFile);
        System.out.println("总操作次数: " + (totalFiles * roundsPerFile));
        System.out.println("总时间: " + totalTime + " 毫秒 (" + (totalTime / 1000.0) + " 秒)");
        System.out.printf("平均每文件时间: %.2f 毫秒%n", (float)totalTime / totalFiles);
        System.out.printf("每秒操作数: %.2f%n", 
                (totalFiles * roundsPerFile * 1000.0) / totalTime);
        System.out.println("========================================");
    }
    
    /* ===================== 添加关闭钩子 ===================== */
    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            shutdown = true;
            executor.shutdownNow();
            System.out.println("\n程序被中断，正在清理资源...");
        }));
    }
}
