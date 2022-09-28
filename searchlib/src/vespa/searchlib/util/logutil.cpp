// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "logutil.h"
#include "dirtraverse.h"
#include <vector>

using vespalib::JSONStringer;

namespace search::util {

vespalib::string
LogUtil::extractLastElements(const vespalib::string & path, size_t numElems)
{
    std::vector<vespalib::string> elems;
    for (size_t pos = 0; pos < path.size(); ) {
        size_t fpos = path.find('/', pos);
        if (fpos == vespalib::string::npos) {
            fpos = path.size();
        }
        size_t len = fpos - pos;
        if (len > 0) {
            elems.push_back(path.substr(pos, len));
        }
        pos = fpos + 1;
    }
    vespalib::string retval;
    if (numElems >= elems.size() && path[0] == '/') {
        retval.append("/");
    }
    size_t num = std::min(numElems, elems.size());
    size_t pos = elems.size() - num;
    for (size_t i = 0; i < num; ++i) {
        if (i != 0) retval.append("/");
        retval.append(elems[pos + i]);
    }
    return retval;
}

void
LogUtil::logDir(JSONStringer & jstr, const vespalib::string & path, size_t numElems)
{
    jstr.beginObject();
    jstr.appendKey("dir").appendString(LogUtil::extractLastElements(path, numElems));
    search::DirectoryTraverse dirt(path.c_str());
    jstr.appendKey("size").appendInt64(dirt.GetTreeSize());
    jstr.endObject();
}

}
