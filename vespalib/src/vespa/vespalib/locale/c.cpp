// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "c.h"
#include "locale.h"
#include <cstdlib>

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

}

