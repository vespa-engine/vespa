package logfmt

import (
	"fmt"
	"strings"
)

type OutputFormat int

const (
	FormatVespa OutputFormat = iota //default is vespa
	FormatRaw
	FormatJSON
)

func (v *OutputFormat) Type() string {
	return "output format"
}

func (v *OutputFormat) String() string {
	flagNames := []string{
		"vespa",
		"raw",
		"json",
	}
	return flagNames[*v]
}

func (v *OutputFormat) Set(val string) error {
	switch strings.ToLower(val) {
	case "vespa":
		*v = FormatVespa
	case "raw":
		*v = FormatRaw
	case "json":
		*v = FormatJSON
	default:
		return fmt.Errorf("'%s' is not a valid format argument", val)
	}
	return nil
}
