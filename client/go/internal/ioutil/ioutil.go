// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// File utilities.
// Author: bratseth

package ioutil

import (
	"bytes"
	"encoding/json"
	"errors"
	"io"
	"os"
	"path/filepath"
	"reflect"
	"strings"

	"github.com/fxamacker/cbor/v2"
)

// cborDecMode is configured to decode maps with string keys for JSON compatibility.
var cborDecMode cbor.DecMode

func init() {
	var err error
	cborDecMode, err = cbor.DecOptions{
		DefaultMapType: reflect.TypeOf(map[string]interface{}(nil)),
	}.DecMode()
	if err != nil {
		panic("failed to initialize CBOR decoder mode: " + err.Error())
	}
}

// Exists returns true if the given path exists.
func Exists(path string) bool {
	info, err := os.Stat(path)
	return !errors.Is(err, os.ErrNotExist) && info != nil
}

// IsDir returns true if the given path points to an existing directory.
func IsDir(path string) bool {
	info, err := os.Stat(path)
	return !errors.Is(err, os.ErrNotExist) && info != nil && info.IsDir()
}

// IsFile returns true if the given path points to an existing regular file.
func IsFile(path string) bool {
	info, err := os.Stat(path)
	return !errors.Is(err, os.ErrNotExist) && info != nil && info.Mode().IsRegular()
}

// IsExecutable returns true if the given path points to an executable file.
func IsExecutable(path string) bool {
	info, err := os.Stat(path)
	return !errors.Is(err, os.ErrNotExist) &&
		info != nil &&
		info.Mode().IsRegular() &&
		((int(info.Mode()) & 0o111) == 0o111)
}

// ReaderToString Returns the content of reader as a string. Read errors are ignored.
func ReaderToString(reader io.Reader) string {
	var buffer strings.Builder
	io.Copy(&buffer, reader)
	return buffer.String()
}

// ReaderToBytes returns the content of a reader as a byte array. Read errors are ignored.
func ReaderToBytes(reader io.Reader) []byte {
	var buffer bytes.Buffer
	buffer.ReadFrom(reader)
	return buffer.Bytes()
}

// ReaderToJSON returns the contents of reader as indented JSON. Read errors are ignored.
func ReaderToJSON(reader io.Reader) string {
	bodyBytes, _ := io.ReadAll(reader)
	var prettyJSON bytes.Buffer
	parseError := json.Indent(&prettyJSON, bodyBytes, "", "    ")
	if parseError != nil { // Not JSON: Print plainly
		return string(bodyBytes)
	}
	return prettyJSON.String()
}

// StringToJSON returns string s as indented JSON.
func StringToJSON(s string) string { return ReaderToJSON(strings.NewReader(s)) }

// CBORToJSON converts CBOR data to indented JSON string.
func CBORToJSON(data []byte) (string, error) {
	var v interface{}
	if err := cborDecMode.Unmarshal(data, &v); err != nil {
		return "", err
	}
	jsonBytes, err := json.MarshalIndent(v, "", "    ")
	if err != nil {
		return "", err
	}
	return string(jsonBytes), nil
}

// CBORToJSONCompact converts CBOR data to compact JSON string.
func CBORToJSONCompact(data []byte) (string, error) {
	var v interface{}
	if err := cborDecMode.Unmarshal(data, &v); err != nil {
		return "", err
	}
	jsonBytes, err := json.Marshal(v)
	if err != nil {
		return "", err
	}
	return string(jsonBytes), nil
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
