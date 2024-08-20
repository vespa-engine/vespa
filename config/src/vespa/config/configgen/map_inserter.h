// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "value_converter.h"
#include <vespa/vespalib/data/slime/object_traverser.h>
#include <map>
#include <string>

namespace config::internal {

template<typename T, typename Converter = config::internal::ValueConverter<T> >
class MapInserter : public ::vespalib::slime::ObjectTraverser {
public:
    MapInserter(std::map<std::string, T> & map);
    void field(const ::vespalib::Memory & symbol, const ::vespalib::slime::Inspector & inspector) override;
private:
    std::map<std::string, T> & _map;
};

}
