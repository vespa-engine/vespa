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

	availableMemory, err := getAvailableMemory()
	if err != nil {
		fmt.Fprintln(os.Stderr, "Warning: Unable to read memory information from /proc/meminfo or cgroup files. Memory checks will be skipped.")
		return
	}

	if availableMemory < minRequiredMemoryInBytes {
		osutil.ExitMsg("Running the Vespa container image requires at least 4GB available memory." +
			" See the relevant docs (https://docs.vespa.ai/en/operations-selfhosted/docker-containers.html#memory) " +
			" or set VESPA_IGNORE_NOT_ENOUGH_MEMORY=true")
	}
}

func getAvailableMemory() (uint64, error) {

	if availableMemory, err := getMemoryLimitCgroupMax(); err == nil {
		return availableMemory, nil
	}

	if availableMemory, err := getAvailableSystemMemory(); err == nil {
		return availableMemory, nil
	}

	return 0, fmt.Errorf("unable to get available memory")
}

func getMemoryLimitCgroupMax() (uint64, error) {
	// Try cgroup v2 first
	if data, err := os.ReadFile("/sys/fs/cgroup/memory.max"); err == nil {
		content := strings.TrimSpace(string(data))
		if content == "max" {
			return 0, fmt.Errorf("no memory limit set")
		}
		return strconv.ParseUint(content, 10, 64)
	}

	// Try cgroup v1
	if data, err := os.ReadFile("/sys/fs/cgroup/memory/memory.limit_in_bytes"); err == nil {
		content := strings.TrimSpace(string(data))
		limit, err := strconv.ParseUint(content, 10, 64)
		if err != nil {
			return 0, err
		}
		// Check for "unlimited" (very large number in cgroup v1)
		if limit >= (1 << 62) {
			return 0, fmt.Errorf("no memory limit set")
		}
		return limit, nil
	}

	return 0, fmt.Errorf("no cgroup memory limit found")
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
