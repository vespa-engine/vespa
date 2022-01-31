// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dictionarywordreader.h"
#include <vespa/searchlib/index/schemautil.h>
#include <vespa/vespalib/util/error.h>
#include <vespa/fastlib/io/bufferedfile.h>

#include <vespa/log/log.h>
LOG_SETUP(".diskindex.dictionarywordreader");

namespace search::diskindex {

using vespalib::getLastErrorString;
using index::SchemaUtil;

DictionaryWordReader::DictionaryWordReader()
    : _word(),
      _wordNum(noWordNumHigh()),
      _old2newwordfile(),
      _dictFile()
{
}


DictionaryWordReader::~DictionaryWordReader() = default;


bool
DictionaryWordReader::open(const vespalib::string & dictionaryName,
                           const vespalib::string & wordMapName,
                           const TuneFileSeqRead &tuneFileRead)
{
    _old2newwordfile.reset(new Fast_BufferedFile(new FastOS_File));
    _dictFile.reset(new PageDict4FileSeqRead);
    if (!_dictFile->open(dictionaryName, tuneFileRead)) {
        LOG(error, "Could not open dictionary %s: %s",
            dictionaryName.c_str(), getLastErrorString().c_str());
        return false;
    }
    _wordNum = noWordNum();

    // Make a mapping from old to new wordID
    if (tuneFileRead.getWantDirectIO()) {
        _old2newwordfile->EnableDirectIO();
    }
    // no checking possible
    _old2newwordfile->OpenWriteOnly(wordMapName.c_str());
    _old2newwordfile->SetSize(0);

    return true;
}

void
DictionaryWordReader::writeNewWordNum(uint64_t newWordNum) {
    _old2newwordfile->WriteBuf(&newWordNum, sizeof(newWordNum));
}

void
DictionaryWordReader::close()
{
    if (!_dictFile->close()) {
        LOG(error, "Error closing input dictionary");
    }
    bool sync_ok = _old2newwordfile->Sync();
    assert(sync_ok);
    bool close_ok = _old2newwordfile->Close();
    assert(close_ok);
}

}
