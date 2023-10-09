// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "serialnumfileheadercontext.h"
#include <vespa/vespalib/data/fileheader.h>


namespace search::common {

SerialNumFileHeaderContext::SerialNumFileHeaderContext(
        const FileHeaderContext &parentFileHeaderContext,
        SerialNum serialNum)
    : FileHeaderContext(),
      _parentFileHeaderContext(parentFileHeaderContext),
      _serialNum(serialNum)
{
}


void
SerialNumFileHeaderContext::addTags(vespalib::GenericHeader &header,
                                    const vespalib::string &name) const
{
    _parentFileHeaderContext.addTags(header, name);
    using Tag = vespalib::GenericHeader::Tag;
    if (_serialNum != 0u)
        header.putTag(Tag("serialNum", _serialNum));
}

}
