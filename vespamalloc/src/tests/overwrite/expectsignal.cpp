// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/process/process.h>
#include <sys/wait.h>

using namespace vespalib;

int main(int argc, char **argv) {

    if (argc != 3) {
        fprintf(stderr, "[ERROR] expected argc to be %d (it was %d)\n", 3, argc);
        return 1;
    }

    int retval = strtol(argv[1], nullptr, 0);

    fprintf(stderr, "argc=%d : Running '%s' expecting signal %d\n", argc, argv[2], retval);

    Process cmd(argv[2]);
    for (std::string line = cmd.read_line(); !(line.empty() && cmd.eof()); line = cmd.read_line()) {
        fprintf(stdout, "%s\n", line.c_str());
    }
    int exitCode = cmd.join();

    if (exitCode == 65535) {
        fprintf(stderr, "[ERROR] child killed (timeout)\n");
    } else if (WIFEXITED(exitCode)) {
        fprintf(stderr, "child terminated normally with exit code %u\n", WEXITSTATUS(exitCode));
    } else if (WIFSIGNALED(exitCode)) {
        fprintf(stderr, "child terminated by signal %u\n", WTERMSIG(exitCode));
        if (WCOREDUMP(exitCode)) {
            fprintf(stderr, "[WARNING] child dumped core\n");
        }
    } else {
        fprintf(stderr, "[WARNING] strange exit code: %u\n", exitCode);
    }

    if ((exitCode & 0x7f) != retval) {
        fprintf(stderr, "[ERROR] expected exit code lower 7 bits to be %d (it was %d)\n", retval, (exitCode & 0x7f));
        return 1;
    }
    return 0;
}
