// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "characterclasstest.h"

int character_class_test_app::Main()
{
    character_class_test t("Test for the character_class");
    t.SetStream(&std::cout);
    t.Run();
    return t.Report();
}

FASTOS_MAIN(character_class_test_app)
