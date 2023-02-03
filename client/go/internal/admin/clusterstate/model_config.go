// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Author: arnej

// utilities to get and manipulate node states in a storage cluster
package clusterstate

import (
	"encoding/json"
	"sort"
	"strings"

	"github.com/vespa-engine/vespa/client/go/internal/admin/trace"
)

type VespaModelConfig struct {
	VespaVersion string `json:"vespaVersion"`
	Hosts        []struct {
		Name     string `json:"name"`
		Services []struct {
			Name        string `json:"name"`
			Type        string `json:"type"`
			Configid    string `json:"configid"`
			Clustertype string `json:"clustertype"`
			Clustername string `json:"clustername"`
			Index       int    `json:"index"`
			Ports       []struct {
				Number int    `json:"number"`
				Tags   string `json:"tags"`
			} `json:"ports"`
		} `json:"services"`
	} `json:"hosts"`
}

func (m *VespaModelConfig) String() string {
	if m == nil {
		return "nil"
	}
	var buf strings.Builder
	buf.WriteString("vespa version: ")
	buf.WriteString(m.VespaVersion)
	for _, h := range m.Hosts {
		buf.WriteString("\n  host: ")
		buf.WriteString(h.Name)
		for _, s := range h.Services {
			buf.WriteString("\n    service: ")
			buf.WriteString(s.Name)
			buf.WriteString(" type: ")
			buf.WriteString(s.Type)
			buf.WriteString(" cluster: ")
			buf.WriteString(s.Clustername)
		}
		buf.WriteString("\n")
	}
	buf.WriteString("\n")
	return buf.String()
}

type ClusterControllerSpec struct {
	host string
	port int
}

func parseModelConfig(input string) *VespaModelConfig {
	codec := json.NewDecoder(strings.NewReader(input))
	var parsedJson VespaModelConfig
	err := codec.Decode(&parsedJson)
	if err != nil {
		trace.Trace("could not decode JSON >>>", input, "<<< error:", err)
		return nil
	}
	return &parsedJson
}

func (m *VespaModelConfig) findClusterControllers() []ClusterControllerSpec {
	res := make([]ClusterControllerSpec, 0, 1)
	for _, h := range m.Hosts {
		for _, s := range h.Services {
			if s.Type == "container-clustercontroller" {
				for _, p := range s.Ports {
					if strings.Contains(p.Tags, "state") {
						res = append(res, ClusterControllerSpec{
							host: h.Name, port: p.Number,
						})
					}
				}
			}
		}
	}
	return res
}

func (m *VespaModelConfig) findSelectedServices(opts *Options) []serviceSpec {
	res := make([]serviceSpec, 0, 5)
	for _, h := range m.Hosts {
		for _, s := range h.Services {
			spec := serviceSpec{
				cluster:     s.Clustername,
				serviceType: s.Type,
				index:       s.Index,
				host:        h.Name,
			}
			if s.Type == "storagenode" {
				// simplify:
				spec.serviceType = "storage"
			}
			if opts.wantService(spec) {
				res = append(res, spec)
			}
		}
	}
	sort.Slice(res, func(i, j int) bool {
		a := res[i]
		b := res[j]
		if a.cluster != b.cluster {
			return a.cluster < b.cluster
		}
		if a.serviceType != b.serviceType {
			return a.serviceType < b.serviceType
		}
		if a.index != b.index {
			return a.index < b.index
		}
		return a.host < b.host
	})
	return res
}
