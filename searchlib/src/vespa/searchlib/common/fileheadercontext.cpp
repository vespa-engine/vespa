// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fileheadercontext.h"
#include "fileheadertags.h"
#include <vespa/vespalib/data/fileheader.h>
#include <algorithm>

using namespace std::chrono;

namespace search::common {

using vespalib::GenericHeader;
using namespace tags;

namespace {

// Handle low resolution steady clock
steady_clock::duration min_flush_duration = std::max(duration_cast<steady_clock::duration>(microseconds(1u)),
                                                     steady_clock::duration(1u));
}

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
    if (header.hasTag(FREEZE_TIME) && header.getTag(FREEZE_TIME).getType() == Tag::TYPE_INTEGER) {
        auto freeze_time = duration_cast<microseconds>(system_clock::now().time_since_epoch()).count();
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

steady_clock::duration
FileHeaderContext::get_flush_duration(const vespalib::GenericHeader& header)
{
    if (header.hasTag(CREATE_TIME) && header.hasTag(FREEZE_TIME)) {
        auto create_time = header.getTag(CREATE_TIME).asInteger();
        auto freeze_time = header.getTag(FREEZE_TIME).asInteger();
        if (freeze_time >= create_time) {
            return std::max(duration_cast<steady_clock::duration>(microseconds(freeze_time - create_time)),
                            min_flush_duration);
        }
    }
    return steady_clock::duration::zero();
}

steady_clock::duration
FileHeaderContext::make_flush_duration(const steady_clock::time_point& create_time)
{
    steady_clock::duration flush_duration = steady_clock::now() - create_time;
    return std::max(flush_duration, min_flush_duration);
}

}
