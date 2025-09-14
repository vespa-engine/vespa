// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package prog

import (
	"fmt"
	"os"
	"strings"

	"github.com/vespa-engine/vespa/client/go/internal/admin/envvars"
	"github.com/vespa-engine/vespa/client/go/internal/admin/trace"
	"github.com/vespa-engine/vespa/client/go/internal/vespa"
)

func vespaMallocLib(suf string) string {
	prefixes := []string{"lib64", "lib"}
	for _, pre := range prefixes {
		fn := fmt.Sprintf("%s/vespa/malloc/%s", pre, suf)
		existsOk, fileName := vespa.HasFileUnderVespaHome(fn)
		if existsOk {
			trace.Debug("found library:", fileName)
			return fileName
		}
		trace.Debug("bad or missing library:", fn)
	}
	return ""
}

func fileExistsAlsoInLdLibraryPath(path string) (bool, string) {
	// absolute or relative path
	if _, err := os.Stat(path); err == nil {
		return true, path
	}

	// Otherwise, search in LD_LIBRARY_PATH
	ldPath := os.Getenv(envvars.LD_LIBRARY_PATH)
	for _, dir := range strings.Split(ldPath, ":") {
		fullPath := fmt.Sprintf("%s/%s", dir, path)
		if _, err := os.Stat(fullPath); err == nil {
			return true, fullPath
		}
	}

	return false, ""
}

func mimallocLib() string {
	// TODO(johsol) while resolving rpm packages for mimalloc, we check for both libmimalloc.so and libmimalloc.so.2
	//              because currently the symlink libmimalloc.so is only available in devel image.
	//              This is for testing.
	fileNames := []string{"libmimalloc.so", "libmimalloc.so.2"}
	for _, fileName := range fileNames {
		ok, path := fileExistsAlsoInLdLibraryPath(fileName)
		if ok {
			trace.Debug("found library:", path)
			return path
		}
	}
	trace.Debug("missing library:", strings.Join(fileNames, ","))
	trace.Warning("Could not find library mimalloc.")
	return ""
}

func (p *Spec) ConfigureMallocImpl() {
	p.shouldUseMallocImpl = false
	if p.MatchesListEnv(envvars.VESPA_USE_NO_VESPAMALLOC) {
		trace.Trace("use no vespamalloc:", p.BaseName)
		return
	}
	if p.shouldUseValgrind && !p.shouldUseCallgrind {
		trace.Trace("use valgrind, so no vespamalloc:", p.BaseName)
		return
	}
	var useFile string

	// TODO(johsol): Keeping this simple for now defaulting to old behaviour if not mimalloc, but in future switch on
	//               mallocImpl for vespamalloc and variants.
	mallocImpl := p.Getenv(envvars.VESPA_USE_MALLOC_IMPL)
	if mallocImpl == "mimalloc" {
		useFile = mimallocLib()
	}
	if useFile == "" {
		switch {
		case p.MatchesListEnv(envvars.VESPA_USE_VESPAMALLOC_DST):
			useFile = vespaMallocLib("libvespamallocdst16.so")
		case p.MatchesListEnv(envvars.VESPA_USE_VESPAMALLOC_D):
			useFile = vespaMallocLib("libvespamallocd.so")
		case p.MatchesListEnv(envvars.VESPA_USE_VESPAMALLOC):
			useFile = vespaMallocLib("libvespamalloc.so")
		}
	}
	trace.Trace("use file:", useFile)
	if useFile == "" {
		return
	}
	if loadAsHuge := p.Getenv(envvars.VESPA_LOAD_CODE_AS_HUGEPAGES); loadAsHuge != "" {
		otherFile := vespaMallocLib("libvespa_load_as_huge.so")
		useFile = fmt.Sprintf("%s:%s", useFile, otherFile)
	}
	p.ConsiderEnvFallback(envvars.VESPA_MALLOC_HUGEPAGES, envvars.VESPA_USE_HUGEPAGES)
	p.mallocPreload = useFile
	p.shouldUseMallocImpl = true
}
