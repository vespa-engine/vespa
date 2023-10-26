// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vdstestlib/config/dirconfig.h>

#include <sstream>
#include <boost/lexical_cast.hpp>

namespace vdstestlib {

template<typename T>
void
DirConfig::Config::setValue(const ConfigKey& key, const T& value)
{
    std::ostringstream ost;
    ost << value;
    set(key, ost.str());
}

template<typename T>
T
DirConfig::Config::getValue(const ConfigKey& key, const T& defVal) const
{
    const ConfigValue* val(get(key));
    if (val == 0) return defVal;
    return boost::lexical_cast<T>(*val);
}

} // storage
