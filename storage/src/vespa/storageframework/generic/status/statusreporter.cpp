// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "statusreporter.h"

namespace storage {
namespace framework {

StatusReporter::StatusReporter(std::string_view id, std::string_view name)
    : _id(id),
      _name(name)
{
}

StatusReporter::~StatusReporter()
{
}

} // framework
} // storage
