// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license.
// Author: johsol

package prog

import (
	"os"
	"path/filepath"
	"testing"

	"github.com/vespa-engine/vespa/client/go/internal/admin/envvars"
)

func TestConfigureMallocImpl_VespaMalloc(t *testing.T) {
	tmp := t.TempDir()
	t.Setenv(envvars.VESPA_HOME, tmp)
	lib64 := mockVespaHome(t, tmp)

	t.Setenv(envvars.VESPA_USE_MALLOC_IMPL, "vespamalloc")

	p := &Spec{}
	p.ConfigureMallocImpl()

	want := filepath.Join(lib64, "libvespamalloc.so")
	if !p.shouldUseMallocImpl {
		t.Fatalf("shouldUseMallocImpl=false, want true")
	}
	if p.mallocPreload != want {
		t.Fatalf("mallocPreload=%q, want %q", p.mallocPreload, want)
	}
}

func TestConfigureMallocImpl_VespaMallocD(t *testing.T) {
	tmp := t.TempDir()
	t.Setenv(envvars.VESPA_HOME, tmp)
	lib64 := mockVespaHome(t, tmp)

	t.Setenv(envvars.VESPA_USE_MALLOC_IMPL, "vespamallocd")

	p := &Spec{}
	p.ConfigureMallocImpl()

	want := filepath.Join(lib64, "libvespamallocd.so")
	if !p.shouldUseMallocImpl {
		t.Fatalf("shouldUseMallocImpl=false, want true")
	}
	if p.mallocPreload != want {
		t.Fatalf("mallocPreload=%q, want %q", p.mallocPreload, want)
	}
}

func TestConfigureMallocImpl_VespaMallocDst(t *testing.T) {
	tmp := t.TempDir()
	t.Setenv(envvars.VESPA_HOME, tmp)
	lib64 := mockVespaHome(t, tmp)

	t.Setenv(envvars.VESPA_USE_MALLOC_IMPL, "vespamallocdst")

	p := &Spec{}
	p.ConfigureMallocImpl()

	want := filepath.Join(lib64, "libvespamallocdst16.so")
	if !p.shouldUseMallocImpl {
		t.Fatalf("shouldUseMallocImpl=false, want true")
	}
	if p.mallocPreload != want {
		t.Fatalf("mallocPreload=%q, want %q", p.mallocPreload, want)
	}
}

func TestConfigureMallocImpl_MiMalloc(t *testing.T) {
	tmp := t.TempDir()
	t.Setenv(envvars.VESPA_HOME, tmp)
	deps := mockVespaDeps(t, tmp)

	// Setup LD_LIBRARY_PATH
	alt := filepath.Join(tmp, "somedummylib")
	if err := os.MkdirAll(alt, 0o755); err != nil {
		t.Fatal(err)
	}
	t.Setenv(envvars.LD_LIBRARY_PATH, alt+":"+deps)

	t.Setenv(envvars.VESPA_USE_MALLOC_IMPL, "mimalloc")

	p := &Spec{}
	p.ConfigureMallocImpl()

	want := filepath.Join(deps, "libmimalloc.so")
	if !p.shouldUseMallocImpl {
		t.Fatalf("shouldUseMallocImpl=false, want true")
	}
	if p.mallocPreload != want {
		t.Fatalf("mallocPreload=%q, want %q", p.mallocPreload, want)
	}
}

func TestConfigureMallocImpl_HugepagesChaining(t *testing.T) {
	tmp := t.TempDir()
	t.Setenv(envvars.VESPA_HOME, tmp)
	lib64 := mockVespaHome(t, tmp)

	t.Setenv(envvars.VESPA_USE_MALLOC_IMPL, "vespamalloc")
	t.Setenv(envvars.VESPA_LOAD_CODE_AS_HUGEPAGES, "1") // any non-empty value triggers chaining

	p := &Spec{}
	p.ConfigureMallocImpl()

	base := filepath.Join(lib64, "libvespamalloc.so")
	huge := filepath.Join(lib64, "libvespa_load_as_huge.so")
	want := base + ":" + huge

	if !p.shouldUseMallocImpl {
		t.Fatalf("shouldUseMallocImpl=false, want true")
	}
	if p.mallocPreload != want {
		t.Fatalf("mallocPreload=%q, want %q", p.mallocPreload, want)
	}
}

func mockVespaHome(t *testing.T, vespaHome string) (lib64 string) {
	t.Helper()
	lib64 = filepath.Join(vespaHome, "lib64", "vespa", "malloc")
	for _, name := range []string{
		"libvespamalloc.so",
		"libvespamallocd.so",
		"libvespamallocdst16.so",
		"libvespa_load_as_huge.so",
	} {
		touch(t, filepath.Join(lib64, name))
	}
	return lib64
}

func mockVespaDeps(t *testing.T, base string) string {
	t.Helper()
	deps := filepath.Join(base, "vespa-deps", "lib64")
	touch(t, filepath.Join(deps, "libmimalloc.so"))
	return deps
}

func touch(t *testing.T, p string) {
	t.Helper()
	if err := os.MkdirAll(filepath.Dir(p), 0o755); err != nil {
		t.Fatalf("mkdir %s: %v", filepath.Dir(p), err)
	}
	f, err := os.Create(p)
	if err != nil {
		t.Fatalf("create %s: %v", p, err)
	}
	_ = f.Close()
}
