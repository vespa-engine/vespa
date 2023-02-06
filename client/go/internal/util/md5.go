// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package util

import (
	"crypto/md5"
	"fmt"
	"io"
)

func Md5Hex(text string) string {
	hasher := md5.New()
	io.WriteString(hasher, text)
	hash := hasher.Sum(nil)
	return fmt.Sprintf("%x", hash)
}
