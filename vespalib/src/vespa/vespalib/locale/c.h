// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace vespalib::locale::c {

double strtod(const char *nptr, char **endptr);
float  strtof(const char *nptr, char **endptr);

// allow underflow variants
double strtod_au(const char *nptr, char **endptr);
float  strtof_au(const char *nptr, char **endptr);

inline double atof(const char *nptr) { return strtod(nptr, nullptr); }

}

