// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/* Included *after* config.h, in order to re-define these macros */

#ifdef PACKAGE
#undef PACKAGE
#endif

/* Name of package */
#define PACKAGE "fnetrpc"

#ifdef VERSION
#undef VERSION
#endif

/* Version number of package */
#define VERSION "0.0.1"
