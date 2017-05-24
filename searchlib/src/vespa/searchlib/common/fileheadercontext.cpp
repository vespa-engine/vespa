// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".index.fileheadercontext");
#include "fileheadercontext.h"
#include <vespa/vespalib/data/fileheader.h>

namespace search
{

namespace common
{

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


} // namespace common

} // namespace search
