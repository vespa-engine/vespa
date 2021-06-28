// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

/**
 * Contains the interfaces that vespamalloc offers in addition to the standard
 * allocation interfaces like malloc, aclloc, free, new, delete, etc.
 * Use dlsym(RTLD_NEXT, "function_name") to ensure the existence of the interface.
 **/

extern "C" {

/**
 * Reports the amount of memory vespamalloc uses. It is actually the size of the datasegment.
 * This is the peak memory usage during process lifetime, and does not differentiate between malloced or freed memory.
 * This is cheap to call
 **/
unsigned long vespamalloc_datasegment_size();

}