// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sourceselector.h"
#include <vespa/fastlib/io/bufferedfile.h>
#include <vespa/searchlib/common/fileheadercontext.h>
#include <vespa/vespalib/util/size_literals.h>

using search::queryeval::Source;
using vespalib::FileHeader;
using vespalib::GenericHeader;
using search::common::FileHeaderContext;

namespace search {

namespace {

const vespalib::string defaultSourceTag = "Default source";
const vespalib::string baseIdTag = "Base id";
const vespalib::string docIdLimitTag = "Doc id limit";

class AddMyHeaderTags : public FileHeaderContext
{
    const SourceSelector::HeaderInfo &_hi;
    const FileHeaderContext &_parent;

public:
    AddMyHeaderTags(const SourceSelector::HeaderInfo &hi, const FileHeaderContext &parent)
        : _hi(hi),
          _parent(parent)
    { }

    virtual void
    addTags(GenericHeader &header, const vespalib::string &name) const override
    {
        typedef GenericHeader::Tag Tag;
        _parent.addTags(header, name);
        header.putTag(Tag(defaultSourceTag, _hi._defaultSource));
        header.putTag(Tag(baseIdTag, _hi._baseId));
        header.putTag(Tag(docIdLimitTag, _hi._docIdLimit));
    }
};

}  // namespace

SourceSelector::HeaderInfo::HeaderInfo(const vespalib::string & baseFileName,
                                       Source defaultSource,
                                       uint32_t baseId,
                                       uint32_t docIdLimit) :
    _baseFileName(baseFileName),
    _defaultSource(defaultSource),
    _baseId(baseId),
    _docIdLimit(docIdLimit)
{ }

SourceSelector::SaveInfo::SaveInfo(const vespalib::string & baseFileName,
                                   Source defaultSource,
                                   uint32_t baseId,
                                   uint32_t docIdLimit,
                                   AttributeVector & sourceStore)
    : _header(baseFileName, defaultSource, baseId, docIdLimit),
      _memSaver()
{
    sourceStore.save(_memSaver, _header._baseFileName);
}

bool
SourceSelector::SaveInfo::save(const TuneFileAttributes &tuneFileAttributes,
                               const FileHeaderContext &fileHeaderContext)
{
    AddMyHeaderTags fh(_header, fileHeaderContext);
    return _memSaver.writeToFile(tuneFileAttributes, fh);
}

SourceSelector::LoadInfo::LoadInfo(const vespalib::string &baseFileName)
    : _header(baseFileName, 0, 0, 0)
{ }

void
SourceSelector::LoadInfo::load()
{
    const vespalib::string fileName = _header._baseFileName + ".dat";
    Fast_BufferedFile file;
    // XXX no checking for success
    file.ReadOpen(fileName.c_str());

    FileHeader fileHeader(4_Ki);
    fileHeader.readFile(file);
    if (fileHeader.hasTag(defaultSourceTag)) {
        _header._defaultSource = fileHeader.getTag(defaultSourceTag).asInteger();
    }
    if (fileHeader.hasTag(baseIdTag)) {
        _header._baseId = fileHeader.getTag(baseIdTag).asInteger();
    }
    if (fileHeader.hasTag(docIdLimitTag)) {
        _header._docIdLimit = fileHeader.getTag(docIdLimitTag).asInteger();
    }
    file.Close();
}

SourceSelector::SourceSelector(Source defaultSource, AttributeVector::SP realSource) :
    ISourceSelector(defaultSource),
    _realSource(realSource)
{ }

SourceSelector::SaveInfo::UP
SourceSelector::extractSaveInfo(const vespalib::string & baseFileName)
{
    return SaveInfo::UP(new SaveInfo(baseFileName, getDefaultSource(), getBaseId(),
                                     getDocIdLimit(), *_realSource));
}

SourceSelector::LoadInfo::UP
SourceSelector::extractLoadInfo(const vespalib::string & baseFileName)
{
    return LoadInfo::UP(new LoadInfo(baseFileName));
}

SourceSelector::Histogram SourceSelector::getDistribution() const
{
    Histogram h;
    auto it = createIterator();
    for (size_t i(0), m(getDocIdLimit()); i < m; i++) {
        h.inc(it->getSource(i));
    }
    return h;
}

} // namespace search
