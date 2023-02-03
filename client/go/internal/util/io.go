// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// File utilities.
// Author: bratseth

package util

import (
	"bytes"
	"encoding/json"
	"errors"
	"io"
	"os"
	"path/filepath"
	"strings"
)

// Returns true if the given path exists
func PathExists(path string) bool {
	info, err := os.Stat(path)
	return !errors.Is(err, os.ErrNotExist) && info != nil
}

// Returns true if the given path points to an existing directory
func IsDirectory(path string) bool {
	info, err := os.Stat(path)
	return !errors.Is(err, os.ErrNotExist) && info != nil && info.IsDir()
}

// Returns true if the given path points to an existing file
func IsRegularFile(path string) bool {
	info, err := os.Stat(path)
	return !errors.Is(err, os.ErrNotExist) && info != nil && info.Mode().IsRegular()
}

// Returns true if the given path points to an executable
func IsExecutableFile(path string) bool {
	info, err := os.Stat(path)
	return !errors.Is(err, os.ErrNotExist) &&
		info != nil &&
		info.Mode().IsRegular() &&
		((int(info.Mode()) & 0111) == 0111)
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
	bodyBytes, _ := io.ReadAll(reader)
	var prettyJSON bytes.Buffer
	parseError := json.Indent(&prettyJSON, bodyBytes, "", "    ")
	if parseError != nil { // Not JSON: Print plainly
		return string(bodyBytes)
	}
	return prettyJSON.String()
}

// AtomicWriteFile atomically writes data to filename.
func AtomicWriteFile(filename string, data []byte) error {
	dir := filepath.Dir(filename)
	tmpFile, err := os.CreateTemp(dir, "vespa")
	if err != nil {
		return err
	}
	defer os.Remove(tmpFile.Name())
	if _, err := tmpFile.Write(data); err != nil {
		return err
	}
	if err := tmpFile.Close(); err != nil {
		return err
	}
	return os.Rename(tmpFile.Name(), filename)
}
