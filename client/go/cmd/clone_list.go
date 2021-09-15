package cmd

import (
	"encoding/json"
	"net/http"
	"sort"
	"time"

	"github.com/vespa-engine/vespa/client/go/util"
)

func listSampleApps() ([]string, error) {
	return listSampleAppsAt("https://api.github.com/repos/vespa-engine/sample-apps/contents/")
}

func listSampleAppsAt(url string) ([]string, error) {
	rfs, err := getRepositoryFiles(url)
	if err != nil {
		return nil, err
	}
	var apps []string
	for _, rf := range rfs {
		isApp, follow := isApp(rf)
		if isApp {
			apps = append(apps, rf.Path)
		} else if follow {
			apps2, err := listSampleAppsAt(rf.URL)
			if err != nil {
				return nil, err
			}
			apps = append(apps, apps2...)
		}
	}
	sort.Strings(apps)
	return apps, nil
}

func getRepositoryFiles(url string) ([]repositoryFile, error) {
	req, err := http.NewRequest("GET", url, nil)
	if err != nil {
		return nil, err
	}
	response, err := util.HttpDo(req, time.Minute, "GitHub")
	if err != nil {
		return nil, err
	}
	defer response.Body.Close()
	var files []repositoryFile
	dec := json.NewDecoder(response.Body)
	if err := dec.Decode(&files); err != nil {
		return nil, err
	}
	return files, nil
}

func isApp(rf repositoryFile) (ok bool, follow bool) {
	if rf.Type != "dir" {
		return false, false
	}
	if rf.Path == "" {
		return false, false
	}
	if rf.Path[0] == '_' || rf.Path[0] == '.' {
		return false, false
	}
	// These are just heuristics and must be updated if we add more directories that are not applications, or that
	// contain multiple applications inside
	switch rf.Name {
	case "test", "bin", "src":
		return false, false
	}
	switch rf.Path {
	case "news", "operations", "vespa-cloud":
		return false, true
	}
	return true, false
}

type repositoryFile struct {
	Path    string `json:"path"`
	Name    string `json:"name"`
	Type    string `json:"type"`
	URL     string `json:"url"`
	HtmlURL string `json:"html_url"`
}
