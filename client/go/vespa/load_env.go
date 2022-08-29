// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// load default environment variables (from $VESPA_HOME/conf/vespa/default-env.txt)
// Author: arnej

package vespa

import (
	"bufio"
	"fmt"
	"os"
	"os/user"
	"strings"
)

// backwards-compatible parsing of default-env.txt
func LoadDefaultEnv() error {
	return loadDefaultEnvTo(new(osEnvReceiver))
}

// parse default-env.txt, then dump export statements for "sh" to stdout
func ExportDefaultEnvToSh() error {
	holder := newShellEnvExporter()
	err := loadDefaultEnvTo(holder)
	holder.dump()
	return err
}

// Which user should vespa services run as?  If current user is root,
// we want to change to some non-privileged user.
// Should be run after LoadDefaultEnv() which possibly loads VESPA_USER
func FindVespaUser() string {
	uName := os.Getenv("VESPA_USER")
	if uName != "" {
		// no check here, assume valid
		return uName
	}
	if os.Getuid() == 0 {
		u, err := user.Lookup("vespa")
		if err == nil {
			uName = u.Username
		} else {
			u, err = user.Lookup("nobody")
			if err == nil {
				uName = u.Username
			}
		}
	}
	if uName == "" {
		u, err := user.Current()
		if err == nil {
			uName = u.Username
		}
	}
	if uName != "" {
		os.Setenv("VESPA_USER", uName)
	}
	return uName
}

type loadEnvReceiver interface {
	fallbackVar(varName, varVal string)
	overrideVar(varName, varVal string)
	unsetVar(varName string)
}

type osEnvReceiver struct {
}

func (p *osEnvReceiver) fallbackVar(varName, varVal string) {
	if os.Getenv(varName) == "" {
		os.Setenv(varName, varVal)
	}
}
func (p *osEnvReceiver) overrideVar(varName, varVal string) {
	os.Setenv(varName, varVal)
}
func (p *osEnvReceiver) unsetVar(varName string) {
	os.Unsetenv(varName)
}

func loadDefaultEnvTo(r loadEnvReceiver) error {
	const defEnvTxt = "/conf/vespa/default-env.txt"
	vespaHome := FindHome()
	f, err := os.Open(vespaHome + defEnvTxt)
	if err != nil {
		return err
	}
	defer f.Close()
	scanner := bufio.NewScanner(f)
	for scanner.Scan() {
		line := scanner.Text()
		if strings.HasPrefix(line, "#") {
			continue
		}
		fields := nSpacedFields(line, 3)
		action := fields[0]
		if action == "" {
			continue
		}
		varName := fields[1]
		varVal := fields[2]
		if !isValidShellVariableName(varName) {
			err = fmt.Errorf("Not a valid environment variable name: '%s'", varName)
			continue
		}
		if strings.HasPrefix(varVal, `"`) && strings.HasSuffix(varVal, `"`) {
			varVal = varVal[1 : len(varVal)-1]
		}
		switch action {
		case "override":
			r.overrideVar(varName, varVal)
		case "fallback":
			r.fallbackVar(varName, varVal)
		case "unset":
			r.unsetVar(varName)
		default:
			err = fmt.Errorf("unknown action '%s'", action)
		}
	}
	if err == nil {
		err = scanner.Err()
	}
	return err
}

// borrowed some code from strings.Fields() implementation:
func nSpacedFields(s string, n int) []string {
	var asciiSpace = [256]uint8{'\t': 1, '\n': 1, '\v': 1, '\f': 1, '\r': 1, ' ': 1}
	a := make([]string, n)
	na := 0
	fieldStart := 0
	i := 0
	// Skip spaces in the front of the input.
	for i < len(s) && asciiSpace[s[i]] != 0 {
		i++
	}
	fieldStart = i
	for i < len(s) && na+1 < n {
		if asciiSpace[s[i]] == 0 {
			i++
			continue
		}
		a[na] = s[fieldStart:i]
		na++
		i++
		// Skip spaces in between fields.
		for i < len(s) && asciiSpace[s[i]] != 0 {
			i++
		}
		fieldStart = i
	}
	// ignore trailing spaces
	for i = len(s); i > fieldStart && asciiSpace[s[i-1]] != 0; i-- {
	}
	a[na] = s[fieldStart:i]
	return a
}

// pretty strict for now, can be more lenient if needed
func isValidShellVariableName(s string) bool {
	for i := 0; i < len(s); i++ {
		b := s[i]
		switch {
		case (b >= 'A' && b <= 'Z'): // ok
		case (b >= 'a' && b <= 'z'): // ok
		case (b >= '0' && b <= '9'): // ok
		case b == '_': // ok
		default:
			return false
		}
	}
	return len(s) > 0
}

type shellEnvExporter struct {
	exportVars map[string]string
	unsetVars  map[string]string
}

func newShellEnvExporter() *shellEnvExporter {
	return &shellEnvExporter{
		exportVars: make(map[string]string),
		unsetVars:  make(map[string]string),
	}
}
func (p *shellEnvExporter) fallbackVar(varName, varVal string) {
	if os.Getenv(varName) == "" || p.unsetVars[varName] != "" {
		delete(p.unsetVars, varName)
		p.exportVars[varName] = shellQuote(varVal)
	}
}
func (p *shellEnvExporter) overrideVar(varName, varVal string) {
	delete(p.unsetVars, varName)
	p.exportVars[varName] = shellQuote(varVal)
}
func (p *shellEnvExporter) unsetVar(varName string) {
	delete(p.exportVars, varName)
	p.unsetVars[varName] = "unset"
}

func shellQuote(s string) string {
	l := 0
	nq := false
	for _, ch := range s {
		switch {
		case (ch >= 'A' && ch <= 'Z') ||
			(ch >= 'a' && ch <= 'z') ||
			(ch >= '0' && ch <= '9'):
			l++
		case ch == '_' || ch == ' ':
			l++
			nq = true
		case ch == '\'' || ch == '\\':
			l = l + 4
			nq = true
		default:
			l++
			nq = true
		}
	}
	if nq {
		l = l + 2
	}
	res := make([]rune, l)
	i := 0
	if nq {
		res[i] = '\''
		i++
	}
	for _, ch := range s {
		if ch == '\'' || ch == '\\' {
			res[i] = '\''
			i++
			res[i] = '\\'
			i++
			res[i] = ch
			i++
			res[i] = '\''
		} else {
			res[i] = ch
		}
		i++
	}
	if nq {
		res[i] = '\''
		i++
	}
	if i != l {
		err := fmt.Errorf("expected length %d but was %d", l, i)
		panic(err)
	}
	return string(res)
}

func (p *shellEnvExporter) dump() {
	for vn, vv := range p.exportVars {
		fmt.Printf("%s=%s\n", vn, vv)
		fmt.Printf("export %s\n", vn)
	}
	for vn, _ := range p.unsetVars {
		fmt.Printf("unset %s\n", vn)
	}
}
