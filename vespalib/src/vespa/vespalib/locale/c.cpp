// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "c.h"
#include "locale.h"
#include <cstdlib>
#include <cerrno>

namespace vespalib::locale::c {

namespace {

Locale _G_C_Locale;

}

double strtod(const char *startp, char **endp) {
    return strtod_l(startp, endp, _G_C_Locale.get());
}

float strtof(const char *startp, char **endp) {
    return strtof_l(startp, endp, _G_C_Locale.get());
}

double strtod_au(const char *startp, char **endp) {
    int was = errno;
    double v = strtod_l(startp, endp, _G_C_Locale.get());
    if (errno == ERANGE) {
        if ((-1.0 < v) && (v < 1.0)) errno = was;
    }
    return v;
}

float strtof_au(const char *startp, char **endp) {
    int was = errno;
    float v = strtof_l(startp, endp, _G_C_Locale.get());
    if (errno == ERANGE) {
        if ((-1.0 < v) && (v < 1.0)) errno = was;
    }
    return v;
}

}
