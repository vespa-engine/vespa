// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package prog

import (
	"os"
	"strings"

	"github.com/vespa-engine/vespa/client/go/internal/admin/trace"
)

func (p *Spec) Setenv(k, v string) {
	p.Env[k] = v
}

func (p *Spec) Getenv(k string) string {
	if v, ok := p.Env[k]; ok {
		return v
	}
	return os.Getenv(k)
}

func (spec *Spec) EffectiveEnv() []string {
	envMap := make(map[string]string)
	addToMap := func(kv string) {
		for idx, elem := range kv {
			if elem == '=' {
				k := kv[:idx]
				envMap[k] = kv
				return
			}
		}
		trace.Trace("invalid entry in os.Environ():", kv)
		envMap[kv] = kv
	}
	for _, entry := range os.Environ() {
		addToMap(entry)
	}
	for k, v := range spec.Env {
		trace.Trace("add to environment:", k, "=", v)
		envMap[k] = k + "=" + v
	}
	envVec := make([]string, len(envMap))
	idx := 0
	for _, val := range envMap {
		envVec[idx] = val
		idx++
	}
	return envVec
}

func (spec *Spec) considerFallback(varName, varValue string) {
	if spec.Getenv(varName) == "" && varValue != "" {
		spec.Setenv(varName, varValue)
	}
}

func (spec *Spec) considerEnvFallback(targetVar, fallbackVar string) {
	spec.considerFallback(targetVar, spec.Getenv(fallbackVar))
}

func (p *Spec) matchesListEnv(envVarName string) bool {
	return p.matchesListString(p.Getenv(envVarName))
}

func (p *Spec) matchesListString(env string) bool {
	if env == "all" {
		trace.Debug(p.Program, "always matching in:", env)
		return true
	}
	parts := strings.Fields(env)
	for _, part := range parts {
		if p.BaseName == part {
			trace.Debug(p.Program, "has basename matching in:", env)
			return true
		}
		trace.Debug("checking matching:", p.BaseName, "!=", part)
	}
	return false
}

func (p *Spec) valueFromListEnv(envVarName string) string {
	return p.valueFromListString(p.Getenv(envVarName))
}

func (p *Spec) valueFromListString(env string) string {
	parts := strings.Fields(env)
	for _, part := range parts {
		idx := strings.Index(part, "=")
		if idx <= 0 {
			trace.Trace("expected key=value, but got:", part)
			continue
		}
		partName := part[:idx]
		idx++
		partValue := part[idx:]
		if p.BaseName == partName || partName == "all" {
			trace.Debug(p.Program, "has basename matching in:", env)
			return partValue
		}
		trace.Debug("checking matching:", p.BaseName, "!=", part)
	}
	return ""
}
