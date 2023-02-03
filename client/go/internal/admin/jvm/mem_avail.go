// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package jvm

import (
	"fmt"
	"os"
	"strconv"
	"strings"

	"github.com/vespa-engine/vespa/client/go/internal/admin/trace"
	"github.com/vespa-engine/vespa/client/go/internal/util"
)

func parseFree(txt string) AmountOfMemory {
	f := strings.Fields(txt)
	for idx, field := range f {
		if field == "Mem:" && idx+1 < len(f) {
			res, err := strconv.Atoi(f[idx+1])
			if err == nil {
				return MegaBytesOfMemory(res)
			} else {
				trace.Warning(err)
			}
		}
	}
	return BytesOfMemory(0)
}

func parentDir(dir string) string {
	lastSlash := 0
	for idx, ch := range dir {
		if ch == '/' {
			lastSlash = idx
		}
	}
	return dir[:lastSlash]
}

func readLineFrom(filename string) (string, error) {
	content, err := os.ReadFile(filename)
	s := string(content)
	if err != nil {
		return s, err
	}
	s = strings.TrimSuffix(s, "\n")
	if strings.Contains(s, "\n") {
		return s, fmt.Errorf("unexpected multiple lines in file %s", filename)
	}
	return s, nil
}

func vespa_cg2get(limitname string) (output string, err error) {
	return vespa_cg2get_impl("", limitname)
}
func vespa_cg2get_impl(rootdir, limitname string) (output string, err error) {
	_, err = os.Stat(rootdir + "/sys/fs/cgroup/cgroup.controllers")
	if err != nil {
		trace.Trace("no cgroups:", err)
		return
	}
	cgroup_content, err := readLineFrom(rootdir + "/proc/self/cgroup")
	if err != nil {
		trace.Trace("no cgroup for self:", err)
		return
	}
	min_value := "max"
	path := rootdir + "/sys/fs/cgroup"
	slice := strings.TrimPrefix(cgroup_content, "0::")
	dirNames := strings.Split(slice, "/")
	for _, dirName := range dirNames {
		path = path + dirName + "/"
		value, err := readLineFrom(path + limitname)
		trace.Debug("read from", path+limitname, "=>", value)
		if err == nil {
			if value == "max" {
				// nop
			} else if min_value == "max" {
				min_value = value
			} else if len(value) < len(min_value) {
				min_value = value
			} else if len(value) == len(min_value) && value < min_value {
				min_value = value
			}
		}
	}
	trace.Trace("min_value of", limitname, "for cgroups v2:", min_value)
	return min_value, nil
}

func getAvailableMemory() AmountOfMemory {
	result := BytesOfMemory(0)
	backticks := util.BackTicksWithStderr
	freeOutput, err := backticks.Run("free", "-m")
	if err == nil {
		result = parseFree(freeOutput)
		trace.Trace("run 'free' ok, result:", result)
	} else {
		trace.Trace("run 'free' failed:", err)
	}
	available_cgroup := KiloBytesOfMemory(1 << 31)
	cggetOutput, err := backticks.Run("cgget", "-nv", "-r", "memory.limit_in_bytes", "/")
	if err != nil {
		cggetOutput, err = vespa_cg2get("memory.max")
	}
	cggetOutput = strings.TrimSpace(cggetOutput)
	if err != nil {
		trace.Debug("run 'cgget' failed:", err, "=>", cggetOutput)
	}
	if err == nil && cggetOutput != "max" {
		numBytes, err := strconv.ParseInt(cggetOutput, 10, 64)
		if err == nil && numBytes > (1<<28) {
			available_cgroup = BytesOfMemory(numBytes)
		} else {
			trace.Warning("unexpected 'cgget' output:", cggetOutput)
		}
	}
	if result.ToKB() == 0 || result.ToKB() > available_cgroup.ToKB() {
		result = available_cgroup
	}
	trace.Trace("getAvailableMemory returns:", result)
	return result
}

func getTransparentHugepageSize() AmountOfMemory {
	const fn = "/sys/kernel/mm/transparent_hugepage/hpage_pmd_size"
	thp_size := MegaBytesOfMemory(2)
	line, err := readLineFrom(fn)
	if err == nil {
		number, err := strconv.ParseInt(line, 10, 64)
		if err == nil {
			thp_size = BytesOfMemory(number)
			trace.Trace("thp_size", line, "=>", thp_size)
		} else {
			trace.Trace("no thp_size:", err)
		}
	} else {
		trace.Trace("no thp_size:", err)
	}
	return thp_size
}
