// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/config/common/source.h>
#include "rawsourcefactory.h"
#include "rawsource.h"

namespace config {

Source::UP
RawSourceFactory::createSource(const IConfigHolder::SP & holder, const ConfigKey & key) const
{
    (void) key;
    return Source::UP(new RawSource(holder, _payload));
}

}
