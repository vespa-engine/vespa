#include <vespa/vespalib/util/exception.h>

using vespalib::SilenceUncaughtException;

int main(int argc, char *argv[]) {
    if (argc != 2) {
        return 77;
    }
    std::runtime_error e("caught or not");
    if (strcmp("uncaught", argv[1]) == 0) {
        throw e;
    } else if (strcmp("silenced_and_caught", argv[1]) == 0) {
        try {
            SilenceUncaughtException silenced(e);
            throw e;
        } catch (const std::runtime_error & ) {
            printf("caught it\n");
        }
    } else if (strcmp("silenced_and_uncaught", argv[1]) == 0) {
        SilenceUncaughtException silenced(e);
        throw e;
    } else {
        return 55;
    }

    return 0;
}
