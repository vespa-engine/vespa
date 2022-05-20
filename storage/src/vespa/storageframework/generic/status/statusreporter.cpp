// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "statusreporter.h"

namespace storage {
namespace framework {

StatusReporter::StatusReporter(vespalib::stringref id, vespalib::stringref name)
    : _id(id),
      _name(name)
{
}

StatusReporter::~StatusReporter()
{
}

} // framework
} // storage
