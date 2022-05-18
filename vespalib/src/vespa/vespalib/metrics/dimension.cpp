// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "dimension.h"
#include "name_repo.h"

namespace vespalib::metrics {

Dimension
Dimension::from_name(const vespalib::string& name)
{
    return NameRepo::instance.dimension(name);
}

const vespalib::string&
Dimension::as_name() const
{
    return NameRepo::instance.dimensionName(*this);
}

} // namespace vespalib::metrics
