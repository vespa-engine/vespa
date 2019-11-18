// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fileheadercontext.h"
#include <vespa/vespalib/data/fileheader.h>
#include <chrono>

using namespace std::chrono;

namespace search::common {

using vespalib::GenericHeader;

FileHeaderContext::FileHeaderContext() = default;

FileHeaderContext::~FileHeaderContext() = default;

void
FileHeaderContext::addCreateAndFreezeTime(GenericHeader &header)
{
    typedef GenericHeader::Tag Tag;
    header.putTag(Tag("createTime", duration_cast<microseconds>(system_clock::now().time_since_epoch()).count()));
    header.putTag(Tag("freezeTime", 0));
}

void
FileHeaderContext::setFreezeTime(GenericHeader &header)
{
    typedef GenericHeader::Tag Tag;
    if (header.hasTag("freezeTime") &&
        header.getTag("freezeTime").getType() == Tag::TYPE_INTEGER) {
        header.putTag(Tag("freezeTime", duration_cast<microseconds>(system_clock::now().time_since_epoch()).count()));
    }
}

}
