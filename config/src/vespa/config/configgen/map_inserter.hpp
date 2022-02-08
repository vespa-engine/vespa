// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "map_inserter.h"

namespace config::internal {

template<typename T, typename Converter>
MapInserter<T, Converter>::MapInserter(std::map<vespalib::string, T> & map)
    : _map(map)
{}

template<typename T, typename Converter>
void
MapInserter<T, Converter>::field(const ::vespalib::Memory & symbol, const ::vespalib::slime::Inspector & inspector)
{
    Converter converter;
    _map[symbol.make_string()] = converter(inspector);
}

}
