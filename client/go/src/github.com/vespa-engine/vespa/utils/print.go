// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Print functions for color-coded text.
// Author: bratseth

package utils

import (
    "fmt"
)

func Error(messages ...string) {
    print("\033[31m", messages)
}

func Success(messages ...string) {
    print("\033[32m", messages)
}

func Detail(messages ...string) {
    print("\033[33m", messages)
}

func print(prefix string, messages []string) {
    fmt.Print(prefix)
    for i := 0; i < len(messages); i++ {
        fmt.Print(messages[i])
        fmt.Print(" ")
    }
    fmt.Println("")
}
