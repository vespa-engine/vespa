// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fileheadertags.h"

namespace search::tags {
// Do not change these constants, they are persisted in many file headers.
vespalib::string FREEZE_TIME("freezeTime");
vespalib::string CREATE_TIME("createTime");
vespalib::string FROZEN("frozen");
vespalib::string DOCID_LIMIT("docIdLimit");
vespalib::string FILE_BIT_SIZE("fileBitSize");
vespalib::string DESC("desc");
vespalib::string ENTRY_SIZE("entrySize");
vespalib::string NUM_KEYS("numKeys");

}
