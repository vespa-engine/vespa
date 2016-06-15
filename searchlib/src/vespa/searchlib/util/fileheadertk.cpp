// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include "fileheadertk.h"

using namespace search;
using vespalib::GenericHeader;

void
FileHeaderTk::addVersionTags(vespalib::GenericHeader &header)
{
#ifdef V_TAG
    header.putTag(GenericHeader::Tag("version-tag", V_TAG));;
    header.putTag(GenericHeader::Tag("version-date", V_TAG_DATE));;
    header.putTag(GenericHeader::Tag("version-pkg", V_TAG_PKG));;
    header.putTag(GenericHeader::Tag("version-arch", V_TAG_ARCH));;
    header.putTag(GenericHeader::Tag("version-system", V_TAG_SYSTEM));
    header.putTag(GenericHeader::Tag("version-system-rev", V_TAG_SYSTEM_REV));
    header.putTag(GenericHeader::Tag("version-builder", V_TAG_BUILDER));
    header.putTag(GenericHeader::Tag("version-component", V_TAG_COMPONENT));
#else
    (void)header;
#endif
}
