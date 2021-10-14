// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "label.h"
#include "name_repo.h"

namespace vespalib::metrics {

Label
Label::from_value(const vespalib::string& value)
{
    return NameRepo::instance.label(value);
}

const vespalib::string&
Label::as_value() const
{
    return NameRepo::instance.labelValue(*this);
}

} // namespace vespalib::metrics
