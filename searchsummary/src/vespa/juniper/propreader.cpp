// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "propreader.h"
#include <vespa/fastlib/io/bufferedfile.h>
#include "juniperdebug.h"
#include <vespa/log/log.h>
LOG_SETUP(".juniper.propreader");

PropReader::PropReader(const char* filename)
    : _keymap()
{
    Process(filename);
}

#define BUFLEN 1024


void PropReader::Process(const char* filename)
{
    Fast_BufferedFile propfile;
    propfile.ReadOpen(filename);
    if (!propfile.IsOpened())
    {
        LOG(warning, "Warning: Could not find property file '%s', using Juniper default values",
            filename);
        return;
    }
    char line[BUFLEN];
    char* linep;
    while ((linep = propfile.ReadLine(line, BUFLEN-1)) != NULL)
    {
        int i;
        char* key;
        if (line[0] == '#') continue; // skip comments

        // find key
        for (i = 0; !isspace(line[i]); i++) { }
        if (i == 0) continue; // Skip lines starting with blank
        line[i++] = 0;
        key = line;

        for (; isspace(line[i]); i++) { }  // Skip blanks

        // find value
        int offset = 0;
        char* value = &line[i];
        for (; !isspace(line[i]); i++)
        {
            if (line[i] == '\\')
            {
                offset++;
                if (line[++i] == 'x')
                {
                    unsigned char v = 0;
                    for (int s = 1; s <= 2; s++, v<<=4)
                    {
                        unsigned char c = static_cast<unsigned char>(line[i + s]);
                        if (isdigit(c))
                            v += (c - '0');
                        else if (c < 'a')
                            v += (c - 'A' + 10);
                        else
                            v += (c - 'a' + 10);
                        if (s == 2) break;
                    }
                    line[i - offset] = static_cast<char>(v);
                    i += 2;
                    offset += 2;
                }
                else
                    if (offset != 0) line[i - offset] = line[i];
            }
            else
                if (offset != 0) line[i - offset] = line[i];
        }
        line[i - offset] = 0;
        LOG(debug, "Parameter :%s: value :%s:", key, value);
        _keymap.Insert(key, value);
    }
}


const char* PropReader::GetProperty(const char* name, const char* def) const
{
    const char*  v = _keymap.Lookup(name, def);
    LOG(debug, "Parameter lookup :%s: value :%s:", name, v);
    return v;
}


void PropReader::UpdateProperty(const char* name, const char* value)
{
    _keymap.Insert(name, value);
}
