// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package util

import (
	"os"
	"time"

	"github.com/briandowns/spinner"
	"github.com/pkg/errors"
)

const (
	spinnerTextEllipsis = "..."
	spinnerTextDone     = "done"
	spinnerTextFailed   = "failed"
	spinnerColor        = "blue"
)

var messages = os.Stderr

func Spinner(text string, fn func() error) error {
	initialMsg := text + spinnerTextEllipsis + " "
	doneMsg := initialMsg + spinnerTextDone + "\n"
	failMsg := initialMsg + spinnerTextFailed + "\n"

	return loading(initialMsg, doneMsg, failMsg, fn)
}

func loading(initialMsg, doneMsg, failMsg string, fn func() error) error {
	done := make(chan struct{})
	errc := make(chan error)
	go func() {
		defer close(done)

		s := spinner.New(spinner.CharSets[11], 100*time.Millisecond, spinner.WithWriter(messages))
		s.Prefix = initialMsg
		s.FinalMSG = doneMsg
		s.HideCursor = true
		s.Writer = messages

		if err := s.Color(spinnerColor); err != nil {
			panic(Error(err, "failed setting spinner color"))
		}

		s.Start()
		err := <-errc
		if err != nil {
			s.FinalMSG = failMsg
		}

		s.Stop()
	}()

	err := fn()
	errc <- err
	<-done
	return err
}

func Error(e error, message string) error {
	return errors.Wrap(e, message)
}
