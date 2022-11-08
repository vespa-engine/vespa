// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package jvm

import (
	"fmt"
	"os"
	"strconv"
	"strings"

	"github.com/vespa-engine/vespa/client/go/trace"
	"github.com/vespa-engine/vespa/client/go/util"
)

func parseFree(txt string) int {
	f := strings.Fields(txt)
	for idx, field := range f {
		if field == "Mem:" && idx+1 < len(f) {
			res, err := strconv.Atoi(f[idx+1])
			if err == nil {
				return res
			} else {
				trace.Warning(err)
			}
		}
	}
	return 0
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

func vespa_cg2get(filename string) (output string, err error) {
	_, err = os.Stat("/sys/fs/cgroup/cgroup.controllers")
	if err != nil {
		trace.Trace("no cgroups:", err)
		return
	}
	cgroup_content, err := os.ReadFile("/proc/self/cgroup")
	if err != nil {
		trace.Trace("no cgroup for self:", err)
		return
	}
	min_value := "max"
	slice := strings.TrimPrefix(string(cgroup_content), "0::")
	slice = strings.TrimSuffix(slice, "\n")
	for strings.HasPrefix(slice, "/") {
		path := fmt.Sprintf("/sys/fs/cgroup%s/%s", slice, filename)
		fileContents, err := os.ReadFile(path)
		if err == nil {
			value := strings.TrimSuffix(string(fileContents), "\n")
			trace.Debug("read from", path, "=>", value)
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
		slice = parentDir(slice)
	}
	trace.Trace("min_value:", min_value)
	return min_value, nil
}

func getAvailableMbOfMemory() int {
	result := 0
	backticks := util.BackTicksWithStderr
	freeOutput, err := backticks.Run("free", "-m")
	if err == nil {
		result = parseFree(freeOutput)
		trace.Trace("run 'free' ok, result:", result)
	} else {
		trace.Trace("run 'free' failed:", err)
	}
	available_cgroup := int(1 << 31)
	cggetOutput, err := backticks.Run("cgget", "-nv", "-r", "memory.limit_in_bytes", "/")
	if err != nil {
		if strings.Contains(cggetOutput, "Cgroup is not mounted") {
			cggetOutput, err = vespa_cg2get("memory.max")
		}
	}
	cggetOutput = strings.TrimSpace(cggetOutput)
	if err != nil {
		trace.Debug("run 'cgget' failed:", err, "=>", cggetOutput)
	}
	if err == nil && cggetOutput != "max" {
		numBytes, err := strconv.Atoi(cggetOutput)
		if err == nil && numBytes > PowerOfTwo10 {
			available_cgroup = numBytes / PowerOfTwo10
		} else {
			trace.Warning("unexpected 'cgget' output:", cggetOutput)
		}
	}
	if result == 0 || result > available_cgroup {
		result = available_cgroup
	}
	trace.Trace("getAvailableMbOfMemory returns:", result)
	return result
}
