// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa logfmt command
// Author: arnej

package logfmt

import (
	"fmt"
	"os"
)

// options designed for compatibility with perl version of vespa-logfmt

type Options struct {
	ShowFields        flagValueForShow
	ShowLevels        flagValueForLevel
	OnlyHostname      string
	OnlyPid           string
	OnlyService       string
	OnlyInternal      bool
	FollowTail        bool
	DequoteNewlines   bool
	TruncateService   bool
	TruncateComponent bool
	ComponentFilter   regexFlag
	MessageFilter     regexFlag
	Format            OutputFormat
}

func NewOptions() (ret Options) {
	ret.ShowLevels.levels = defaultLevelFlags()
	ret.ShowFields.shown = defaultShowFlags()
	return
}

func (o *Options) showField(field string) bool {
	return o.ShowFields.shown[field]
}

func (o *Options) showLevel(level string) bool {
	rv, ok := o.ShowLevels.levels[level]
	if !ok {
		o.ShowLevels.levels[level] = true
		fmt.Fprintf(os.Stderr, "Warnings: unknown level '%s' in input\n", level)
		return true
	}
	return rv
}
