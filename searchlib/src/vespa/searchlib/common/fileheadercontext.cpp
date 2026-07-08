// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fileheadercontext.h"

#include "create_and_freeze_times.h"
#include "fileheadertags.h"

#include <vespa/vespalib/data/fileheader.h>

using std::chrono::steady_clock;
using std::chrono::system_clock;

namespace search::common {

using vespalib::GenericHeader;
using namespace tags;

FileHeaderContext::FileHeaderContext() = default;

FileHeaderContext::~FileHeaderContext() = default;

void FileHeaderContext::addCreateAndFreezeTime(GenericHeader& header) {
    using Tag = GenericHeader::Tag;
    header.putTag(Tag(CREATE_TIME, CreateAndFreezeTimes::to_utc_us(system_clock::now())));
    header.putTag(Tag(FREEZE_TIME, 0));
}

void FileHeaderContext::setFreezeTime(GenericHeader& header) {
    using Tag = GenericHeader::Tag;
    if (header.hasTag(FREEZE_TIME) && header.getTag(FREEZE_TIME).getType() == Tag::TYPE_INTEGER) {
        auto freeze_time = CreateAndFreezeTimes::to_utc_us((system_clock::now()));
        if (header.hasTag(CREATE_TIME) && header.getTag(CREATE_TIME).getType() == Tag::TYPE_INTEGER) {
            auto create_time = header.getTag(CREATE_TIME).asInteger();
            if (freeze_time <= create_time) {
                // Workaround for insufficient system clock resolution or system clock being stepped backwards
                freeze_time = create_time + 1;
            }
        }
        header.putTag(Tag(FREEZE_TIME, freeze_time));
    }
}

} // namespace search::common
