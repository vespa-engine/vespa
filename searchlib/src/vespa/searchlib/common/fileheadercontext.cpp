// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fileheadercontext.h"
#include "fileheadertags.h"
#include <vespa/vespalib/data/fileheader.h>
#include <chrono>

using namespace std::chrono;

namespace search::common {

using vespalib::GenericHeader;
using namespace tags;

FileHeaderContext::FileHeaderContext() = default;

FileHeaderContext::~FileHeaderContext() = default;

void
FileHeaderContext::addCreateAndFreezeTime(GenericHeader &header)
{
    using Tag = GenericHeader::Tag;
    header.putTag(Tag(CREATE_TIME, duration_cast<microseconds>(system_clock::now().time_since_epoch()).count()));
    header.putTag(Tag(FREEZE_TIME, 0));
}

void
FileHeaderContext::setFreezeTime(GenericHeader &header)
{
    using Tag = GenericHeader::Tag;
    if (header.hasTag(FREEZE_TIME) &&
        header.getTag(FREEZE_TIME).getType() == Tag::TYPE_INTEGER) {
        header.putTag(Tag(FREEZE_TIME, duration_cast<microseconds>(system_clock::now().time_since_epoch()).count()));
    }
}

}
