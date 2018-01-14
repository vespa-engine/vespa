// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <stdlib.h>
#include <stdio.h>
#include <unistd.h>
#include <pwd.h>
#include <grp.h>

/**
 * small utility to use instead of "su" when we want to just
 * switch to the vespa user without any more fuss
 **/

int main(int argc, char** argv)
{
    if (argc < 2) {
        fprintf(stderr, "missing arguments, usage: vespa-run-as-vespa-user <cmd> [args ...]\n");
        exit(1);
    }
    const char *username = getenv("VESPA_USER");
    if (username == nullptr) {
        username = "yahoo";
    }
    struct passwd *p = getpwnam(username);
    if (p == nullptr) {
        fprintf(stderr, "FATAL error: user '%s' missing in passwd file\n", username);
        exit(1);
    }
    gid_t g = p->pw_gid;
    uid_t u = p->pw_uid;

    if (setgid(g) != 0) {
        perror("FATAL error: could not change group id");
        exit(1);
    }
    size_t listsize = 1;
    gid_t grouplist[1] = { g };
    if (setgroups(listsize, grouplist) != 0) {
        perror("FATAL error: could not setgroups");
        exit(1);
    }
    if (setuid(u) != 0) {
        perror("FATAL error: could not change user id");
        exit(1);
    }
    execvp(argv[1], &argv[1]);
    perror("FATAL error: execvp failed");
    exit(1);
}
