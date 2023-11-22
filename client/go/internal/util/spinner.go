// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package util

import (
	"io"
	"strings"
	"time"

	"github.com/briandowns/spinner"
)

// Spinner writes message to writer w and executes function fn. While fn is running a spinning animation will be
// displayed after message.
func Spinner(w io.Writer, message string, fn func() error) error {
	s := spinner.New(spinner.CharSets[11], 100*time.Millisecond, spinner.WithWriter(w))
	// Cursor is hidden by default. Hiding cursor requires Stop() to be called to restore cursor (i.e. if the process is
	// interrupted), however we don't want to bother with a signal handler just for this
	s.HideCursor = false
	if err := s.Color("blue", "bold"); err != nil {
		return err
	}
	if !strings.HasSuffix(message, " ") {
		message += " "
	}
	s.Prefix = message
	s.FinalMSG = "\r" + message + "done\n"
	s.Start()
	err := fn()
	if err != nil {
		s.FinalMSG = "\r" + message + "failed\n"
	}
	s.Stop()
	return err
}
