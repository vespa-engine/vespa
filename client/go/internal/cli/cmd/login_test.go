// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package cmd

import (
	"errors"
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestErrLoginDenied(t *testing.T) {
	const (
		ssoRequired     = "Your organization require SSO for Vespa Cloud access"
		disposableEmail = "Disposable email addresses are not allowed for Vespa Cloud access"
	)
	tests := []struct {
		reason string
		want   string // empty when the error is not a denied login
	}{
		{"600", ssoRequired},
		{"601", disposableEmail},
		{"601: Sign-up with disposable email addresses is not allowed.", disposableEmail},
		{"602: Some reason this version does not know.", "login error: Some reason this version does not know."},
		{"602", ""},
		{"User did not consent", ""},
		{"6000", ""},
		{"", ""},
	}
	for _, tt := range tests {
		err := errLoginDenied(errors.New(tt.reason))
		if tt.want == "" {
			assert.Nil(t, err, "reason %q", tt.reason)
		} else if assert.NotNil(t, err, "reason %q", tt.reason) {
			assert.Equal(t, tt.want, err.Error(), "reason %q", tt.reason)
		}
	}
}
