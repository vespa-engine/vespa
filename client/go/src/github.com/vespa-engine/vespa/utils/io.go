// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// File utilities.
// Author: bratseth

package utils

import (
    "errors"
    "os"
)

// Returns true if the given path exists
func PathExists(path string) bool {
    _, err := os.Stat(path)
    return ! errors.Is(err, os.ErrNotExist)
}

// Returns true is the given path points to an existing directory
func IsDirectory(path string) bool {
    info, err := os.Stat(path)
    return ! errors.Is(err, os.ErrNotExist) && info.IsDir()
}
