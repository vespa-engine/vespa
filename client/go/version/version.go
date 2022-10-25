// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package version

import (
	"fmt"
	"strconv"
	"strings"

	"github.com/vespa-engine/vespa/client/go/util"
)

// Version represents a semantic version number.
type Version struct {
	Major int
	Minor int
	Patch int
	Label string
}

// IsZero returns whether v is the zero version, 0.0.0.
func (v Version) IsZero() bool {
	return v.Major == 0 && v.Minor == 0 && v.Patch == 0
}

func (v Version) String() string {
	var sb strings.Builder
	sb.WriteString(strconv.Itoa(v.Major))
	sb.WriteRune('.')
	sb.WriteString(strconv.Itoa(v.Minor))
	sb.WriteRune('.')
	sb.WriteString(strconv.Itoa(v.Patch))
	if v.Label != "" {
		sb.WriteRune('-')
		sb.WriteString(v.Label)
	}
	return sb.String()
}

// Compare returns a positive integer if v1 is greater than v2, a negative integer if v1 is less than v2 and zero if they
// are equal.
func (v1 Version) Compare(v2 Version) int {
	result := v1.Major - v2.Major
	if result != 0 {
		return result
	}
	result = v1.Minor - v2.Minor
	if result != 0 {
		return result
	}
	result = v1.Patch - v2.Patch
	if result != 0 {
		return result
	}
	// Version without label always sorts first
	if v1.Label == "" && v2.Label != "" {
		return 1
	}
	if v1.Label != "" && v2.Label == "" {
		return -1
	}
	if v1.Label > v2.Label {
		return 1
	}
	if v1.Label < v2.Label {
		return -1
	}
	return 0
}

// Less returns true if v1 is lower than v2.
func (v1 Version) Less(v2 Version) bool { return v1.Compare(v2) < 0 }

// MustParse is like Parse, but panics if s cannot be parsed.
func MustParse(s string) Version {
	v, err := Parse(s)
	if err != nil {
		util.JustExitWith(err)
	}
	return v
}

// Parse parses a semantic version number from string s.
func Parse(s string) (Version, error) {
	if len(s) > 0 && s[0] == 'v' {
		s = s[1:] // Trim v prefix
	}
	parts := strings.Split(s, ".")
	if len(parts) != 3 {
		return Version{}, fmt.Errorf("invalid version number: %s", s)
	}
	major, err := strconv.Atoi(parts[0])
	if err != nil {
		return Version{}, fmt.Errorf("invalid major version: %s", parts[0])
	}
	minor, err := strconv.Atoi(parts[1])
	if err != nil {
		return Version{}, fmt.Errorf("invalid minor version: %s", parts[1])
	}
	parts2 := strings.SplitN(parts[2], "-", 2)
	patch, err := strconv.Atoi(parts2[0])
	if err != nil {
		return Version{}, fmt.Errorf("invalid patch version: %s", parts[2])
	}
	v := Version{Major: major, Minor: minor, Patch: patch}
	if len(parts2) > 1 {
		v.Label = parts2[1]
	}
	return v, nil
}
