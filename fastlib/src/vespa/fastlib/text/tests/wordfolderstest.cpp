// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "wordfolderstest.h"

int main(int, char **) {
    WordFoldersTest t;
    t.SetStream(&std::cout);
    t.Run();
    return t.Report();
}
