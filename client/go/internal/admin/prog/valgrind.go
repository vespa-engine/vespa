// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package prog

import (
	"fmt"
	"os"
	"strings"

	"github.com/vespa-engine/vespa/client/go/internal/admin/envvars"
	"github.com/vespa-engine/vespa/client/go/internal/admin/trace"
	"github.com/vespa-engine/vespa/client/go/internal/osutil"
	"github.com/vespa-engine/vespa/client/go/internal/vespa"
)

const (
	VALGRIND_PROG = "valgrind"
)

func (p *Spec) ConfigureValgrind() {
	p.shouldUseValgrind = false
	p.shouldUseCallgrind = false
	if p.MatchesListEnv(envvars.VESPA_USE_VALGRIND) {
		trace.Trace("using valgrind as", p.Program, "has basename in", envvars.VESPA_USE_VALGRIND)
		backticks := osutil.BackTicksWithStderr
		out, err := backticks.Run(VALGRIND_PROG, "--help")
		if err != nil {
			trace.Trace("trial run of valgrind fails:", err, "=>", out)
			return
		}
		if opts := p.Getenv(envvars.VESPA_VALGRIND_OPT); strings.Contains(opts, "callgrind") {
			p.shouldUseCallgrind = true
		}
		p.shouldUseValgrind = true
	}
}

func (p *Spec) valgrindOptions() []string {
	env := p.Getenv(envvars.VESPA_VALGRIND_OPT)
	if env != "" {
		return strings.Fields(env)
	}
	result := []string{
		"--num-callers=32",
		"--run-libc-freeres=yes",
		"--track-origins=yes",
		"--freelist-vol=1000000000",
		"--leak-check=full",
		"--show-reachable=yes",
	}
	result = addValgrindSuppression(result, "etc/vespa/valgrind-suppressions.txt")
	result = addValgrindSuppression(result, "etc/vespa/suppressions.txt")
	return result
}

func addValgrindSuppression(r []string, fn string) []string {
	existsOk, fileName := vespa.HasFileUnderVespaHome(fn)
	if existsOk {
		r = append(r, fmt.Sprintf("--suppressions=%s", fileName))
	}
	return r
}

func (p *Spec) valgrindLogOption() string {
	return fmt.Sprintf("--log-file=%s/var/tmp/valgrind.%s.log.%d", vespa.FindHome(), p.BaseName, os.Getpid())
}

func (p *Spec) prependValgrind(args []string) []string {
	result := make([]string, 0, 15+len(args))
	result = append(result, VALGRIND_PROG)
	for _, arg := range p.valgrindOptions() {
		result = append(result, arg)
	}
	result = append(result, p.valgrindLogOption())
	for _, arg := range args {
		result = append(result, arg)
	}
	return result
}
