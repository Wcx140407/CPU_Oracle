/* (N)compress.c - File compression ala IEEE Computer, Mar 1992.
 * 并行版本：支持自定义输入数据集和多线程处理
 *
 * 使用方法：
 *   ./compress <线程数> <配置文件名> [循环次数]
 *   
 * 示例：
 *   ./compress 4 dataset.conf 5
 *
 * 配置文件格式：
 *   第一行：标题（忽略）
 *   后续行：每个数据文件的编号
 */

#ifdef _MSC_VER
#	define	WINDOWS
#endif

#ifdef __MINGW32__
#	define	MINGW
#endif

#include	<stdint.h>
#include	<stdio.h>
#include	<stdlib.h>
#include	<string.h>
#include	<fcntl.h>
#include	<ctype.h>
#include	<signal.h>
#include	<sys/types.h>
#include	<sys/stat.h>
#include	<errno.h>
#include 	<sys/time.h>
#include    <pthread.h>
#include    <queue>
#include    <vector>
#include    <string>

#if !defined(DOS) && !defined(WINDOWS)
#	include	<dirent.h>
#	define RECURSIVE 1
#	include	<unistd.h>
#endif

#ifdef UTIME_H
#	include	<utime.h>
#else
	struct utimbuf {
		time_t actime;
		time_t modtime;
	};
#endif

#ifdef	__STDC__
#	define	ARGS(a)				a
#else
#	define	ARGS(a)				()
#endif

#ifndef SIG_TYPE
#	define	SIG_TYPE	void (*)()
#endif

#if defined(AMIGA) || defined(DOS) || defined(MINGW) || defined(WINDOWS)
#	define	chmod(pathname, mode) 0
#	define	chown(pathname, owner, group) 0
#	define	utime(pathname, times) 0
#endif

#if defined(WINDOWS)
#	define isatty(fd) 0
#	define open _open
#	define close _close
#	define read _read
#	define strdup _strdup
#	define unlink _unlink
#	define write _write
#endif

#ifndef	LSTAT
#	define	lstat	stat
#endif

#if defined(DOS) || defined(WINDOWS)
#	define	F_OK	0
static inline int access(const char *pathname, int mode)
{
	struct stat st;
	return lstat(pathname, &st) == 0;
}
#endif

static char ident[] = "@(#)(N)compress 5.0 (Parallel Version)";
#define version_id (ident+4)

#undef	min
#define	min(a,b)	((a>b) ? b : a)

#ifndef	IBUFSIZ
#	define	IBUFSIZ	BUFSIZ
#endif
#ifndef	OBUFSIZ
#	define	OBUFSIZ	BUFSIZ
#endif

#define	MAGIC_1		(char_type)'\037'
#define	MAGIC_2		(char_type)'\235'
#define BIT_MASK	0x1f
#define BLOCK_MODE	0x80
#define FIRST	257
#define	CLEAR	256
#define INIT_BITS 9

#ifndef SACREDMEM
#	define SACREDMEM	0
#endif

#ifndef USERMEM
#	define USERMEM 	450000
#endif

#ifndef	BYTEORDER
#	define	BYTEORDER	0000
#endif

#ifdef	DOS
#	define	BITS   16
#	if BITS == 16
#		define	MAXSEG_64K
#	endif
#	undef	BYTEORDER
#	define	BYTEORDER 	4321
#endif

#ifndef	O_BINARY
#	define	O_BINARY	0
#endif

#ifdef M_XENIX
#	if BITS == 16
# 		define MAXSEG_64K
#	else
#	if BITS > 13
#		define BITS	13
#	endif
#	endif
#endif

#ifndef BITS
#	if USERMEM >= (800000+SACREDMEM)
#		define FAST
#	else
#	if USERMEM >= (433484+SACREDMEM)
#		define BITS	16
#	else
#	if USERMEM >= (229600+SACREDMEM)
#		define BITS	15
#	else
#	if USERMEM >= (127536+SACREDMEM)
#		define BITS	14
#   else
#	if USERMEM >= (73464+SACREDMEM)
#		define BITS	13
#	else
#		define BITS	12
#	endif
#	endif
#   endif
#	endif
#	endif
#endif

#ifdef FAST
#	define	HBITS		17
#	define	HSIZE	   (1<<HBITS)
#	define	HMASK	   (HSIZE-1)
#	define	HPRIME		 9941
#	define	BITS		   16
#	undef	MAXSEG_64K
#else
#	if BITS == 16
#		define HSIZE	69001
#	endif
#	if BITS == 15
#		define HSIZE	35023
#	endif
#	if BITS == 14
#		define HSIZE	18013
#	endif
#	if BITS == 13
#		define HSIZE	9001
#	endif
#	if BITS <= 12
#		define HSIZE	5003
#	endif
#endif

#define CHECK_GAP 10000

typedef long int			code_int;

#ifdef SIGNED_COMPARE_SLOW
	typedef unsigned long int	count_int;
	typedef unsigned short int	count_short;
	typedef unsigned long int	cmp_code_int;
#else
	typedef long int	 		count_int;
	typedef long int			cmp_code_int;
#endif

typedef	unsigned char	char_type;

#define ARGVAL() (*++(*argv) || (--argc && *++argv))

#define MAXCODE(n)	(1L << (n))

union	bytes
{
	long	word;
	struct
	{
#if BYTEORDER == 4321
		char_type	b1;
		char_type	b2;
		char_type	b3;
		char_type	b4;
#else
#if BYTEORDER == 1234
		char_type	b4;
		char_type	b3;
		char_type	b2;
		char_type	b1;
#else
#	undef	BYTEORDER
		int				dummy;
#endif
#endif
	} bytes;
} ;

#ifdef BYTEORDER
#define	output(b,o,c,n)	{	char_type	*p = &(b)[(o)>>3];					\
							union bytes i;									\
							i.word = ((long)(c))<<((o)&0x7);				\
							p[0] |= i.bytes.b1;								\
							p[1] |= i.bytes.b2;								\
							p[2] |= i.bytes.b3;								\
							(o) += (n);										\
						}
#else
#define	output(b,o,c,n)	{	char_type	*p = &(b)[(o)>>3];					\
							long		 i = ((long)(c))<<((o)&0x7);		\
							p[0] |= (char_type)(i);							\
							p[1] |= (char_type)(i>>8);						\
							p[2] |= (char_type)(i>>16);						\
							(o) += (n);										\
						}
#endif

#define	input(b,o,c,n,m){	char_type 		*p = &(b)[(o)>>3];				\
							(c) = ((((long)(p[0]))|((long)(p[1])<<8)|		\
									 ((long)(p[2])<<16))>>((o)&0x7))&(m);	\
							(o) += (n);										\
						}

/* ===================== 并行处理相关结构体和变量 ===================== */
typedef struct {
    int file_id;           // 数据文件编号
    int rounds;            // 每个文件的压缩/解压轮数
    long long start_time;  // 开始时间
    long long end_time;    // 结束时间
} Task;

typedef struct {
    pthread_t thread_id;
    int thread_num;
    std::queue<Task>* task_queue;
    pthread_mutex_t* queue_mutex;
    pthread_cond_t* queue_cond;
    int* all_tasks_done;
} ThreadData;

/* ===================== 线程局部数据结构 ===================== */
typedef struct {
    // 压缩/解压状态
    int do_decomp;          // 解压模式标志
    int force;              // 强制覆盖标志
    int quiet;              // 静默模式标志
    int maxbits;            // 最大位数
    int exit_code;          // 退出代码
    
    // 文件信息
    char *ifname;           // 输入文件名
    char *ofname;           // 输出文件名
    struct stat infstat;    // 输入文件状态
    int remove_ofname;      // 删除输出文件标志
    
    // 统计信息
    long bytes_in;          // 输入字节数
    long bytes_out;         // 输出字节数
    
    // 缓冲区
    char_type inbuf[IBUFSIZ+64];
    char_type outbuf[OBUFSIZ+2048];
    
    // 哈希表和编码表
#ifdef MAXSEG_64K
    count_int *htab[9];
    unsigned short *codetab[5];
#else
    count_int htab[HSIZE];
    unsigned short codetab[HSIZE];
#endif
    
    // 文件ID（用于日志）
    int file_id;
} ThreadLocalData;

/* ===================== 全局变量 ===================== */
// 注意：这些全局变量只在单线程模式下使用
char *progname;
int silent = 0;
int quiet = 1;
int do_decomp = 0;
int force = 0;
int nomagic = 0;
int maxbits = BITS;
int zcat_flg = 0;
int recursive = 0;
int exit_code = -1;

/* ===================== 并行处理全局变量 ===================== */
pthread_mutex_t task_mutex = PTHREAD_MUTEX_INITIALIZER;
pthread_cond_t task_cond = PTHREAD_COND_INITIALIZER;
std::queue<Task> task_queue;
int all_tasks_done = 0;
int active_threads = 0;
int num_threads = 4;
int rounds_per_file = 1;
int total_files = 0;

/* ===================== 函数声明 ===================== */
void  	Usage			ARGS((int));
void  	about			ARGS((void));
void  	prratio			ARGS((FILE *,long,long));
void  	read_error		ARGS((ThreadLocalData *));
void  	write_error		ARGS((ThreadLocalData *));
void 	abort_compress	ARGS((ThreadLocalData *));
void  	compress		ARGS((int,int,ThreadLocalData *));
void  	decompress		ARGS((int,int,ThreadLocalData *));
void	clear_htab		ARGS((ThreadLocalData *));
void	clear_tab_prefixof ARGS((ThreadLocalData *));
long long getCurrentTime ARGS((void));
char* Int2String		ARGS((int,char *));
void* worker_thread		ARGS((void*));
void process_file_task	ARGS((Task));
void comprexx_parallel	ARGS((const char *, ThreadLocalData *));
void init_thread_local_data ARGS((ThreadLocalData *));
void free_thread_local_data ARGS((ThreadLocalData *));

/* ===================== 辅助函数 ===================== */
long long getCurrentTime()
{
    long long timestamp;
    struct timeval t;
    gettimeofday(&t, NULL);
    timestamp = t.tv_sec * 1000 + t.tv_usec / 1000;
    return timestamp;
}

char* Int2String(int num, char *str)
{
    sprintf(str, "%d", num);
    return str;
}

/* ===================== 线程局部数据管理 ===================== */
void init_thread_local_data(ThreadLocalData *data)
{
    memset(data, 0, sizeof(ThreadLocalData));
    
    // 初始化默认值
    data->force = 1;           // 并行模式下总是强制覆盖
    data->quiet = 1;           // 并行模式下静默（减少输出冲突）
    data->maxbits = BITS;
    data->exit_code = -1;
    data->remove_ofname = 0;
    data->bytes_in = 0;
    data->bytes_out = 0;
    
    // 初始化缓冲区
    memset(data->inbuf, 0, sizeof(data->inbuf));
    memset(data->outbuf, 0, sizeof(data->outbuf));
    
    // 初始化哈希表和编码表
    clear_htab(data);
    clear_tab_prefixof(data);
}

void free_thread_local_data(ThreadLocalData *data)
{
    if (data->ifname) {
        free(data->ifname);
        data->ifname = NULL;
    }
    if (data->ofname) {
        free(data->ofname);
        data->ofname = NULL;
    }
}

/* ===================== 压缩核心函数（线程安全版本） ===================== */
void compress(int fdin, int fdout, ThreadLocalData *data)
{
    long hp;
    int rpos;
    long fc;
    int outbits;
    int rlop;
    int rsize;
    int stcode;
    code_int free_ent;
    int boff;
    int n_bits;
    int ratio;
    long checkpoint;
    code_int extcode;
    union
    {
        long			code;
        struct
        {
            char_type		c;
            unsigned short	ent;
        } e;
    } fcode;

    ratio = 0;
    checkpoint = CHECK_GAP;
    extcode = MAXCODE(n_bits = INIT_BITS)+1;
    stcode = 1;
    free_ent = FIRST;

    memset(data->outbuf, 0, sizeof(data->outbuf));
    data->bytes_out = 0; 
    data->bytes_in = 0;
    data->outbuf[0] = MAGIC_1;
    data->outbuf[1] = MAGIC_2;
    data->outbuf[2] = (char)(data->maxbits | BLOCK_MODE);
    boff = outbits = (3<<3);
    fcode.code = 0;

    clear_htab(data);

    while ((rsize = read(fdin, data->inbuf, IBUFSIZ)) > 0)
    {
        if (data->bytes_in == 0)
        {
            fcode.e.ent = data->inbuf[0];
            rpos = 1;
        }
        else
            rpos = 0;

        rlop = 0;

        do
        {
            if (free_ent >= extcode && fcode.e.ent < FIRST)
            {
                if (n_bits < data->maxbits)
                {
                    boff = outbits = (outbits-1)+((n_bits<<3)-
                              	   ((outbits-boff-1+(n_bits<<3))%(n_bits<<3)));
                    if (++n_bits < data->maxbits)
                        extcode = MAXCODE(n_bits)+1;
                    else
                        extcode = MAXCODE(n_bits);
                }
                else
                {
                    extcode = MAXCODE(16)+OBUFSIZ;
                    stcode = 0;
                }
            }

            if (!stcode && data->bytes_in >= checkpoint && fcode.e.ent < FIRST)
            {
                long int rat;

                checkpoint = data->bytes_in + CHECK_GAP;

                if (data->bytes_in > 0x007fffff)
                {
                    rat = (data->bytes_out+(outbits>>3)) >> 8;
                    if (rat == 0)
                        rat = 0x7fffffff;
                    else
                        rat = data->bytes_in / rat;
                }
                else
                    rat = (data->bytes_in << 8) / (data->bytes_out+(outbits>>3));
                if (rat >= ratio)
                    ratio = (int)rat;
                else
                {
                    ratio = 0;
                    clear_htab(data);
                    output(data->outbuf,outbits,CLEAR,n_bits);
                    boff = outbits = (outbits-1)+((n_bits<<3)-
                              	   ((outbits-boff-1+(n_bits<<3))%(n_bits<<3)));
                    extcode = MAXCODE(n_bits = INIT_BITS)+1;
                    free_ent = FIRST;
                    stcode = 1;
                }
            }

            if (outbits >= (OBUFSIZ<<3))
            {
                if (write(fdout, data->outbuf, OBUFSIZ) != OBUFSIZ)
                    write_error(data);

                outbits -= (OBUFSIZ<<3);
                boff = -(((OBUFSIZ<<3)-boff)%(n_bits<<3));
                data->bytes_out += OBUFSIZ;

                memcpy(data->outbuf, data->outbuf+OBUFSIZ, (outbits>>3)+1);
                memset(data->outbuf+(outbits>>3)+1, '\0', OBUFSIZ);
            }

            {
                int i;

                i = rsize-rlop;

                if ((code_int)i > extcode-free_ent)	i = (int)(extcode-free_ent);
                if (i > ((sizeof(data->outbuf) - 32)*8 - outbits)/n_bits)
                    i = ((sizeof(data->outbuf) - 32)*8 - outbits)/n_bits;
                    
                if (!stcode && (long)i > checkpoint-data->bytes_in)
                    i = (int)(checkpoint-data->bytes_in);

                rlop += i;
                data->bytes_in += i;
            }

            goto next;
hfound:		fcode.e.ent = data->codetab[hp];
next:  		if (rpos >= rlop)
	   			goto endlop;
next2: 		fcode.e.c = data->inbuf[rpos++];
#ifndef FAST
            {
                code_int i;
                fc = fcode.code;
                hp = (((long)(fcode.e.c)) << (BITS-8)) ^ (long)(fcode.e.ent);

                if ((i = data->htab[hp]) == fc)
                    goto hfound;

                if (i != -1)
                {
                    long disp;

                    disp = (HSIZE - hp)-1;

                    do
                    {
                        if ((hp -= disp) < 0)	hp += HSIZE;

                        if ((i = data->htab[hp]) == fc)
                            goto hfound;
                    }
                    while (i != -1);
                }
            }
#else
            {
                long i;
                long p;
                fc = fcode.code;
                hp = ((((long)(fcode.e.c)) << (HBITS-8)) ^ (long)(fcode.e.ent));

                if ((i = data->htab[hp]) == fc)	goto hfound;
                if (i == -1)				goto out;

                p = primetab[fcode.e.c];
lookup:			hp = (hp+p)&HMASK;
                if ((i = data->htab[hp]) == fc)	goto hfound;
                if (i == -1)				goto out;
                hp = (hp+p)&HMASK;
                if ((i = data->htab[hp]) == fc)	goto hfound;
                if (i == -1)				goto out;
                hp = (hp+p)&HMASK;
                if ((i = data->htab[hp]) == fc)	goto hfound;
                if (i == -1)				goto out;
                goto lookup;
            }
out:		;
#endif
            output(data->outbuf,outbits,fcode.e.ent,n_bits);

            {
                long fc = fcode.code;
                fcode.e.ent = fcode.e.c;

                if (stcode)
                {
                    data->codetab[hp] = (unsigned short)free_ent++;
                    data->htab[hp] = fc;
                }
            } 

            goto next;

endlop:		if (fcode.e.ent >= FIRST && rpos < rsize)
                goto next2;

            if (rpos > rlop)
            {
                data->bytes_in += rpos-rlop;
                rlop = rpos;
            }
        }
        while (rlop < rsize);
    }

    if (rsize < 0)
        read_error(data);

    if (data->bytes_in > 0)
        output(data->outbuf,outbits,fcode.e.ent,n_bits);

    if (write(fdout, data->outbuf, (outbits+7)>>3) != (outbits+7)>>3)
        write_error(data);

    data->bytes_out += (outbits+7)>>3;
}

/* ===================== 解压核心函数（线程安全版本） ===================== */
void decompress(int fdin, int fdout, ThreadLocalData *data)
{
    char_type *stackp;
    code_int code;
    int finchar;
    code_int oldcode;
    code_int incode;
    int inbits;
    int posbits;
    int outpos;
    int insize;
    int bitmask;
    code_int free_ent;
    code_int maxcode;
    code_int maxmaxcode;
    int n_bits;
    int rsize;
    int block_mode;

    data->bytes_in = 0;
    data->bytes_out = 0;
    insize = 0;

    while (insize < 3 && (rsize = read(fdin, data->inbuf+insize, IBUFSIZ)) > 0)
        insize += rsize;

    if (insize < 3 || data->inbuf[0] != MAGIC_1 || data->inbuf[1] != MAGIC_2)
    {
        if (rsize < 0)
            read_error(data);

        if (insize > 0)
        {
            fprintf(stderr, "Thread %d: not in compressed format\n", data->file_id);
            data->exit_code = 1;
        }

        return;
    }

    data->maxbits = data->inbuf[2] & BIT_MASK;
    block_mode = data->inbuf[2] & BLOCK_MODE;

    if (data->maxbits > BITS)
    {
        fprintf(stderr, "Thread %d: compressed with %d bits, can only handle %d bits\n",
                data->file_id, data->maxbits, BITS);
        data->exit_code = 4;
        return;
    }

    maxmaxcode = MAXCODE(data->maxbits);

    data->bytes_in = insize;
    maxcode = MAXCODE(n_bits = INIT_BITS)-1;
    bitmask = (1<<n_bits)-1;
    oldcode = -1;
    finchar = 0;
    outpos = 0;
    posbits = 3<<3;

    free_ent = ((block_mode) ? FIRST : 256);

    clear_tab_prefixof(data);

    for (code = 255 ; code >= 0 ; --code)
        data->htab[code] = (char_type)code;

    do
    {
resetbuf:	;
        {
            int i;
            int e;
            int o;

            o = posbits >> 3;
            e = o <= insize ? insize - o : 0;

            for (i = 0 ; i < e ; ++i)
                data->inbuf[i] = data->inbuf[i+o];

            insize = e;
            posbits = 0;
        }

        if (insize < sizeof(data->inbuf)-IBUFSIZ)
        {
            if ((rsize = read(fdin, data->inbuf+insize, IBUFSIZ)) < 0)
                read_error(data);

            insize += rsize;
        }

        inbits = ((rsize > 0) ? (insize - insize%n_bits)<<3 : 
                                (insize<<3)-(n_bits-1));

        while (inbits > posbits)
        {
            if (free_ent > maxcode)
            {
                posbits = ((posbits-1) + ((n_bits<<3) -
                                    (posbits-1+(n_bits<<3))%(n_bits<<3)));

                ++n_bits;
                if (n_bits == data->maxbits)
                    maxcode = maxmaxcode;
                else
                    maxcode = MAXCODE(n_bits)-1;

                bitmask = (1<<n_bits)-1;
                goto resetbuf;
            }

            input(data->inbuf,posbits,code,n_bits,bitmask);

            if (oldcode == -1)
            {
                if (code >= 256) {
                    fprintf(stderr, "Thread %d: corrupt input\n", data->file_id);
                    abort_compress(data);
                }
                data->outbuf[outpos++] = (char_type)(finchar = (int)(oldcode = code));
                continue;
            }

            if (code == CLEAR && block_mode)
            {
                clear_tab_prefixof(data);
                free_ent = FIRST - 1;
                posbits = ((posbits-1) + ((n_bits<<3) -
                            (posbits-1+(n_bits<<3))%(n_bits<<3)));
                maxcode = MAXCODE(n_bits = INIT_BITS)-1;
                bitmask = (1<<n_bits)-1;
                goto resetbuf;
            }

            incode = code;
            stackp = (char_type *)&data->htab[HSIZE-1];

            if (code >= free_ent)
            {
                if (code > free_ent)
                {
                    char_type *p;

                    posbits -= n_bits;
                    p = &data->inbuf[posbits>>3];

                    fprintf(stderr, "Thread %d: corrupt input\n", data->file_id);
                    abort_compress(data);
                }

                *--stackp = (char_type)finchar;
                code = oldcode;
            }

            while ((cmp_code_int)code >= (cmp_code_int)256)
            {
                *--stackp = data->htab[code];
                code = data->codetab[code];
            }

            *--stackp = (char_type)(finchar = data->htab[code]);

            {
                int i;

                if (outpos+(i = ((char_type *)&data->htab[HSIZE-1]-stackp)) >= OBUFSIZ)
                {
                    do
                    {
                        if (i > OBUFSIZ-outpos) i = OBUFSIZ-outpos;

                        if (i > 0)
                        {
                            memcpy(data->outbuf+outpos, stackp, i);
                            outpos += i;
                        }

                        if (outpos >= OBUFSIZ)
                        {
                            if (write(fdout, data->outbuf, outpos) != outpos)
                                write_error(data);

                            outpos = 0;
                        }
                        stackp+= i;
                    }
                    while ((i = ((char_type *)&data->htab[HSIZE-1]-stackp)) > 0);
                }
                else
                {
                    memcpy(data->outbuf+outpos, stackp, i);
                    outpos += i;
                }
            }

            if ((code = free_ent) < maxmaxcode)
            {
                data->codetab[code] = (unsigned short)oldcode;
                data->htab[code] = (char_type)finchar;
                free_ent = code+1;
            } 

            oldcode = incode;
        }

        data->bytes_in += rsize;
    }
    while (rsize > 0);

    if (outpos > 0 && write(fdout, data->outbuf, outpos) != outpos)
        write_error(data);
}

/* ===================== 哈希表管理函数 ===================== */
void clear_htab(ThreadLocalData *data)
{
    memset(data->htab, -1, sizeof(data->htab));
}

void clear_tab_prefixof(ThreadLocalData *data)
{
    memset(data->codetab, 0, 256 * sizeof(unsigned short));
}

/* ===================== 错误处理函数 ===================== */
void read_error(ThreadLocalData *data)
{
    fprintf(stderr, "Thread %d: read error on %s\n", 
            data->file_id, data->ifname ? data->ifname : "stdin");
    abort_compress(data);
}

void write_error(ThreadLocalData *data)
{
    fprintf(stderr, "Thread %d: write error on %s\n", 
            data->file_id, data->ofname ? data->ofname : "stdout");
    abort_compress(data);
}

void abort_compress(ThreadLocalData *data)
{
    if (data->remove_ofname && data->ofname)
        unlink(data->ofname);
    
    data->exit_code = 1;
}

/* ===================== 文件处理函数 ===================== */
void comprexx_parallel(const char *fileptr, ThreadLocalData *data)
{
    int fdin = -1;
    int fdout = -1;
    int has_z_suffix;
    char *tempname;
    unsigned long namesize = strlen(fileptr);

    tempname = (char *)malloc(namesize + 3);
    if (tempname == NULL)
    {
        fprintf(stderr, "Thread %d: malloc error\n", data->file_id);
        return;
    }

    strcpy(tempname, fileptr);
    has_z_suffix = (namesize >= 2 && strcmp(&tempname[namesize - 2], ".Z") == 0);

    if (lstat(tempname, &data->infstat) == -1)
    {
        fprintf(stderr, "Thread %d: Cannot stat %s: %s\n", 
                data->file_id, tempname, strerror(errno));
        free(tempname);
        return;
    }

    if ((data->infstat.st_mode & S_IFMT) != S_IFREG)
    {
        fprintf(stderr, "Thread %d: %s is not a regular file\n", 
                data->file_id, tempname);
        free(tempname);
        return;
    }

    if (data->do_decomp)
    {
        if (!has_z_suffix)
        {
            fprintf(stderr, "Thread %d: %s has no .Z suffix for decompression\n", 
                    data->file_id, tempname);
            free(tempname);
            return;
        }

        if (data->ofname) free(data->ofname);
        data->ofname = strdup(tempname);
        if (data->ofname == NULL)
        {
            fprintf(stderr, "Thread %d: strdup error\n", data->file_id);
            free(tempname);
            return;
        }

        if (has_z_suffix)
            data->ofname[namesize - 2] = '\0';
    }
    else
    {
        if (has_z_suffix)
        {
            fprintf(stderr, "Thread %d: %s already has .Z suffix\n", 
                    data->file_id, tempname);
            free(tempname);
            return;
        }

        if (data->ofname) free(data->ofname);
        data->ofname = (char *)malloc(namesize + 3);
        if (data->ofname == NULL)
        {
            fprintf(stderr, "Thread %d: malloc error\n", data->file_id);
            free(tempname);
            return;
        }
        memcpy(data->ofname, tempname, namesize);
        strcpy(&data->ofname[namesize], ".Z");
    }

    if (data->ifname) free(data->ifname);
    data->ifname = strdup(tempname);
    if (data->ifname == NULL)
    {
        fprintf(stderr, "Thread %d: strdup error\n", data->file_id);
        free(tempname);
        return;
    }

    if ((fdin = open(tempname, O_RDONLY|O_BINARY)) == -1)
    {
        fprintf(stderr, "Thread %d: Cannot open %s: %s\n", 
                data->file_id, tempname, strerror(errno));
        free(tempname);
        return;
    }

    if (access(data->ofname, F_OK) == 0 && !data->force)
    {
        fprintf(stderr, "Thread %d: %s already exists (use -f to force)\n", 
                data->file_id, data->ofname);
        close(fdin);
        free(tempname);
        return;
    }

    if (access(data->ofname, F_OK) == 0 && data->force)
    {
        if (unlink(data->ofname))
        {
            fprintf(stderr, "Thread %d: Cannot remove %s: %s\n", 
                    data->file_id, data->ofname, strerror(errno));
            close(fdin);
            free(tempname);
            return;
        }
    }

    if ((fdout = open(data->ofname, O_WRONLY|O_CREAT|O_EXCL|O_BINARY, 0600)) == -1)
    {
        fprintf(stderr, "Thread %d: Cannot create %s: %s\n", 
                data->file_id, data->ofname, strerror(errno));
        close(fdin);
        free(tempname);
        return;
    }

    data->remove_ofname = 1;

    if (data->do_decomp == 0)
        compress(fdin, fdout, data);
    else
        decompress(fdin, fdout, data);

    close(fdin);
    
    if (fdout != 1 && close(fdout))
    {
        fprintf(stderr, "Thread %d: Error closing output\n", data->file_id);
    }

    if (!data->quiet)
    {
        if (data->do_decomp == 0)
        {
            fprintf(stderr, "Thread %d: Compressed %s -> %s (%.2f%%)\n", 
                    data->file_id, tempname, data->ofname,
                    (data->bytes_in > 0) ? 
                    (100.0 * (data->bytes_in - data->bytes_out) / data->bytes_in) : 0.0);
        }
        else
        {
            fprintf(stderr, "Thread %d: Decompressed %s -> %s\n", 
                    data->file_id, tempname, data->ofname);
        }
    }

    free(tempname);
}

/* ===================== 工作线程函数 ===================== */
void* worker_thread(void* arg)
{
    ThreadData* data = (ThreadData*)arg;
    
    ThreadLocalData local_data;
    init_thread_local_data(&local_data);
    
    printf("Thread %d started\n", data->thread_num);
    
    while (1) {
        Task task;
        int got_task = 0;
        
        pthread_mutex_lock(data->queue_mutex);
        
        if (!data->task_queue->empty()) {
            task = data->task_queue->front();
            data->task_queue->pop();
            active_threads++;
            got_task = 1;
        } else if (*data->all_tasks_done) {
            pthread_mutex_unlock(data->queue_mutex);
            break;
        } else {
            pthread_cond_wait(data->queue_cond, data->queue_mutex);
            pthread_mutex_unlock(data->queue_mutex);
            continue;
        }
        
        pthread_mutex_unlock(data->queue_mutex);
        
        if (got_task) {
            task.start_time = getCurrentTime();
            local_data.file_id = task.file_id;
            
            printf("Thread %d starting file %d, time: %lld\n", 
                   data->thread_num, task.file_id, task.start_time);
            
            char name[100];
            strcpy(name, "dataset/");
            char num[20];
            Int2String(task.file_id, num);
            strcat(name, num);
            
            for (int n = 0; n < task.rounds; n++) {
                local_data.do_decomp = 0;
                comprexx_parallel(name, &local_data);
                
                local_data.do_decomp = 1;
                comprexx_parallel(name, &local_data);
            }
            
            task.end_time = getCurrentTime();
            printf("Thread %d finished file %d, time: %lld, duration: %lldms\n", 
                   data->thread_num, task.file_id, task.end_time, 
                   task.end_time - task.start_time);
            
            pthread_mutex_lock(data->queue_mutex);
            active_threads--;
            pthread_cond_broadcast(data->queue_cond);
            pthread_mutex_unlock(data->queue_mutex);
        }
    }
    
    free_thread_local_data(&local_data);
    printf("Thread %d exited\n", data->thread_num);
    return NULL;
}

/* ===================== 主函数 ===================== */
/* ===================== 主函数 ===================== */
int main(int argc, char *argv[])
{
    progname = argv[0];
    
    if (argc < 3) {
        fprintf(stderr, "Usage: %s <num_threads> <config_file> [rounds_per_file]\n", argv[0]);
        fprintf(stderr, "Example: %s 4 dataset.conf 5\n", argv[0]);
        fprintf(stderr, "\nConfig file format:\n");
        fprintf(stderr, "  First line: header (optional, ignored)\n");
        fprintf(stderr, "  Following lines: file numbers (e.g., 1, 2, 3)\n");
        fprintf(stderr, "\nFiles should be in dataset/ directory as dataset/1, dataset/2, etc.\n");
        return 1;
    }
    
    num_threads = atoi(argv[1]);
    if (num_threads <= 0) {
        num_threads = 4;
        printf("Using default thread count: %d\n", num_threads);
    }
    
    if (argc >= 4) {
        rounds_per_file = atoi(argv[3]);
        if (rounds_per_file <= 0) rounds_per_file = 1;
    }
    
    // 读取配置文件
    printf("Reading config file: %s\n", argv[2]);
    
    FILE *fp = fopen(argv[2], "r");
    if (fp == NULL) {
        fprintf(stderr, "Error: Cannot open config file: %s\n", argv[2]);
        fprintf(stderr, "Error details: %s\n", strerror(errno));
        return 1;
    }
    
    int file_ids[1000];
    int file_count = 0;
    char line[256];
    int line_num = 0;
    
    // 逐行读取配置文件
    while (fgets(line, sizeof(line), fp) != NULL) {
        line_num++;
        
        // 去除换行符
        line[strcspn(line, "\n\r")] = 0;
        
        // 跳过空行和注释行（以#开头的行）
        if (line[0] == '\0' || line[0] == '#') {
            printf("Line %d: Skipping empty/comment line\n", line_num);
            continue;
        }
        
        // 尝试解析数字
        int file_id;
        if (sscanf(line, "%d", &file_id) == 1) {
            if (file_count < 1000) {
                file_ids[file_count] = file_id;
                file_count++;
                printf("Line %d: Added file ID %d\n", line_num, file_id);
            } else {
                fprintf(stderr, "Warning: Too many files, max is 1000\n");
                break;
            }
        } else {
            // 如果第一行不是数字，可能是标题行
            if (line_num == 1) {
                printf("Line 1: Header line (ignored): %s\n", line);
            } else {
                fprintf(stderr, "Warning: Line %d is not a valid number: %s\n", line_num, line);
            }
        }
    }
    
    fclose(fp);
    
    if (file_count == 0) {
        fprintf(stderr, "\nError: No valid file IDs found in config file!\n");
        fprintf(stderr, "Config file should contain one file ID per line.\n");
        fprintf(stderr, "Example config file content:\n");
        fprintf(stderr, "1\n");
        fprintf(stderr, "2\n");
        fprintf(stderr, "3\n");
        fprintf(stderr, "\nOr with a header line:\n");
        fprintf(stderr, "Dataset Configuration\n");
        fprintf(stderr, "1\n");
        fprintf(stderr, "2\n");
        fprintf(stderr, "3\n");
        return 1;
    }
    
    printf("\nSuccessfully read %d file IDs from config file\n", file_count);
    printf("File IDs: ");
    for (int i = 0; i < file_count; i++) {
        printf("%d ", file_ids[i]);
        if (i > 0 && i % 10 == 0) printf("\n           ");
    }
    printf("\n");
    
    // 检查数据集目录是否存在
    struct stat st;
    if (stat("dataset", &st) != 0 || !S_ISDIR(st.st_mode)) {
        printf("\nWarning: dataset/ directory not found or is not a directory.\n");
        printf("Creating dataset/ directory...\n");
        
        if (mkdir("dataset", 0755) != 0) {
            fprintf(stderr, "Error: Cannot create dataset/ directory: %s\n", strerror(errno));
            fprintf(stderr, "Please create it manually and add your data files.\n");
            return 1;
        }
        
        printf("Created dataset/ directory.\n");
        printf("You need to add your data files (e.g., dataset/1, dataset/2, etc.)\n");
    }
    
    // 检查前几个文件是否存在（用于验证）
    printf("\nChecking if data files exist...\n");
    int missing_files = 0;
    int check_count = (file_count < 5) ? file_count : 5;
    
    for (int i = 0; i < check_count; i++) {
        char filename[100];
        snprintf(filename, sizeof(filename), "dataset/%d", file_ids[i]);
        
        if (access(filename, F_OK) == 0) {
            printf("  ✓ File exists: %s\n", filename);
        } else {
            printf("  ✗ File missing: %s\n", filename);
            missing_files++;
        }
    }
    
    if (missing_files > 0 && check_count < file_count) {
        printf("  ... and %d more files to check\n", file_count - check_count);
    }
    
    if (missing_files == check_count) {
        printf("\nWarning: No data files found in dataset/ directory!\n");
        printf("You need to create test files. Example commands:\n");
        printf("  for i in {1..%d}; do\n", file_count);
        printf("    dd if=/dev/urandom of=dataset/\\$i bs=1M count=1\n");
        printf("  done\n");
        printf("\nContinue anyway? (y/n): ");
        
        char response[10];
        if (fgets(response, sizeof(response), stdin) != NULL) {
            if (response[0] != 'y' && response[0] != 'Y') {
                printf("Aborted by user.\n");
                return 1;
            }
        }
    }
    
    printf("\n========================================\n");
    printf("Parallel Compression Program\n");
    printf("Threads: %d\n", num_threads);
    printf("Config file: %s\n", argv[2]);
    printf("Files to process: %d\n", file_count);
    printf("Rounds per file: %d\n", rounds_per_file);
    printf("========================================\n\n");
    
    // 创建任务队列
    for (int i = 0; i < file_count; i++) {
        Task task;
        task.file_id = file_ids[i];
        task.rounds = rounds_per_file;
        task_queue.push(task);
    }
    
    total_files = file_count;
    
    // 创建并启动工作线程
    ThreadData* thread_data = new ThreadData[num_threads];
    pthread_t* threads = new pthread_t[num_threads];
    
    printf("Creating %d worker threads...\n", num_threads);
    
    for (int i = 0; i < num_threads; i++) {
        thread_data[i].thread_num = i + 1;
        thread_data[i].task_queue = &task_queue;
        thread_data[i].queue_mutex = &task_mutex;
        thread_data[i].queue_cond = &task_cond;
        thread_data[i].all_tasks_done = &all_tasks_done;
        
        if (pthread_create(&threads[i], NULL, worker_thread, &thread_data[i]) != 0) {
            fprintf(stderr, "Error: Cannot create thread %d: %s\n", i, strerror(errno));
            return 1;
        }
    }
    
    long long start_time = getCurrentTime();
    printf("\nProcessing started at: %lld\n", start_time);
    printf("Press Ctrl+C to stop\n\n");
    
    // 广播信号，唤醒等待的线程
    pthread_mutex_lock(&task_mutex);
    pthread_cond_broadcast(&task_cond);
    pthread_mutex_unlock(&task_mutex);
    
    // 监视进度
    int processed_files = 0;
    int last_reported = -1;
    
    while (processed_files < total_files) {
        usleep(500000); // 休眠500ms
        
        pthread_mutex_lock(&task_mutex);
        int remaining = task_queue.size() + active_threads;
        processed_files = total_files - remaining;
        pthread_mutex_unlock(&task_mutex);
        
        // 每处理10%报告一次进度
        int progress_percent = (processed_files * 100) / total_files;
        if (progress_percent != last_reported && (progress_percent % 10 == 0 || processed_files == total_files)) {
            printf("Progress: %d/%d files processed (%d%%)\n", 
                   processed_files, total_files, progress_percent);
            last_reported = progress_percent;
        }
    }
    
    // 通知所有线程任务完成
    pthread_mutex_lock(&task_mutex);
    all_tasks_done = 1;
    pthread_cond_broadcast(&task_cond);
    pthread_mutex_unlock(&task_mutex);
    
    // 等待所有线程结束
    printf("\nWaiting for threads to finish...\n");
    for (int i = 0; i < num_threads; i++) {
        pthread_join(threads[i], NULL);
    }
    
    long long end_time = getCurrentTime();
    long long total_time = end_time - start_time;
    
    printf("\n========================================\n");
    printf("All tasks completed successfully!\n");
    printf("Total files processed: %d\n", total_files);
    printf("Total rounds per file: %d\n", rounds_per_file);
    printf("Total operations: %d\n", total_files * rounds_per_file * 2);
    printf("Total time: %lld ms (%.2f seconds)\n", total_time, total_time / 1000.0);
    printf("Average time per file: %.2f ms\n", 
           (float)total_time / total_files);
    printf("Operations per second: %.2f\n", 
           (total_files * rounds_per_file * 2 * 1000.0) / total_time);
    printf("========================================\n");
    
    // 清理资源
    delete[] thread_data;
    delete[] threads;
    
    pthread_mutex_destroy(&task_mutex);
    pthread_cond_destroy(&task_cond);
    
    return 0;
}

/* ===================== 其他辅助函数 ===================== */
void prratio(FILE * stream, long int num, long int den)
{
    int q;

    if (den > 0)
    {
        if (num > 214748L) 
            q = (int)(num/(den/10000L));
        else
            q = (int)(10000L*num/den);
    }
    else
        q = 10000;

    if (q < 0)
    {
        putc('-', stream);
        q = -q;
    }
    fprintf(stream, "%d.%02d%%", q / 100, q % 100);
}

void Usage(int status)
{
    fprintf(status ? stderr : stdout, "\
Usage: %s [-dfhvcVr] [-b maxbits] [--] [file ...]\n\
    -d   If given, decompression is done instead.\n\
    -c   Write output on stdout, don't remove original.\n\
    -b   Parameter limits the max number of bits/code.\n", progname);
    fprintf(status ? stderr : stdout, "\
    -f   Forces output file to be generated, even if one already.\n\
    exists, and even if no space is saved by compressing.\n\
    If -f is not used, the user will be prompted if stdin is.\n\
    a tty, otherwise, the output file will not be overwritten.\n\
    -h   This help output.\n\
    -v   Write compression statistics.\n\
    -V   Output version and compile options.\n\
    -r   Recursive. If a filename is a directory, descend\n\
        into it and compress everything in it.\n");
    exit(status);
}

void about()
{
    printf("Compress version: %s\n", version_id);
    printf("Compile options:\n        ");
#ifdef FAST
    printf("FAST, ");
#endif
#ifdef SIGNED_COMPARE_SLOW
    printf("SIGNED_COMPARE_SLOW, ");
#endif
#ifdef MAXSEG_64K
    printf("MAXSEG_64K, ");
#endif
#ifdef DOS
    printf("DOS, ");
#endif
#ifdef DEBUG
    printf("DEBUG, ");
#endif
#ifdef LSTAT
    printf("LSTAT, ");
#endif
    printf("\n        IBUFSIZ=%d, OBUFSIZ=%d, BITS=%d\n",
        IBUFSIZ, OBUFSIZ, BITS);

    printf("\n\
Parallel version with thread-safe implementation\n\
\n\
Author version 5.x (Modernization):\n\
Author version 4.2.4.x (Maintenance):\n\
     Mike Frysinger  (vapier@gmail.com)\n\
\n\
Author version 4.2 (Speed improvement & source cleanup):\n\
     Peter Jannesen  (peter@ncs.nl)\n\
\n\
Author version 4.1 (Added recursive directory compress):\n\
     Dave Mack  (csu@alembic.acs.com)\n\
\n\
Authors version 4.0 (World release in 1985):\n\
     Spencer W. Thomas, Jim McKie, Steve Davies,\n\
     Ken Turkowski, James A. Woods, Joe Orost\n");

    exit(0);
}