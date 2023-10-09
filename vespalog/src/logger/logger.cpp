// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <fcntl.h>
#include <errno.h>
#include <unistd.h>
#include <time.h>

#include <vespa/log/llparser.h>
#include "llreader.h"

using namespace ns_log;

int main(int argc, char *argv[])
{
    InputBuf input(0);
    LLParser llparser;

    int ch;
    while ((ch = getopt(argc, argv, "s:c:l:p:h")) != -1) {
        switch (ch) {
        case 's':
            llparser.setService(optarg);
            break;
        case 'c':
            llparser.setComponent(optarg);
            break;
        case 'l': {
            Logger::LogLevel level = Logger::parseLevel(optarg);
            if (level == Logger::NUM_LOGLEVELS) {
                fprintf(stderr, "Unknown loglevel %s - using info\n",
                        optarg);
                level = Logger::info;
            }
            llparser.setDefaultLevel(level);
            break;
        }
        case 'p':
            llparser.setPid(atoi(optarg));
            break;
        default:
            fprintf(stderr, "Usage: foo | %s [-s service] [-c component]"
                    "[-l level] [-p pid]\n", argv[0]);
            return (ch == 'h') ? EXIT_SUCCESS : EXIT_FAILURE;
        }
    }

    try {
        input.doAllInput(llparser);
    } catch (MsgException& ex) {
        fprintf(stderr, "error: %s\n", ex.what());
        return 1;
    }
    return 0;
}
