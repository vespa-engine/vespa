// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <vespa/fastlib/text/unicodeutil.h>
#include <vector>

namespace search {

/// Type of general unsigned 8 bit data.
typedef unsigned char byte;
/// A simple container for the raw querystack.
typedef vespalib::stringref QueryPacketT;
/// The type of the local documentId.
typedef unsigned DocumentIdT;
/// This is the type of the CollectionId used in the StorageAPI.
typedef uint64_t CollectionIdT;
/// The type to identify a query.
typedef unsigned QueryIdT;
/// The rank type.
typedef unsigned RankT;
/// How time type. Used to represent seconds since 1970.
typedef unsigned TimeT;
/// Type to identify performance counters.
typedef uint64_t CounterT;
/// Type to identify performance values.
typedef int ValueT;
/// This is a 16 byte vector used in SSE2 integer operations.
typedef char v16qi __attribute__ ((__vector_size__(16)));
/// This is a 2 element uint64_t vector used in SSE2 integer operations.
typedef long long v2di  __attribute__ ((__vector_size__(16)));
/// A type to represent a list of strings.
typedef std::vector<vespalib::string> StringListT;
/// A type to represent a vector of 32 bit signed integers.
typedef std::vector<int32_t> Int32ListT;
/// A type to represent a list of document ids.
typedef std::vector<DocumentIdT> DocumentIdList;

/// A debug macro the does "a" when l & the mask is true. The mask is set per file.
#define DEBUG(l, a) { if (l&DEBUGMASK) {a;} }
#ifdef __USE_RAWDEBUG__
  #define RAWDEBUG(a) a
#else
  #define RAWDEBUG(a)
#endif
/// A macro avoid warnings for unused parameters.
#define UNUSED_PARAM(p)
/// A macro that gives you number of elements in an array.
#define NELEMS(a)    (sizeof(a)/sizeof(a[0]))

}

