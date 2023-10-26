// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "locale.h"
#include <cassert>

namespace vespalib::locale {

Locale::Locale() : Locale(LC_ALL_MASK, "C") { }
Locale::Locale(int category, const char *locale)
    : _locale(newlocale(category, locale, nullptr))
{
    assert(_locale != nullptr);
}

Locale::~Locale() {
    freelocale(_locale);
}

}

