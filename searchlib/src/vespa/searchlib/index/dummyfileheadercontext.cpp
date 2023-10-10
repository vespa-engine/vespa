// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dummyfileheadercontext.h"
#include <vespa/vespalib/data/fileheader.h>
#include <vespa/searchlib/util/fileheadertk.h>
#include <vespa/vespalib/util/host_name.h>
#include <cassert>
#include <unistd.h>

namespace search::index {

vespalib::string DummyFileHeaderContext::_creator;

DummyFileHeaderContext::DummyFileHeaderContext()
    : common::FileHeaderContext(),
      _disableFileName(false),
      _hostName(),
      _pid(getpid())
{
    _hostName = vespalib::HostName::get();
    assert(!_hostName.empty());
}

DummyFileHeaderContext::~DummyFileHeaderContext()
{
}

void
DummyFileHeaderContext::disableFileName()
{
    _disableFileName = true;
}

void
DummyFileHeaderContext::addTags(vespalib::GenericHeader &header,
                                const vespalib::string &name) const
{
    using Tag = vespalib::GenericHeader::Tag;

    FileHeaderTk::addVersionTags(header);
    if (!_disableFileName) {
        header.putTag(Tag("fileName", name));
        addCreateAndFreezeTime(header);
    }
    header.putTag(Tag("hostName", _hostName));
    header.putTag(Tag("pid", _pid));
    if (!_creator.empty()) {
        header.putTag(Tag("creator", _creator));
    }
    header.putTag(Tag("DummyFileHeaderContext", "enabled"));
}

void
DummyFileHeaderContext::setCreator(const vespalib::string &creator)
{
    _creator = creator;
}

}
