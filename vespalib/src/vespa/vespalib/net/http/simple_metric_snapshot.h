// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/data/slime/slime.h>

namespace vespalib {

class SimpleMetricSnapshot
{
private:
    vespalib::Slime _data;
    vespalib::slime::Cursor& _metrics;
    vespalib::slime::Cursor& _values;
    double _snapLen;

public:
    SimpleMetricSnapshot(uint32_t prevTime, uint32_t currTime);
    void addCount(const char *name, const char *desc, uint32_t count);
    void addGauge(const char *name, const char *desc, long gauge);

    vespalib::string asString() const {
        return _data.toString();
    }
};

} // namespace vespalib
