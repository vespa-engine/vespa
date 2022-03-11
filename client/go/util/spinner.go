// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package util

import (
	"io"
	"os"
	"strings"
	"time"

	"github.com/briandowns/spinner"
	"github.com/mattn/go-isatty"
)

// Spinner writes message to writer w and executes function fn. While fn is running a spinning animation will be
// displayed after message.
func Spinner(w io.Writer, message string, fn func() error) error {
	s := spinner.New(spinner.CharSets[11], 100*time.Millisecond, spinner.WithWriter(w))
	if err := s.Color("blue", "bold"); err != nil {
		return err
	}
	if !strings.HasSuffix(message, " ") {
		message += " "
	}
	s.Prefix = message
	s.FinalMSG = "\r" + message + "done\n"
	useSpinner := useSpinner(w)
	if useSpinner { // spinner package does this check too, but it's hardcoded to check os.Stdout :(
		s.Start()
	}
	err := fn()
	if err != nil {
		s.FinalMSG = "\r" + message + "failed\n"
	}
	if useSpinner {
		s.Stop()
	}
	return err
}

func useSpinner(w io.Writer) bool {
	if !isTerminal(w) {
		return false
	}
	// Explicitly disable spinner for Screwdriver. It emulates a tty but
	// \r result in a newline, and output gets truncated.
	if _, screwdriver := os.LookupEnv("SCREWDRIVER"); screwdriver {
		return false
	}
	return true
}

func isTerminal(w io.Writer) bool {
	f, ok := w.(*os.File)
	return ok && isatty.IsTerminal(f.Fd())
}
