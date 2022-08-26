// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// load default environment variables (from $VESPA_HOME/conf/vespa/default-env.txt)
// Author: arnej

package vespa

import (
	"bufio"
	"fmt"
	"os"
	"strings"
)

// backwards-compatible parsing of default-env.txt
func LoadDefaultEnv() error {
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
			os.Setenv(varName, varVal)
		case "fallback":
			if os.Getenv(varName) == "" {
				os.Setenv(varName, varVal)
			}
		case "unset":
			os.Unsetenv(varName)
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
