// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <util/filereader.h>
#include <iostream>
#include <string.h>
#include <cassert>

/**
 * Extract query urls from web logs. The filterfile application reads
 * concatenated web logs from stdin and writes all query urls found in
 * the input to stdout. Urls beginning with '/cgi-bin/search?'  are
 * assumed to be query urls. Only the 'query' and 'type' parameters
 * are kept in the output.
 **/

int
main(int argc, char** argv)
{
    bool showUsage = false;
    bool allowAllParams = false;
    int  bufsize = 10240;

    // parse options and override defaults.
    int         optIdx;
    char        opt;
    const char *arg;
    bool        optError;

    optIdx = 1;
    optError = false;
    while((opt = GetOpt(argc, argv, "ahm:", arg, optIdx)) != -1) {
        switch(opt) {
        case 'a':
            allowAllParams = true;
            break;
        case 'h':
            showUsage = true;
            break;
        case 'm':
            bufsize = atoi(arg);
            if (bufsize < 10240) {
                bufsize = 10240;
            }
            break;
        default:
            optError = true;
            break;
        }
    }

    if (optError || showUsage) {
        printf("usage: vespa-fbench-filter-file [-a] [-h] [-m maxLineSize]\n\n");
        printf("Read concatenated fastserver logs from stdin and write\n");
        printf("extracted query urls to stdout.\n\n");
        printf(" -a : all parameters to the original query urls are preserved.\n");
        printf("      If the -a switch is not given, only 'query' and 'type'\n");
        printf("      parameters are kept in the extracted query urls.\n");
        printf(" -h : print this usage information.\n");
        printf(" -m <num> : max line size for input/output lines.\n");
        printf("            Can not be less than the default [10240]\n");
        return -1;
    }

    const char *beginToken = "GET ";
    int beginTokenlen = strlen(beginToken);

    const char *endToken = " HTTP/";

    //const char *prefix = "/cgi-bin/search?";
    const char *prefix = "/?";
    int prefixlen = strlen(prefix);

    //const char *trigger = "/cgi-bin/";
    const char *trigger = "";
    int triggerlen = strlen(trigger);

    // open input and output (should never fail)
    FileReader *reader = new FileReader();
    if (!reader->OpenStdin()) {
        printf("could not open stdin! (strange)\n");
        delete reader;
        return -1;
    }
    std::ostream & file = std::cout;

    // filter the input
    char *line    = new char[bufsize];
    assert(line != NULL);
    int   res;
    char *tmp;
    char *url;
    int   startIdx;
    int   endIdx;
    int   idx;
    int   outIdx;
    char *buf     = new char[bufsize];
    assert(buf != NULL);
    int   state; // 0=expect param name, 1=copy, 2=skip
    bool  gotQuery;
    memcpy(buf, prefix, prefixlen);
    while ((res = reader->ReadLine(line, bufsize - 1)) >= 0) {

        // find field beginning
        tmp = strstr(line, beginToken);
        startIdx = (tmp != NULL) ? (tmp - line) + beginTokenlen : 0;

        // find url beginning
        url = strstr(line + startIdx, trigger);
        if (url == NULL)
            continue;                                // CONTINUE

        // find field end
        tmp = strstr(line + startIdx, endToken);
        if (tmp == NULL)
            tmp = strstr(line + startIdx, "\"");
        endIdx = (tmp != NULL) ? (tmp - line) : strlen(line);

        // find params
        idx = (url - line) + triggerlen;
        while (idx < endIdx && line[idx++] != '?');
        if (idx >= endIdx)
            continue;                                // CONTINUE

        outIdx   = prefixlen;
        state    = 0;              // expect param name
        gotQuery = false;
        while(idx < endIdx) {
            switch (state) {
            case 0:
                state = ((strncmp(line + idx, "query=", 6) == 0
                          && (gotQuery = true)) ||
                         allowAllParams ||
                         strncmp(line + idx, "type=", 5) == 0) ? 1 : 2;
                break;
            case 1:
                buf[outIdx++] = line[idx];
                [[fallthrough]];
            case 2:
                if (line[idx++] == '&')
                    state = 0;
                break;
            }
        }
        if (!gotQuery)
            continue;                                // CONTINUE

        if (buf[outIdx - 1] == '&')
            outIdx--;
        buf[outIdx++] = '\n';
        buf[outIdx] = '\0';
        if (!file.write(buf, outIdx)) {
            reader->Close();
            delete reader;
            delete [] line;
            delete [] buf;
            return -1;
        }
    }
    reader->Close();
    delete reader;
    delete [] line;
    delete [] buf;
    return 0;
}
