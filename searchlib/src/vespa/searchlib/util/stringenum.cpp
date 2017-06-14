// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Copyright (C) 2001-2003 Fast Search & Transfer ASA
// Copyright (C) 2003 Overture Services Norway AS

#include "stringenum.h"
#include <vespa/fastlib/io/bufferedfile.h>

#include <vespa/log/log.h>
LOG_SETUP(".seachlib.util.stringenum");

namespace search {
namespace util {

static inline char *
StripString(char *str)
{
    char *first = NULL; // first non-space char
    char *last  = NULL; // last non-space char

    if (str == NULL)
        return NULL;

    for (; *str != '\0' && isspace(*str); str++);
    first = str;

    for (; *str != '\0'; str++)
        if (!isspace(*str))
            last = str;

    if (last != NULL)
        *(last + 1) = '\0';

    return first;
}

StringEnum::~StringEnum() { }

void
StringEnum::CreateReverseMapping() const
{
    _reverseMap.resize(_numEntries);

    for (Map::const_iterator it = _mapping.begin();
         it != _mapping.end();
         it++)
    {
        assert(it->second >= 0);
        assert(it->second < (int)_numEntries);
        _reverseMap[it->second] = it->first.c_str();
    }
}


bool
StringEnum::Save(const char *filename)
{
    char     str[1024];

    Fast_BufferedFile file;
    file.WriteOpen(filename);
    if (!file.IsOpened())
        return false;

    file.SetSize(0);
    sprintf(str, "%d\n", _numEntries);
    file.WriteString(str);

    for (uint32_t i = 0; i < _numEntries; i++) {
        file.WriteString(Lookup(i));
        file.WriteString("\n");
    }

    file.Close();
    return true;
}


bool
StringEnum::Load(const char *filename)
{
    char        line[1024];
    char       *pt;
    uint32_t    entries;    // from first line of file
    uint32_t    lineNumber; // current line in file
    uint32_t    entryCnt;   // # entries obtained from file

    Clear();

    Fast_BufferedFile file;
    if (!file.OpenReadOnly(filename))
        return false;

    lineNumber = 0;
    entryCnt   = 0;

    pt = StripString(file.ReadLine(line, sizeof(line)));
    if (pt == NULL || *pt == '\0')
        return false;
    lineNumber++;

    entries = atoi(pt);

    while (!file.Eof()) {
        pt = StripString(file.ReadLine(line, sizeof(line)));
        if (pt == NULL)  // end of input ?
            break;
        lineNumber++;
        if (*pt == '\0') // empty line ?
            continue;

        uint32_t tmp = _numEntries;
        if (static_cast<uint32_t>(Add(pt)) != tmp) {
            LOG(error, "(%s:%d) duplicate enum entry: %s", filename, lineNumber, pt);
        }
        entryCnt++;
    }

    file.Close();
    if (entries != _numEntries
        || entries != entryCnt) {
        Clear();
        return false;
    }
    return true;
}

}
}
