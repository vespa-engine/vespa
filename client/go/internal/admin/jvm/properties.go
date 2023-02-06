// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package jvm

import (
	"bytes"
	"fmt"
	"os"
	"sort"
	"strings"

	"github.com/vespa-engine/vespa/client/go/internal/admin/trace"
	"github.com/vespa-engine/vespa/client/go/internal/util"
)

// quote as specified in JDK source file java.base/share/classes/java/util/Properties.java
func propQuote(s string, buf *bytes.Buffer) {
	inKey := true
	inVal := false
	for _, ch := range s {
		needQ := false
		switch {
		case ch == ' ':
			needQ = !inVal
		case ch == ':':
			needQ = inKey
		case ch < ' ':
			needQ = true
		case ch > '~':
			needQ = true
		case ch == '\\':
			buf.WriteString("\\")
		case ch == '=':
			inKey = false
		default:
			inVal = !inKey
		}
		if needQ {
			fmt.Fprintf(buf, "\\u%04X", ch)
		} else {
			buf.WriteRune(ch)
		}
	}
}

func envAsProperties(envv []string) []byte {
	suppress := map[string]bool{
		"_":                      true,
		"HISTCONTROL":            true,
		"HISTSIZE":               true,
		"IFS":                    true,
		"LESSOPEN":               true,
		"LOADEDMODULES":          true,
		"LS_COLORS":              true,
		"MAIL":                   true,
		"MODULEPATH":             true,
		"MODULEPATH_modshare":    true,
		"MODULESHOME":            true,
		"MODULES_CMD":            true,
		"MODULES_RUN_QUARANTINE": true,
		"OLDPWD":                 true,
		"PCP_DIR":                true,
		"PS1":                    true,
		"PWD":                    true,
		"SHLVL":                  true,
		"SSH_AUTH_SOCK":          true,
		"SSH_CLIENT":             true,
		"SSH_CONNECTION":         true,
		"SSH_TTY":                true,
		"S_COLORS":               true,
		"which_declare":          true,
		"":                       true,
	}
	var buf bytes.Buffer
	buf.WriteString("# properties converted from environment variables\n")
	sort.Strings(envv)
	for _, env := range envv {
		parts := strings.Split(env, "=")
		if len(parts) >= 2 {
			varName := parts[0]
			if suppress[varName] || strings.Contains(varName, "%%") {
				continue
			}
			if strings.Contains(env, "\n") {
				continue
			}
			propQuote(env, &buf)
			buf.WriteRune('\n')
		} else {
			trace.Warning("environment value without '=':", env)
		}
	}
	return buf.Bytes()
}

func writeEnvAsProperties(envv []string, propsFile string) {
	if propsFile == "" {
		panic("missing propsFile")
	}
	trace.Trace("write props file:", propsFile)
	err := os.WriteFile(propsFile, envAsProperties(envv), 0600)
	if err != nil {
		util.JustExitWith(err)
	}
}
