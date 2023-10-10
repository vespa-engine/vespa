// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <unistd.h>
#include <stdio.h>
#include <string.h>

int main() {
    char c[2] = "x";
    while (fread(&c, 1, 1, stdin) == 1) {
        const char *out = c;
        switch (c[0]) {
        case '<': out = "&lt;";   break;
        case '>': out = "&gt;";   break;
        case '&': out = "&amp;";  break;
        case '"': out = "&quot;"; break;
        }
        fwrite(out, 1, strlen(out), stdout);
    }
    return 0;
}
