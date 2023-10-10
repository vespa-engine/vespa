// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <clocale>
#ifndef __linux__
#include <xlocale.h>
#endif

namespace vespalib::locale {

class Locale {
public:
    Locale();  // Standard C locale, NOT default locale.
    Locale(int category, const char *locale);
    ~Locale();
    locale_t get() const { return _locale; }
private:
    locale_t _locale;
};

}

