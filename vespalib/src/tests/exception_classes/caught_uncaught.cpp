#include <vespa/vespalib/util/exception.h>

using vespalib::SilenceUncaughtException;
using vespalib::GuardTheTerminationHandler;

void throwE() {
    std::runtime_error e("caught or not");
    throw e;
}

void silenceE() {
    std::runtime_error e("caught or not");
    SilenceUncaughtException silenced(e);
    throw e;
}

void throwAndCatch() {
    GuardTheTerminationHandler terminationHandlerGuard;
    try {
        silenceE();
    } catch (const std::exception & e) {
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
