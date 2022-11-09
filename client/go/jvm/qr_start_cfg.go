// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

package jvm

import (
	"encoding/json"
	"strings"

	"github.com/vespa-engine/vespa/client/go/trace"
	"github.com/vespa-engine/vespa/client/go/util"
)

type QrStartConfig struct {
	Jvm struct {
		Server                               bool   `json:"server"`
		Verbosegc                            bool   `json:"verbosegc"`
		Gcopts                               string `json:"gcopts"`
		Heapsize                             int    `json:"heapsize"`
		MinHeapsize                          int    `json:"minHeapsize"`
		Stacksize                            int    `json:"stacksize"`
		CompressedClassSpaceSize             int    `json:"compressedClassSpaceSize"`
		BaseMaxDirectMemorySize              int    `json:"baseMaxDirectMemorySize"`
		DirectMemorySizeCache                int    `json:"directMemorySizeCache"`
		HeapSizeAsPercentageOfPhysicalMemory int    `json:"heapSizeAsPercentageOfPhysicalMemory"`
		AvailableProcessors                  int    `json:"availableProcessors"`
	} `json:"jvm"`
	Qrs struct {
		Env string `json:"env"`
	} `json:"qrs"`
	Jdisc struct {
		ClasspathExtra string `json:"classpath_extra"`
		ExportPackages string `json:"export_packages"`
	} `json:"jdisc"`
}

func (a *ApplicationContainer) getQrStartCfg() *QrStartConfig {
	var parsedJson QrStartConfig
	args := []string{
		"-j",
		"-w", "10",
		"-n", "search.config.qr-start",
		"-i", a.ConfigId(),
	}
	backticks := util.BackTicksForwardStderr
	data, err := backticks.Run("vespa-get-config", args...)
	if err != nil {
		trace.Trace("could not get qr-start config:", err)
	} else {
		codec := json.NewDecoder(strings.NewReader(data))
		err = codec.Decode(&parsedJson)
		if err != nil {
			trace.Trace("could not decode JSON >>>", data, "<<< error:", err)
		}
	}
	return &parsedJson
}
