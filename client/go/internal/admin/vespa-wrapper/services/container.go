package services

import (
	"bufio"
	"fmt"
	"os"
	"strconv"
	"strings"

	"github.com/vespa-engine/vespa/client/go/internal/osutil"
)

const minRequiredMemoryInBytes = 3 * 1024 * 1024 * 1024

func VerifyAvailableMemory() {
	if os.Getenv("VESPA_IGNORE_NOT_ENOUGH_MEMORY") != "" {
		fmt.Fprintln(os.Stderr, "Memory check disabled via VESPA_IGNORE_NOT_ENOUGH_MEMORY.")
		return
	}

	if !isMemoryInfoAvailable() {
		osutil.ExitMsg("Unable to determine available memory: required files (/proc/meminfo, /sys/fs/cgroup/memory.max) are missing.")
	}

	availableMemory, err := getMemoryLimitCgroupMax()
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		osutil.ExitMsg("Available memory could not be obtained, " + err.Error())
	}

	if availableMemory < minRequiredMemoryInBytes {
		osutil.ExitMsg("Running the Vespa container image requires at least 4GB available memory." +
			" See the relevant docs (https://docs.vespa.ai/en/operations-selfhosted/docker-containers.html#memory) " +
			" or set VESPA_IGNORE_NOT_ENOUGH_MEMORY=true")
	}
}

func isMemoryInfoAvailable() bool {
	paths := []string{
		"/sys/fs/cgroup/memory.max", // cgroup v2
		"/proc/meminfo",             // host-level memory info
	}

	for _, path := range paths {
		if _, err := os.Stat(path); os.IsNotExist(err) {
			return false
		}
	}

	return true
}

func getMemoryLimitCgroupMax() (uint64, error) {
	data, err := os.ReadFile("/sys/fs/cgroup/memory.max")
	if err != nil {
		return 0, fmt.Errorf("failed to read memory.max: %w", err)
	}

	trimmed := strings.TrimSpace(string(data))
	if trimmed == "max" {
		// No memory limit enforced, use available system memory
		return getAvailableSystemMemory()
	}

	limit, err := strconv.ParseUint(trimmed, 10, 64)
	if err != nil {
		return 0, fmt.Errorf("failed to parse memory limit: %w", err)
	}

	return limit, nil
}

func getAvailableSystemMemory() (uint64, error) {
	file, err := os.Open("/proc/meminfo")
	if err != nil {
		return 0, fmt.Errorf("failed to open /proc/meminfo: %w", err)
	}
	defer file.Close()

	scanner := bufio.NewScanner(file)
	for scanner.Scan() {
		line := scanner.Text()
		if strings.HasPrefix(line, "MemAvailable:") {
			fields := strings.Fields(line)
			if len(fields) < 2 {
				return 0, fmt.Errorf("unexpected format in MemAvailable line: %s", line)
			}
			// MemAvailable is in kB
			kb, err := strconv.ParseUint(fields[1], 10, 64)
			if err != nil {
				return 0, fmt.Errorf("failed to parse memory value: %w", err)
			}
			return kb * 1024, nil // convert to bytes
		}
	}
	if err := scanner.Err(); err != nil {
		return 0, fmt.Errorf("scanner error: %w", err)
	}

	return 0, fmt.Errorf("MemAvailable not found in /proc/meminfo")
}
