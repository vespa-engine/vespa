// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

// Include something from STL so that _STLPORT_VERSION gets defined if appropriate
#include <string>
#include <algorithm>

/* Juniper debug macro  */

#define JD_INFO         0x1  /* Useful information (verbose mode) */
#define JD_PAR          0x2  /* Tracking parameter settings mm. */
#define JD_DUMP         0x4  /* Dump statistics etc. */
#define JD_JE          0x10  /* Juniper per query entry/exit */
#define JD_ENT        0x100  /* Enter functions */
#define JD_EXIT       0x200  /* Enter functions */
#define JD_INPUT      0x400  /* Tracking input */
#define JD_WCMP      0x1000  /* Word completion */
#define JD_DESC      0x2000  /* Descriptor buildup */
#define JD_SUMLEN    0x4000  /* Dynamic teaser length */
#define JD_MDUMP     0x8000  /* Dumping found/qualified matches and match occurrences */
#define JD_TOKEN    0x10000  /* Tokenization (verbose) */
#define JD_ALLOC    0x20000  /* Allocations and deallocations */
#define JD_PAR_V    0x40000  /* Parameter setting tracking (verbose) */
#define JD_TOKBYT  0x100000  /* Use hexbyte token output (with JD_TOKEN) */
#define JD_STACK   0x200000  /* Dump stack but do not attempt to process anything */

/* Logging to log object (juniperlog summary field) */
#define JL(level, stmt)  do { if (_log_mask & level) { stmt; } } while (0)

#ifdef FASTOS_DEBUG
extern unsigned debug_level;
#define JD(level, stmt)  do { if (debug_level & level) { stmt; } } while (0)
# warning "FASTOS_DEBUG is defined"

/* Invariant checking */

#define JD_INVAR(level, condition, action, log) \
   do { if (!(condition)) { if (debug_level & level) { log; } action; } } while (0)
#else

#define JD_INVAR(level, condition, action, log) \
   do { if (!(condition)) { action; } } while (0)
#define JD(level, stmt)

#endif

template <class _container>
void dump_list(_container& __c)
{
    std::for_each(__c.begin(), __c.end(), [](auto& elem) { elem->dump(); });
}

