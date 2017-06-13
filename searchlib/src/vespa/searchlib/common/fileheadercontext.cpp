// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fileheadercontext.h"
#include <vespa/vespalib/data/fileheader.h>
#include <vespa/fastos/timestamp.h>

namespace search::common {

using vespalib::GenericHeader;

FileHeaderContext::FileHeaderContext()
{
}

FileHeaderContext::~FileHeaderContext()
{
}

void
FileHeaderContext::addCreateAndFreezeTime(GenericHeader &header)
{
    typedef GenericHeader::Tag Tag;
    fastos::TimeStamp ts(fastos::ClockSystem::now());
    header.putTag(Tag("createTime", ts.us()));
    header.putTag(Tag("freezeTime", 0));
}

void
FileHeaderContext::setFreezeTime(GenericHeader &header)
{
    typedef GenericHeader::Tag Tag;
    if (header.hasTag("freezeTime") &&
        header.getTag("freezeTime").getType() == Tag::TYPE_INTEGER) {
        fastos::TimeStamp ts(fastos::ClockSystem::now());
        header.putTag(Tag("freezeTime", ts.us()));
    }
}

}
