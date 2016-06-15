// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @file heapdebugger.h
 *
 * @author Ove Martin Malm
 * @date Creation date: 2000-08-09
 * @version $Id$
 * @file mcheckhooks.h
 *
 * Copyright (c) : 1997-2000 Fast Search & Transfer ASA
 * ALL RIGHTS RESERVED
 */

#pragma once
#include <cstddef>


/**
 *  This function is used for counting memory usage. Must be called before any block is allocated
 * (pt. linux only)
 * Parameter controls operation
 */
extern void enableHeapUsageMonitor(int param = 0);


/**
 * This function return the current memory used.
 * @return   Net number of bytes allocated on the heap
 */
extern size_t getHeapUsage(void);

/**
 * This enables heap debugging. Must be called before any block is allocated
 * Parameter controls operation
 * (pt. linux only)
 */
extern void enableHeapCorruptCheck(int param = 0);

#define HEAPCHECKMODE_REMOVE        -1      // Deinstall
#define HEAPCHECKMODE_NORMAL        0       // Normal
#define HEAPCHECKMODE_EXTENSIVE     1       // All allocated blocks checked on all ops..
#define HEAPCHECKMODE_DISABLED      2       // No checking (but extra bytes allocated)



/**
 * Run a heap check now. Will lock the heap and run through a full check. Will crash if it fails...
 */
extern void checkHeapNow(void);


/**
 * And this enables linux mcheck function with an approprate callback function. (absolutely linux only)
 * see man mcheck
 */
extern void enableMCheck(void);












