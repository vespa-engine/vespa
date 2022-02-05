// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/config/common/source.h>
#include "rawsourcefactory.h"
#include "rawsource.h"

namespace config {

std::unique_ptr<Source>
RawSourceFactory::createSource(std::shared_ptr<IConfigHolder> holder, const ConfigKey & key) const
{
    (void) key;
    return std::make_unique<RawSource>(std::move(holder), _payload);
}

}
