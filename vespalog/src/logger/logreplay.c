// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <sys/types.h>
#include <sys/time.h>
#include <stdio.h>
#include <math.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

int
main(int argc, char **argv)
{
    char line[10000];
    
    double delta = 0;

    if (argc != 1) {
        fprintf(stderr, "Usage: %s < <vespa.log>\n"
                "Replays a vespa log file with the same timing delta between each log message.\n"
                "Reprints the log messages without timestamps.\n\n",
                argv[0]);
        return EXIT_FAILURE;
    }
    
    while (fgets(line, sizeof line, stdin)) {
        struct timeval tv;
        char *s;
        double log_time;
        double now;
        double delay;

        gettimeofday(&tv, NULL);
        now = tv.tv_sec + 1e-6 * tv.tv_usec;
        
        log_time = strtod(line, NULL);
        if (!delta) {
            delta = now - log_time;
        }

        delay = log_time + delta - now;
        if (delay > 0) {
            tv.tv_sec = floor(delay);
            tv.tv_usec = 1e6 * (delay - floor(delay));
            /* fprintf(stderr, "Sleeping for %.3fs\n", delay); */
            select(0, NULL, NULL, NULL, &tv);
        }

        s = strchr(line, '\t');
        if (s) {
            s = s + 1;
        } else {
            s = line;
        }
        fwrite(s, strlen(s), 1, stdout);
        fflush(stdout);
    }
    return EXIT_SUCCESS;
}
