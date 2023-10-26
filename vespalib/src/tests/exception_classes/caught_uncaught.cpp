// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/util/exceptions.h>

using namespace vespalib;

void throwE() {
    ExceptionWithPayload e("caught or not");
    throw e;
}

void silenceE() {
    ExceptionWithPayload e("caught or not");
    e.setPayload(std::make_unique<SilenceUncaughtException>(e));
    throw e;
}

void throwAndCatch() {
    try {
        silenceE();
    } catch (const ExceptionWithPayload & e) {
        printf("caught it\n");
    }
}

int main(int argc, char *argv[]) {
    if (argc != 2) {
        return 77;
    }
    if (strcmp("uncaught", argv[1]) == 0) {
        throwE();
    } else if (strcmp("silenced_and_caught", argv[1]) == 0) {
        throwAndCatch();
    } else if (strcmp("uncaught_after_silenced_and_caught", argv[1]) == 0) {
        throwAndCatch();
        throwE();
    } else if (strcmp("silenced_and_uncaught", argv[1]) == 0) {
        silenceE();
    } else {
        return 55;
    }

    return 0;
}
