package version

import (
	"fmt"
	"strconv"
	"strings"
)

// Version represents a semantic version number.
type Version struct {
	Major int
	Minor int
	Patch int
}

func (v Version) String() string { return fmt.Sprintf("%d.%d.%d", v.Major, v.Minor, v.Patch) }

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
	return 0
}

// Less returns true if v1 is lower than v2.
func (v1 Version) Less(v2 Version) bool { return v1.Compare(v2) < 0 }

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
	patch, err := strconv.Atoi(parts[2])
	if err != nil {
		return Version{}, fmt.Errorf("invalid patch version: %s", parts[2])
	}
	return Version{Major: major, Minor: minor, Patch: patch}, nil
}
