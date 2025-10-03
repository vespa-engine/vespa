// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package cmd

import (
	"testing"
)

func TestNewCountCmd(t *testing.T) {
	cli := &CLI{}
	cmd := newCountCmd(cli)

	if cmd.Use != "count" {
		t.Errorf("Expected command use to be 'count', got '%s'", cmd.Use)
	}

	if cmd.Short == "" {
		t.Error("Expected command to have a short description")
	}

	if cmd.Long == "" {
		t.Error("Expected command to have a long description")
	}

	// Test that the command has the expected flags
	flags := cmd.Flags()

	if flags.Lookup("document-type") == nil {
		t.Error("Expected --document-type flag to be defined")
	}

	if flags.Lookup("verbose") == nil {
		t.Error("Expected --verbose flag to be defined")
	}

	// Test flag shorthands
	if flags.ShorthandLookup("d") == nil {
		t.Error("Expected -d shorthand for --document-type flag")
	}

	if flags.ShorthandLookup("v") == nil {
		t.Error("Expected -v shorthand for --verbose flag")
	}

	// Test that removed flags are not present
	if flags.Lookup("format") != nil {
		t.Error("Expected --format flag to be removed (simplified command)")
	}

	if flags.Lookup("timeout") != nil {
		t.Error("Expected --timeout flag to be removed (simplified command)")
	}

	if flags.Lookup("header") != nil {
		t.Error("Expected --header flag to be removed (simplified command)")
	}
}
