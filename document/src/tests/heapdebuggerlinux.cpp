// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 *
 * @author Ove Martin Malm
 * @date Creation date: 2000-21-08
 * @version $Id$
 *
 * Copyright (c) : 1997-2000 Fast Search & Transfer ASA
 * ALL RIGHTS RESERVED
 */


// These are necessary to make it compile....
#define _MALLOC_INTERNAL
#define MALLOC_HOOKS
#include <stdio.h>
#define _LIBC
#include <malloc.h>
#include <mcheck.h>
#include <stdint.h>
#include <libintl.h>

#include <iostream>
#include <pthread.h>
#include <signal.h>
#include <unistd.h>
#include <string.h>
#include <ctype.h>

#include "heapdebugger.h"

// Predeclaration
static void HeapCorrupt(const char * caller, const struct elemheader *hdr, enum mcheck_status err);

// -------------------------------
// Part #1 Memory usage


#ifndef INTERNAL_SIZE_T
#define INTERNAL_SIZE_T size_t
#endif

#define SIZE_SZ                (sizeof(INTERNAL_SIZE_T))

/* size field is or'ed with PREV_INUSE when previous adjacent chunk in use */

#define PREV_INUSE 0x1

/* size field is or'ed with IS_MMAPPED if the chunk was obtained with mmap() */

#define IS_MMAPPED 0x2

/* Bits to mask off when extracting size */

#define SIZE_BITS (PREV_INUSE|IS_MMAPPED)

struct malloc_chunk
{
    INTERNAL_SIZE_T prev_size; /* Size of previous chunk (if free). */
    INTERNAL_SIZE_T size;      /* Size in bytes, including overhead. */
    struct malloc_chunk* fd;   /* double links -- used only if free. */
    struct malloc_chunk* bk;
};

typedef struct malloc_chunk* mchunkptr;

/* conversion from malloc headers to user pointers, and back */

#define chunk2mem(p)   ((Void_t*)((char*)(p) + 2*SIZE_SZ))
#define mem2chunk(mem) ((mchunkptr)((char*)(mem) - 2*SIZE_SZ))

/* Get size, ignoring use bits */

#define chunkSize(p)          ((p)->size & ~(SIZE_BITS))


static void (*old_free_hook_memusage)(void* buf, const void * caller );
static void* (*old_malloc_hook_memusage)(size_t bytes, const void * caller);
static void* (*old_realloc_hook_memusage)(void *buf, size_t bytes, const void *caller);
static void* (*old_memalign_hook_memusage)(size_t alignment, size_t bytes,const void *caller);
static size_t MemoryUsage = 0;
static pthread_mutex_t memUsageMutex = PTHREAD_MUTEX_INITIALIZER;
static bool MemUsageHookInstalled = false;


static void me_use_free_hook(void *ptr, const void *caller)
{
    mchunkptr p;

    pthread_mutex_lock(&memUsageMutex);

    if (ptr) {
        p = mem2chunk(ptr);
        MemoryUsage -= chunkSize(p);
    }

    __free_hook = old_free_hook_memusage;

    if (old_free_hook_memusage != NULL) {
        (*old_free_hook_memusage) (ptr, caller);
    } else {
        free (ptr);
    }

    __free_hook = me_use_free_hook;

    pthread_mutex_unlock(&memUsageMutex);
}

static void* me_use_malloc_hook(size_t size, const void *caller)
{
    void *anew;
    mchunkptr p;

    pthread_mutex_lock(&memUsageMutex);

    __malloc_hook = old_malloc_hook_memusage;

    if (old_malloc_hook_memusage != NULL) {
        anew = (*old_malloc_hook_memusage) (size, caller);
    } else {
        anew = malloc (size);
    }

    if (anew) {
        p = mem2chunk(anew);
        MemoryUsage += chunkSize(p);
    }

    __malloc_hook = me_use_malloc_hook;
    pthread_mutex_unlock(&memUsageMutex);

    return(anew);
}

static void* me_use_realloc_hook(void *ptr, size_t size, const void* caller)
{
    void *anew;
    mchunkptr p;
    size_t oldsz = 0;
    size_t newsz = 0;

    pthread_mutex_lock(&memUsageMutex);

    if (ptr) {
        p = mem2chunk(ptr);
        oldsz = chunkSize(p);
    }

    __free_hook = old_free_hook_memusage;
    __malloc_hook = old_malloc_hook_memusage;
    __realloc_hook = old_realloc_hook_memusage;

    if (old_realloc_hook_memusage != NULL) {
        anew = (*old_realloc_hook_memusage) (ptr, size, caller);
    } else {
        anew = realloc (ptr, size);
    }

    if (anew) {
        p = mem2chunk(anew);
        newsz = chunkSize(p);

        MemoryUsage += (newsz - oldsz);
    }

    __free_hook = me_use_free_hook;
    __malloc_hook = me_use_malloc_hook;
    __realloc_hook = me_use_realloc_hook;

    pthread_mutex_unlock(&memUsageMutex);

    return anew;
}

static void* me_use_memalign_hook(size_t alignment, size_t bytes,const void *caller)
{

    void *aligned;

    std::cout << "memalign" << std::endl;
    __memalign_hook = old_memalign_hook_memusage;

    if (old_memalign_hook_memusage != NULL) {
        aligned = (*old_memalign_hook_memusage) (alignment, bytes,caller);
    } else {
        aligned = memalign (alignment, bytes);
    }

    __memalign_hook = me_use_memalign_hook;

    return aligned;
}


void enableHeapUsageMonitor(int param)
{
    (void)param;
    if (!MemUsageHookInstalled) {
        old_free_hook_memusage = __free_hook;
        __free_hook = me_use_free_hook;
        old_malloc_hook_memusage = __malloc_hook;
        __malloc_hook = me_use_malloc_hook;
        old_realloc_hook_memusage = __realloc_hook;
        __realloc_hook = me_use_realloc_hook;
        MemUsageHookInstalled = true;
        std::cout << "Memory usage will be counted" << std::endl;
    }
}

size_t getHeapUsage(void)
{
    return(MemoryUsage);
}


// + Get status message....




// -------------------
// Part 2:  Heap corrupt

static void (*old_free_hook_memcorrupt)(void*, const void *);
static void* (*old_malloc_hook_memcorrupt)(size_t, const void *);
static void* (*old_realloc_hook_memcorrupt)(void*, size_t, const void *);
static void* (*old_memalign_hook_memcorrupt)(size_t alignment, size_t bytes,const void *caller);

static pthread_mutex_t HeapCorruptCheckMutex = PTHREAD_MUTEX_INITIALIZER;

static int HeapCorruptCheckMode = HEAPCHECKMODE_REMOVE;
static bool PrintUsage = false;


/* Arbitrary magical numbers.  */
#define MAGICWORD	0xfedabeeb
#define MAGICFREE	0xf1eef1ee
#define MAGICBYTE	((char) 0x01)  // "End byte"
#define MALLOCFLOOD	((char) 0x02)  // "Fresh (unused) memory"
#define FREEFLOOD	((char) 0x03)  // "Dead" memory

struct elemheader {
    __malloc_size_t size;	        /* Exact size requested by user.  */
    unsigned long int magic;	/* Magic number to check header integrity.  */
    struct elemheader *prev;
    struct elemheader *next;
};

/* This is the beginning of the list of all memory blocks allocated.
   It is only constructed if the pedantic testing is requested.  */

static struct elemheader *root;

static enum mcheck_status me_corrupt_mprobe (const struct elemheader * block)
{
    enum mcheck_status status;
    switch (block->magic ^ ((uintptr_t) block->prev + (uintptr_t) block->next)) {
    case MAGICFREE:
        status = MCHECK_FREE;
        break;
    case MAGICWORD:
    {
        const char *elem = (const char *)block;
        status = (elem[sizeof(struct elemheader)+block->size] != MAGICBYTE) ?  MCHECK_TAIL : MCHECK_OK;
    }
    break;

    default:
        status = MCHECK_HEAD;
        break;
    }
    return status;
}



static void unlink_blk (struct elemheader *ptr)
{
    if (ptr->next != NULL) {
        ptr->next->prev = ptr->prev;
        ptr->next->magic = MAGICWORD ^ ((uintptr_t) ptr->next->prev + (uintptr_t) ptr->next->next);
    }

    if (ptr->prev != NULL) {
        ptr->prev->next = ptr->next;
        ptr->prev->magic = MAGICWORD ^ ((uintptr_t) ptr->prev->prev + (uintptr_t) ptr->prev->next);
    } else {
        root = ptr->next;
    }
}

static void link_blk  (struct elemheader *ptr)
{
    ptr->prev = NULL;
    ptr->next = root;
    root = ptr;
    ptr->magic = MAGICWORD ^ (uintptr_t) ptr->next;

    /* And the next block.  */
    if (ptr->next != NULL) {
        ptr->next->prev = ptr;
        ptr->next->magic = MAGICWORD ^ ((uintptr_t) ptr + (uintptr_t) ptr->next->next);
    }
}

static void heapwalker(void)
{
    const struct elemheader *runp = root;

    while (runp)  {
        enum mcheck_status mstatus = me_corrupt_mprobe(runp);
        if (mstatus != MCHECK_OK) {
            HeapCorrupt("heapwalker",runp,mstatus);
        }
        runp = runp->next;
    }
}


static void me_corrupt_free_hook (__ptr_t ptr, const __ptr_t caller)
{
    enum mcheck_status mstatus;

    pthread_mutex_lock(&HeapCorruptCheckMutex);

    if (HeapCorruptCheckMode == HEAPCHECKMODE_EXTENSIVE) {
        heapwalker();
    }

    if (PrintUsage) {
        if (ptr) {
            struct elemheader *hdr = (struct elemheader *)ptr - 1;
            std::cout << "pid :" << getpid() << " free: Attempting to delete area with size " <<  hdr->size << " bytes" << std::endl;
        } else {
            std::cout << "pid :" << getpid() << " free: Attempting to delete NULL pointer " << std::endl;
        }
    }


    if (ptr) {
        struct elemheader *hdr = (struct elemheader *)ptr - 1;

        mstatus = (HeapCorruptCheckMode == HEAPCHECKMODE_DISABLED) ? MCHECK_OK : me_corrupt_mprobe(hdr);
        if (mstatus != MCHECK_OK) {
            HeapCorrupt("free", hdr ,mstatus);
        }

        if (mstatus == MCHECK_OK) {  // ForGet element if not detected or reallocated..
            hdr->magic = MAGICFREE;
            unlink_blk (hdr);
            hdr->prev = hdr->next = NULL;

            char * userdata = (char *)hdr+sizeof(struct elemheader);
            if (hdr->size > 0) {
                memset (userdata, FREEFLOOD, hdr->size);
            }

            ptr = (__ptr_t) hdr;
        } else {
            ptr = NULL;    // ForGet element if not detected or reallocated..
        }
    }


    __free_hook = old_free_hook_memcorrupt;

    if (old_free_hook_memcorrupt != NULL) {
        (*old_free_hook_memcorrupt) (ptr, caller);
    } else {
        free (ptr);
    }

    __free_hook = me_corrupt_free_hook;

    pthread_mutex_unlock(&HeapCorruptCheckMutex);
}

static __ptr_t me_corrupt_malloc_hook (__malloc_size_t size, const __ptr_t caller)
{
    struct elemheader *hdr;
    enum mcheck_status mstatus;

    pthread_mutex_lock(&HeapCorruptCheckMutex);


    if (HeapCorruptCheckMode == HEAPCHECKMODE_EXTENSIVE) {
        heapwalker();
    }

    if (PrintUsage) {
        std::cout << "pid :" << getpid() << " malloc: Attempting to allocate " <<  size << " bytes" << std::endl;
    }


    __malloc_hook = old_malloc_hook_memcorrupt;
    if (old_malloc_hook_memcorrupt != NULL) {
        hdr = (struct elemheader *) (*old_malloc_hook_memcorrupt) (sizeof (struct elemheader) + size + 1,caller);
    } else {
        hdr = (struct elemheader *) malloc (sizeof (struct elemheader) + size + 1);
    }

    __malloc_hook = me_corrupt_malloc_hook;

    if (hdr) {
        hdr->size = size;
        link_blk (hdr);

        char * userdata = (char *)hdr+sizeof(struct elemheader);
        userdata[size] = MAGICBYTE;
        if (size > 0) {
            memset (userdata, MALLOCFLOOD, size);
        }

        // Self check!
        mstatus = (HeapCorruptCheckMode == HEAPCHECKMODE_DISABLED) ? MCHECK_OK : me_corrupt_mprobe(hdr);
        if (mstatus != MCHECK_OK) {
            HeapCorrupt("malloc", hdr ,mstatus);
        }
    }

    pthread_mutex_unlock(&HeapCorruptCheckMutex);

    return hdr ? (__ptr_t) (hdr + 1) : NULL;
}

static __ptr_t me_corrupt_realloc_hook (__ptr_t ptr, __malloc_size_t size, const __ptr_t caller)
{
    struct elemheader *hdr;
    __malloc_size_t osize;
    enum mcheck_status mstatus;

    pthread_mutex_lock(&HeapCorruptCheckMutex);

    if (HeapCorruptCheckMode == HEAPCHECKMODE_EXTENSIVE) {
        heapwalker();
    }

    if (PrintUsage) {
        std::cout << "pid :" << getpid() << " realloc: Attempting to allocate area with size " <<  size << " bytes.";
        if (ptr) {
            hdr = (struct elemheader *)ptr - 1;
            std::cout << " Old buffer was " << hdr->size << " bytes" << std::endl;
        } else {
            std::cout << " No old buffer." << std::endl;
        }
    }

    if (ptr) {
        hdr = ((struct elemheader *) ptr) - 1;
        osize = hdr->size;

        mstatus = (HeapCorruptCheckMode == HEAPCHECKMODE_DISABLED) ? MCHECK_OK : me_corrupt_mprobe(hdr);
        if (mstatus != MCHECK_OK) {
            HeapCorrupt("realloc/1", hdr ,mstatus);
        }

        unlink_blk (hdr);
        if (size < osize) {
            memset ((char *) ptr + size, FREEFLOOD, osize - size);
        }
    } else {
        osize = 0;
        hdr = NULL;
    }

    __free_hook = old_free_hook_memcorrupt;
    __malloc_hook = old_malloc_hook_memcorrupt;
    __realloc_hook = old_realloc_hook_memcorrupt;

    if (old_realloc_hook_memcorrupt != NULL) {
        hdr = (struct elemheader *) (*old_realloc_hook_memcorrupt) ((__ptr_t) hdr, sizeof (struct elemheader) + size + 1, caller);
    } else {
        hdr = (struct elemheader *) realloc ((__ptr_t) hdr,sizeof (struct elemheader) + size + 1);
    }

    __free_hook = me_corrupt_free_hook;
    __malloc_hook = me_corrupt_malloc_hook;
    __realloc_hook = me_corrupt_realloc_hook;

    if (hdr) {
        hdr->size = size;
        link_blk (hdr);

        char * userdata = (char *)hdr+sizeof(struct elemheader);
        userdata[size] = MAGICBYTE;

        if (size > osize) {
            memset (&userdata[osize], MALLOCFLOOD, size-osize);
        }

        mstatus = (HeapCorruptCheckMode == HEAPCHECKMODE_DISABLED) ? MCHECK_OK : me_corrupt_mprobe(hdr);
        if (mstatus != MCHECK_OK) {
            HeapCorrupt("realloc/2", hdr ,mstatus);
        }
    }

    pthread_mutex_unlock(&HeapCorruptCheckMutex);

    return hdr ? (__ptr_t) (hdr + 1) : NULL;
}


static void* me_corrupt_memalign_hook(size_t alignment, size_t bytes,const void *caller)
{
    void *aligned;

    std::cout << "memalign #2 " << std::endl;
    __memalign_hook = old_memalign_hook_memcorrupt;

    if (old_memalign_hook_memcorrupt != NULL) {
        aligned = (*old_memalign_hook_memcorrupt) (alignment, bytes,caller);
    } else {
        aligned = memalign (alignment, bytes);
    }

    __memalign_hook = me_corrupt_memalign_hook;

    return aligned;
}



void enableHeapCorruptCheck(int param)
{

    pthread_mutex_lock(&HeapCorruptCheckMutex);


    // This is install..
    if (HeapCorruptCheckMode == HEAPCHECKMODE_REMOVE && param != HEAPCHECKMODE_REMOVE) {
        old_free_hook_memcorrupt = __free_hook;
        __free_hook = me_corrupt_free_hook;
        old_malloc_hook_memcorrupt = __malloc_hook;
        __malloc_hook=me_corrupt_malloc_hook;
        old_realloc_hook_memcorrupt = __realloc_hook;
        __realloc_hook = me_corrupt_realloc_hook;
        old_memalign_hook_memcorrupt = __memalign_hook;
        __memalign_hook = me_corrupt_memalign_hook;
        std::cout << "Heap corruption detector installed" << std::endl;
    }

    // This is deinstall..
    if (HeapCorruptCheckMode != HEAPCHECKMODE_REMOVE && param == HEAPCHECKMODE_REMOVE) {
        __free_hook = old_free_hook_memcorrupt;
        __malloc_hook = old_malloc_hook_memcorrupt;
        __realloc_hook = old_realloc_hook_memcorrupt;
        __memalign_hook = old_memalign_hook_memcorrupt;
        std::cout << "Heap corruption detector removed" << std::endl;
    }

    HeapCorruptCheckMode = param;
    pthread_mutex_unlock(&HeapCorruptCheckMutex);
}




void checkHeapNow(void)
{
    pthread_mutex_lock(&HeapCorruptCheckMutex);
    heapwalker();
    pthread_mutex_unlock(&HeapCorruptCheckMutex);
}


//---------------------------------
// Part 3: glibc's mcheck librrary

static void mcheckabortfunc(enum mcheck_status err)
{
    HeapCorrupt("mcheckabortfunc",NULL,err);
}


void enableMCheck(void)
{
    mcheck(&mcheckabortfunc);
}


//---------------------------------
// Part 4: error report & exit

static void HeapDump(const char *info,const char *p,int cnt)
{
    char Legend[150];
    snprintf(Legend,sizeof(Legend),"Legend: Magic (tail) [%02x]. allocated, but unused: [%02x]. Reclaimed to free pool: [%02x]",
             (unsigned char)MAGICBYTE,
             (unsigned char)MALLOCFLOOD,
             (unsigned char)FREEFLOOD);

    std::cout << Legend << std::endl;
    std::cout << "Buffer dump: " <<  info << std::endl;

    for (int i = 0;i < cnt;i++) {
        char c = p[i];
        if (isgraph(c) || c == ' ') {
            std::cout << c;
        } else {
            char Tmp[10];
            snprintf(Tmp,sizeof(Tmp),"[%02x]",(unsigned char)c);
            std::cout << Tmp;
        }
    }
    std::cout << std::endl;
}


static void HeapCorrupt(const char * caller, const struct elemheader *hdr, enum mcheck_status err)
{

    bool AbortNow = true;

#define DUMPSIZE 100

    if (!caller) {
        caller = "?";
    }
    switch (err) {
    case MCHECK_DISABLED:
        std::cout << caller
                  << ": Heap check was not called before the first allocation. No consistency checking can be done."
                  << std::endl;
        break;

    case MCHECK_OK:
        std::cout << caller
                  << ": No inconsistency detected."
                  << std::endl;
        AbortNow = false;
        break;

    case MCHECK_HEAD:
        std::cout << caller
                  <<  ": The data immediately before the block was modified. This commonly happens when an array index or pointer is decremented too far."
                  << std::endl
                  << "  This block is ignored (and may appear as memory leakage)."
                  << std::endl;

        if (hdr) {
            HeapDump("Immediately before ",((const char *)hdr) - (DUMPSIZE/2) ,DUMPSIZE/2);
            HeapDump("The element in question ",(const char *)hdr,DUMPSIZE/2);
        }
        AbortNow = false;
        break;

    case MCHECK_TAIL:
        std::cout << caller
                  << ": The data immediately after the block was modified. This commonly happens when an array index or pointer is incremented too far."
                  << std::endl;

        if (hdr) {
            HeapDump("Tail of element in question: ", ((const char *)hdr) + sizeof(struct elemheader) +  hdr->size + 1 - (DUMPSIZE/2),(DUMPSIZE/2));
            HeapDump("Immediately after element: ",((const char *)hdr) + sizeof(struct elemheader) + hdr->size + 1 + 1, (DUMPSIZE/2));
        }

        break;

    case MCHECK_FREE:
        std::cout << caller
                  << ":  The block was already freed."
                  << std::endl;
        break;


    default:
        std::cout << caller
                  << ":  Unexpected event " << err
                  << std::endl;
        break;
    }


    if (hdr) {
        std::cout << caller << ": The element size was " << hdr->size << " bytes." << std::endl;
        if (err == MCHECK_HEAD) {
            std::cout << caller << ": But size may be incorrect since other parts of the header is destroyed/undecodable." << std::endl;
        }
    }

    if (AbortNow) {
        std::cout << caller << ": Application will crash now." << std::flush;

        // No core dump? Check ulimit -c

        kill(getpid(), SIGSEGV);
    }

}

// Enable this one if you want the heapdebugger to be installed "before anything else"
// Uses same method as -lmcheck
#if 0

/* This one is called from malloc init.. */

static void MallocInitHook(void)
{
    enableHeapUsageMonitor(0);
    enableHeapCorruptCheck(0);
}

void (*__malloc_initialize_hook) (void) = MallocInitHook;


#endif
