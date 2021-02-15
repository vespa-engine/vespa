// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "documentsummary.h"
#include <vespa/fastlib/io/bufferedfile.h>
#include <vespa/searchlib/util/filekit.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/util/error.h>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.docsummary.documentsummary");

using vespalib::getLastErrorString;

namespace search {
namespace docsummary {

bool
DocumentSummary::readDocIdLimit(const vespalib::string &dir, uint32_t &count)
{
    char numbuf[20];
    Fast_BufferedFile qcntfile(4_Ki);
    unsigned int qcnt;
    vespalib::string qcntname;
    const char *p;

    qcntname = dir + "/docsum.qcnt";

    count = qcnt = 0;
    // XXX no checking for success
    qcntfile.ReadOpen(qcntname.c_str());
    if (!qcntfile.IsOpened() || qcntfile.Eof())
        return false;
    p = qcntfile.ReadLine(numbuf, sizeof(numbuf));
    while (*p >= '0' && *p <= '9')
        qcnt = qcnt * 10 + *p++ - '0';
    qcntfile.Close();
    count = qcnt;
    return true;
}


bool
DocumentSummary::writeDocIdLimit(const vespalib::string &dir, uint32_t count)
{
    vespalib::string qcntname = dir + "/docsum.qcnt";
    Fast_BufferedFile qcntfile(new FastOS_File);

    qcntfile.WriteOpen(qcntname.c_str());
    if (!qcntfile.IsOpened()) {
        LOG(error, "Could not open %s: %s", qcntname.c_str(), getLastErrorString().c_str());
        return false;
    }
    qcntfile.addNum(count, 0, ' ');
    qcntfile.WriteByte('\n');
    qcntfile.Sync();
    qcntfile.Close();
    return true;
}

}
}
