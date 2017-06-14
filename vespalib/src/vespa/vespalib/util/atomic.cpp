// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "atomic.h"

namespace vespalib {

// here are 5 operation, on unsigned 32-bit integers:
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
/**
 * @fn bool Atomic::cmpSwap(volatile uint32_t * dest, uint32_t newVal, uint32_t oldVal)
 * @brief atomic compare and set
 *
 * Compares the current contents of the destination with oldVal; if they are
 * equal, change destination to newVal.
 * See http://en.wikipedia.org/wiki/Compare-and-swap for more details.
 * @param dest pointer to memory that shall be compared and set
 * @param newVal new value to store in dest
 * @param oldVal expected old value in dest
 * @return true if the swap was performed, false if the destination data had changed
 **/


// the rest is variants with slightly different argument types

// signed 32-bit:

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
 * @fn int32_t Atomic::postDec(volatile int32_t *data)
 * @brief perform atomic post-decrement
 *
 * Atomically perform { (*data)-- }
 * @param data pointer to the integer the decrement should be performed on
 **/
/**
 * @fn int32_t Atomic::postInc(volatile int32_t *data)
 * @brief perform atomic post-increment
 *
 * Atomically perform { (*data)++ }
 * @param data pointer to the integer the increment should be performed on
 **/
/**
 * @fn bool Atomic::cmpSwap(volatile int32_t * dest, int32_t newVal, int32_t oldVal)
 * @brief atomic compare and set
 * @param dest pointer to memory that shall be compared and set
 * @param newVal new value to store in dest
 * @param oldVal expected old value in dest
 * @return true if the swap was performed, false if the destination data had changed
 **/

// unsigned 64-bit:

/**
 * @fn void Atomic::add(volatile uint64_t *data, uint64_t xdelta)
 * @brief perform atomic add instruction
 *
 * Atomically perform { *data += xdelta }
 * @param data pointer to the integer the add should be performed on
 * @param xdelta the delta to add
 **/
/**
 * @fn void Atomic::sub(volatile uint64_t *data, uint64_t xdelta)
 * @brief perform atomic substract instruction
 *
 * Atomically perform { *data -= xdelta }
 * @param data pointer to the integer the subtract should be performed on
 * @param xdelta the delta to subtract
 **/
/**
 * @fn uint64_t Atomic::postInc(volatile uint64_t *data)
 * @brief perform atomic post-increment
 *
 * Atomically perform { (*data)++ }
 * @param data pointer to the integer the increment should be performed on
 * @return old value of memory location
 **/
/**
 * @fn uint64_t Atomic::postDec(volatile uint64_t *data)
 * @brief perform atomic post-decrement
 *
 * Atomically perform { (*data)-- }
 * @param data pointer to the integer the decrement should be performed on
 * @return old value of memory location
 **/
/**
 * @fn bool Atomic::cmpSwap(volatile uint64_t * dest, uint64_t newVal, uint64_t oldVal)
 * @brief atomic compare and set
 * @param dest pointer to memory that shall be compared and set
 * @param newVal new value to store in dest
 * @param oldVal expected old value in dest
 * @return true if the swap was performed, false if the destination data had changed
 **/

// signed 64-bit:

/**
 * @fn void Atomic::add(volatile int64_t *data, int64_t xdelta)
 * @brief perform atomic add instruction
 *
 * Atomically perform { *data += xdelta }
 * @param data pointer to the integer the add should be performed on
 * @param xdelta the delta to add
 **/
/**
 * @fn void Atomic::sub(volatile int64_t *data, int64_t xdelta)
 * @brief perform atomic substract instruction
 *
 * Atomically perform { *data -= xdelta }
 * @param data pointer to the integer the subtract should be performed on
 * @param xdelta the delta to subtract
 **/
/**
 * @fn int64_t Atomic::postInc(volatile int64_t *data)
 * @brief perform atomic post-increment
 *
 * Atomically perform { (*data)++ }
 * @param data pointer to the integer the increment should be performed on
 * @return old value of memory location
 **/
/**
 * @fn int64_t Atomic::postDec(volatile int64_t *data)
 * @brief perform atomic post-decrement
 *
 * Atomically perform { (*data)-- }
 * @param data pointer to the integer the decrement should be performed on
 * @return old value of memory location
 **/
/**
 * @fn bool Atomic::cmpSwap(volatile int64_t * dest, int64_t newVal, int64_t oldVal)
 * @brief atomic compare and set
 * @param dest pointer to memory that shall be compared and set
 * @param newVal new value to store in dest
 * @param oldVal expected old value in dest
 * @return true if the swap was performed, false if the destination data had changed
 **/





// signed 128-bit:

/**
 * @fn bool Atomic::cmpSwap(volatile long long * dest, long long newVal, long long oldVal)
 * @brief atomic compare and set
 * @param dest pointer to memory that shall be compared and set
 * @param newVal new value to store in dest
 * @param oldVal expected old value in dest
 * @return true if the swap was performed, false if the destination data had changed
 **/

// pointer plus tag:
/**
 * @fn bool Atomic::cmpSwap(volatile TaggedPtr * dest, TaggedPtr newVal, TaggedPtr oldVal)
 * @brief atomic compare and set
 *
 * Compares the current contents of the destination with oldVal; if they are
 * equal, change destination to newVal.  Note that the entire TaggedPtr struct
 * (pointer plus tag) is compared and set in one atomic opertion.
 * See http://en.wikipedia.org/wiki/Compare-and-swap for more details.
 *
 * @param dest pointer to memory that shall be compared and set
 * @param newVal new value to store in dest
 * @param oldVal expected old value in dest
 * @return true if the swap was performed, false if the destination data had changed
 **/
/**
 * @fn bool Atomic::cmpSwap(volatile unsigned long long * dest, unsigned long long newVal, unsigned long long oldVal)
 * @brief atomic compare and set
 * @param dest pointer to memory that shall be compared and set
 * @param newVal new value to store in dest
 * @param oldVal expected old value in dest
 * @return true if the swap was performed, false if the destination data had changed
 **/


} // end namespace
