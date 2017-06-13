// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Copyright (C) 2003 Fast Search & Transfer ASA
// Copyright (C) 2003 Overture Services Norway AS

#pragma once

#include <sys/types.h>
#include <stdint.h>

namespace vespalib {

/**
 * @brief Atomic instructions class
 *
 * To avoid mutexes around simple counters, use these functions.
 * Currently only implemented for GCC on i386 and x86_64 platforms.
 * To check if instructions are available, test the feature macro
 * HAVE_VESPALIB_ATOMIC with \#ifdef.
 **/
class Atomic
{
public:
    /**
     * @brief Pointer and tag - use instead of bare pointer for cmpSwap()
     *
     * When making a lock-free data structure by using cmpSwap
     * on pointers, you'll often run into the "ABA problem", see
     * http://en.wikipedia.org/wiki/ABA_problem for details.
     * The TaggedPtr makes it easy to do the woraround with tag bits,
     * but requires the double-word compare-and-swap instruction.
     * Very early Amd K7/8 CPUs are lacking this and will fail (Illegal Instruction).
     **/
    struct TaggedPtr
    {
        TaggedPtr() : _ptr(nullptr), _tag(0) { }
        TaggedPtr(void * h, size_t t) : _ptr(h), _tag(t) { }
        TaggedPtr(const TaggedPtr & h) : _ptr(h._ptr), _tag(h._tag) { }
        TaggedPtr & operator = (const TaggedPtr & h) { if (this != &h) {_ptr = h._ptr; _tag = h._tag; }; return *this; }

        void   * _ptr;
        size_t   _tag;
    };
    static inline bool cmpSwap(volatile TaggedPtr * dest, TaggedPtr newVal, TaggedPtr oldVal);

    static inline void add(volatile uint32_t *data, uint32_t xdelta);
    static inline void sub(volatile uint32_t *data, uint32_t xdelta);
    static inline uint32_t postInc(volatile uint32_t *data);
    static inline uint32_t postDec(volatile uint32_t *data);
    static inline bool cmpSwap(volatile uint32_t * dest, uint32_t newVal, uint32_t oldVal);

    static inline int32_t postAdd(volatile int32_t *data,  int32_t xdelta);
    static inline void add(volatile int32_t *data,  int32_t xdelta);
    static inline void sub(volatile int32_t *data,  int32_t xdelta);
    static inline int32_t postInc(volatile int32_t *data);
    static inline int32_t postDec(volatile int32_t *data);
    static inline bool cmpSwap(volatile int32_t * dest, int32_t newVal, int32_t oldVal);

    static inline void add(volatile uint64_t *data, uint64_t xdelta);
    static inline void sub(volatile uint64_t *data, uint64_t xdelta);
    static inline uint64_t postInc(volatile uint64_t *data);
    static inline uint64_t postDec(volatile uint64_t *data);
    static inline bool cmpSwap(volatile uint64_t * dest, uint64_t newVal, uint64_t oldVal);

    static inline int64_t postAdd(volatile int64_t *data,  int64_t xdelta);
    static inline void add(volatile int64_t *data,  int64_t xdelta);
    static inline void sub(volatile int64_t *data,  int64_t xdelta);
    static inline int64_t postInc(volatile int64_t *data);
    static inline int64_t postDec(volatile int64_t *data);
    static inline bool cmpSwap(volatile int64_t * dest, int64_t newVal, int64_t oldVal);

#if defined(__x86_64__)
    static inline bool cmpSwap(volatile long long * dest, long long newVal, long long oldVal);
    static inline bool cmpSwap(volatile unsigned long long * dest, unsigned long long newVal, unsigned long long oldVal);
#endif
};

#if defined(__x86_64__)
    #define VESPALIB_ATOMIC_TAGGEDPTR_ALIGNMENT __attribute__ ((aligned (16)))
#else
    #error "VESPALIB_ATOMIC_TAGGEDPTR_ALIGNMENT can not be defined."
#endif

/**
 * @fn void Atomic::add(volatile int32_t *data, int32_t xdelta)
 * @brief perform atomic add instruction
 *
 * Atomically perform { *data += xdelta }
 * @param data pointer to the integer the add should be performed on
 * @param xdelta the delta to add
 **/

/**
 * @fn void Atomic::sub(volatile int32_t *data, int32_t xdelta)
 * @brief perform atomic substract instruction
 *
 * Atomically perform { *data -= xdelta }
 * @param data pointer to the integer the subtract should be performed on
 * @param xdelta the delta to subtract
 **/

/**
 * @fn void Atomic::add(volatile uint32_t *data, uint32_t xdelta)
 * @brief perform atomic add instruction
 *
 * Atomically perform { *data += xdelta }
 * @param data pointer to the integer the add should be performed on
 * @param xdelta the delta to add
 **/

/**
 * @fn void Atomic::sub(volatile uint32_t *data, uint32_t xdelta)
 * @brief perform atomic substract instruction
 *
 * Atomically perform { *data -= xdelta }
 * @param data pointer to the integer the subtract should be performed on
 * @param xdelta the delta to subtract
 **/


/**
 * @fn uint32_t Atomic::postDec(volatile uint32_t *data)
 * @brief perform atomic post-decrement
 *
 * Atomically perform { (*data)-- }
 * @param data pointer to the integer the decrement should be performed on
 **/

/**
 * @fn uint32_t Atomic::postInc(volatile uint32_t *data)
 * @brief perform atomic post-increment
 *
 * Atomically perform { (*data)++ }
 * @param data pointer to the integer the increment should be performed on
 **/

#if defined(__x86_64__)

#define HAVE_VESPALIB_ATOMIC

inline int32_t
Atomic::postAdd(volatile int32_t *data, int32_t xdelta)
{
    __asm__("lock ; xaddl %0,%1"
            : "+r" (xdelta),
              "+m" (*data)
            : : "memory");
    return xdelta;
}

inline void
Atomic::add(volatile int32_t *data, int32_t xdelta)
{
    __asm__("lock ; addl %1,%0"
            : "=m" (*data)
            : "ir" (xdelta), "m" (*data));
}

inline void
Atomic::add(volatile uint32_t *data, uint32_t xdelta)
{
    __asm__("lock ; addl %1,%0"
            : "=m" (*data)
            : "ir" (xdelta), "m" (*data));
}

inline void
Atomic::sub(volatile int32_t *data, int32_t xdelta)
{
    __asm__("lock ; subl %1,%0"
            : "=m" (*data)
            : "ir" (xdelta), "m" (*data));
}

inline void
Atomic::sub(volatile uint32_t *data, uint32_t xdelta)
{
    __asm__("lock ; subl %1,%0"
            : "=m" (*data)
            : "ir" (xdelta), "m" (*data));
}


inline int
Atomic::postDec(volatile int32_t *data)
{
    int32_t result;

    __asm__("lock ; xaddl %0, %1"
            : "=r" (result), "=m" (*data) : "0" (-1), "m" (*data));
    return result;
}

inline uint32_t
Atomic::postDec(volatile uint32_t *data)
{
    int32_t result;

    __asm__("lock ; xaddl %0, %1"
            : "=r" (result), "=m" (*data) : "0" (-1), "m" (*data));
    return result;
}

inline int
Atomic::postInc(volatile int32_t *data)
{
    int32_t result;

    __asm__("lock ; xaddl %0, %1"
            : "=r" (result), "=m" (*data) : "0" (1), "m" (*data));
    return result;
}

inline uint32_t
Atomic::postInc(volatile uint32_t *data)
{
    int32_t result;

    __asm__("lock ; xaddl %0, %1"
            : "=r" (result), "=m" (*data) : "0" (1), "m" (*data));
    return result;
}

inline bool
Atomic::cmpSwap(volatile int32_t * dest, int32_t newVal, int32_t oldVal)
{
    char result;
    __asm__ __volatile__("lock; cmpxchgl %2, %0;"
                         "setz %1"
                         : "+m"(*dest), "=q"(result)
                         : "r" (newVal), "a"(oldVal) : "memory");
    return result;
}

inline bool
Atomic::cmpSwap(volatile uint32_t * dest, uint32_t newVal, uint32_t oldVal)
{
    char result;
    __asm__ __volatile__("lock; cmpxchgl %2, %0;"
                         "setz %1"
                         : "+m"(*dest), "=q"(result)
                         : "r" (newVal), "a"(oldVal) : "memory");
    return result;
}

inline int64_t
Atomic::postAdd(volatile int64_t *data, int64_t xdelta)
{
    __asm__("lock ; xaddq %0,%1"
            : "+r" (xdelta),
              "+m" (*data)
            : : "memory");
    return xdelta;
}

inline void
Atomic::add(volatile int64_t *data, int64_t xdelta)
{
    __asm__("lock ; addq %1,%0"
            : "=m" (*data)
            : "ir" (xdelta), "m" (*data));
}

inline void
Atomic::add(volatile uint64_t *data, uint64_t xdelta)
{
    __asm__("lock ; addq %1,%0"
            : "=m" (*data)
            : "ir" (xdelta), "m" (*data));
}

inline void
Atomic::sub(volatile int64_t *data, int64_t xdelta)
{
    __asm__("lock ; subq %1,%0"
            : "=m" (*data)
            : "ir" (xdelta), "m" (*data));
}

inline void
Atomic::sub(volatile uint64_t *data, uint64_t xdelta)
{
    __asm__("lock ; subq %1,%0"
            : "=m" (*data)
            : "ir" (xdelta), "m" (*data));
}


inline int64_t
Atomic::postDec(volatile int64_t *data)
{
    int64_t result;

    __asm__("lock ; xaddq %0, %1"
            : "=r" (result), "=m" (*data) : "0" (int64_t(-1)), "m" (*data));
    return result;
}

inline uint64_t
Atomic::postDec(volatile uint64_t *data)
{
    uint64_t result;

#if defined(__i386__)
    do {
        result = *data;
    } while(!cmpSwap(data, result-1, result));
#else
    __asm__("lock ; xaddq %0, %1"
            : "=r" (result), "=m" (*data) : "0" (uint64_t(-1)), "m" (*data));
#endif
    return result;
}

inline int64_t
Atomic::postInc(volatile int64_t *data)
{
    int64_t result;

#if defined(__i386__)
    do {
        result = *data;
    } while(!cmpSwap(data, result+1, result));
#else
    __asm__("lock ; xaddq %0, %1"
            : "=r" (result), "=m" (*data) : "0" (1), "m" (*data));
#endif
    return result;
}

inline uint64_t
Atomic::postInc(volatile uint64_t *data)
{
    uint64_t result;

#if defined(__i386__)
    do {
        result = *data;
    } while(!cmpSwap(data, result+1, result));
#else
    __asm__("lock ; xaddq %0, %1"
            : "=r" (result), "=m" (*data) : "0" (1), "m" (*data));
#endif
    return result;
}

inline bool
Atomic::cmpSwap(volatile uint64_t * dest, uint64_t newVal, uint64_t oldVal) {
    bool result;
    __asm__ __volatile__("lock; cmpxchgq %3, %0;"
                         "setz %1"
                         : "+m"(*dest), "=q"(result), "+a"(oldVal)
                         : "r" (newVal)
                         : "memory", "cc");
    return result;
}

inline bool
Atomic::cmpSwap(volatile int64_t * dest, int64_t newVal, int64_t oldVal)
{
    return cmpSwap((volatile uint64_t *) dest, newVal, oldVal);
}

#if defined(__x86_64__)
inline bool
Atomic::cmpSwap(volatile unsigned long long * dest, unsigned long long newVal, unsigned long long oldVal)
{
    char result;
    unsigned long long res;
    union pair {
        uint64_t v64[2];
        unsigned long long v128;
    };
    pair nv;
    nv.v128 = newVal;
    __asm__ volatile (
        "lock ;"
        "cmpxchg16b %6;"
        "setz %2;"
        : "=A" (res),
          "=m" (*dest),
          "=q" (result)
        : "0" (oldVal),
          "b" (nv.v64[0]),
          "c" (nv.v64[1]),
          "m" (*dest)
        : "memory"
    );
    return result;
}

inline bool
Atomic::cmpSwap(volatile long long * dest, long long newVal, long long oldVal)
{
    return cmpSwap((volatile unsigned long long *) dest, newVal, oldVal);
}

#endif

inline bool
Atomic::cmpSwap(volatile TaggedPtr * dest, TaggedPtr newVal, TaggedPtr oldVal)
{
    char result;
    void * ptr;
    size_t tag;
#if defined(__x86_64__)
    __asm__ volatile (
        "lock ;"
        "cmpxchg16b %8;"
        "setz %1;"
        : "=m" (*dest),
          "=q" (result),
          "=a" (ptr),
          "=d" (tag)
        : "a" (oldVal._ptr),
          "d" (oldVal._tag),
          "b" (newVal._ptr),
          "c" (newVal._tag),
          "m" (*dest)
        : "memory"
    );
#else
#ifdef __pic__
    __asm__ volatile (
        "pushl %%ebx;"
        "movl %6, %%ebx;"
        "lock ;"
        "cmpxchg8b %8;"
        "setz %1;"
        "popl %%ebx;"
        : "=m" (*dest),
          "=q" (result),
          "=a" (ptr),
          "=d" (tag)
        : "2" (oldVal._ptr),
          "3" (oldVal._tag),
          "m" (newVal._ptr),
          "c" (newVal._tag),
          "m" (*dest)
        : "memory"
    );
#else
    __asm__ volatile (
        "lock ;"
        "cmpxchg8b %8;"
        "setz %1;"
        : "=m" (*dest),
          "=q" (result),
          "=a" (ptr),
          "=d" (tag)
        : "a" (oldVal._ptr),
          "d" (oldVal._tag),
          "b" (newVal._ptr),
          "c" (newVal._tag),
          "m" (*dest)
        : "memory"
    );
#endif

#endif

    return result;
}
#else
    #error "Atomic methods has not been defined for this platform."
#endif // #ifdef __x86_64__

} // namespace vespalib

