// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

namespace search {

/// Type of general unsigned 8 bit data.
typedef unsigned char byte;
/// The type of the local documentId.
typedef unsigned DocumentIdT;
/// How time type. Used to represent seconds since 1970.
typedef unsigned TimeT;
/// This is a 16 byte vector used in SSE2 integer operations.
typedef char v16qi __attribute__ ((__vector_size__(16)));
/// This is a 2 element uint64_t vector used in SSE2 integer operations.
typedef long long v2di  __attribute__ ((__vector_size__(16)));

/// A macro that gives you number of elements in an array.
#define NELEMS(a)    (sizeof(a)/sizeof(a[0]))

}

