// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/text/utf8.h>

void printCodepoint(unsigned long codepoint)
{
    vespalib::string data;
    vespalib::Utf8Writer w(data);
    w.putChar(codepoint);
    printf("URL encoding of codepoint U+%04lX entity &#%lu; string value '%s' is:\n",
           codepoint, codepoint, data.c_str());

    for (size_t i = 0; i < data.size(); ++i) {
        unsigned char byte = data[i];
        printf("%%%02X", byte);
    }
    printf("\n");
}

int main(int argc, char **argv)
{
    if (argc == 2) {
        unsigned long codepoint = 0;
        if (sscanf(argv[1], "U+%lx", &codepoint) == 1) {
            printCodepoint(codepoint);
            return 0;
        } else if (sscanf(argv[1], "\\u%lx", &codepoint) == 1) {
            printCodepoint(codepoint);
            return 0;
        }
    }
    fprintf(stderr, "Usage: %s U+XXXX\n", argv[0]);
    return 1;
}
