// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// File utilities.
// Author: bratseth

package util

import (
	"bytes"
	"encoding/json"
	"errors"
	"io"
	"os"
	"strings"
)

// Returns true if the given path exists
func PathExists(path string) bool {
	_, err := os.Stat(path)
	return !errors.Is(err, os.ErrNotExist)
}

// Returns true is the given path points to an existing directory
func IsDirectory(path string) bool {
	info, err := os.Stat(path)
	return !errors.Is(err, os.ErrNotExist) && info.IsDir()
}

// Returns the content of a reader as a string
func ReaderToString(reader io.Reader) string {
	var buffer strings.Builder
	io.Copy(&buffer, reader)
	return buffer.String()
}

// Returns the content of a reader as a byte array
func ReaderToBytes(reader io.Reader) []byte {
	var buffer bytes.Buffer
	buffer.ReadFrom(reader)
	return buffer.Bytes()
}

// Returns the contents of reader as indented JSON
func ReaderToJSON(reader io.Reader) string {
	bodyBytes := ReaderToBytes(reader)
	var prettyJSON bytes.Buffer
	parseError := json.Indent(&prettyJSON, bodyBytes, "", "    ")
	if parseError != nil { // Not JSON: Print plainly
		return string(bodyBytes)
	}
	return string(prettyJSON.Bytes())
}
