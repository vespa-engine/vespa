// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package xml

import (
	"bufio"
	"bytes"
	"encoding/xml"
	"fmt"
	"io"
	"regexp"
	"strconv"
	"strings"

	"github.com/vespa-engine/vespa/client/go/internal/vespa"
)

var DefaultDeployment Deployment

func init() {
	defaultDeploymentRaw := `<deployment version="1.0">
  <prod>
    <region>aws-us-east-1c</region>
  </prod>
</deployment>`
	d, err := ReadDeployment(strings.NewReader(defaultDeploymentRaw))
	if err != nil {
		panic(err)
	}
	DefaultDeployment = d
}

// Deployment represents the contents of a deployment.xml file.
type Deployment struct {
	Root     xml.Name   `xml:"deployment"`
	Version  string     `xml:"version,attr"`
	Instance []Instance `xml:"instance"`
	Prod     Prod       `xml:"prod"`
	rawXML   bytes.Buffer
}

type Instance struct {
	Prod Prod `xml:"prod"`
}

type Prod struct {
	Regions []Region `xml:"region"`
}

type Region struct {
	Name string `xml:",chardata"`
}

func (d Deployment) String() string { return d.rawXML.String() }

// Replace replaces any elements of name found under parentName with data.
func (s *Deployment) Replace(parentName, name string, data interface{}) error {
	rewritten, err := Replace(&s.rawXML, parentName, name, data)
	if err != nil {
		return err
	}
	newXML, err := ReadDeployment(strings.NewReader(rewritten))
	if err != nil {
		return err
	}
	*s = newXML
	return nil
}

// Services represents the contents of a services.xml file.
type Services struct {
	Root      xml.Name    `xml:"services"`
	Container []Container `xml:"container"`
	Content   []Content   `xml:"content"`
	rawXML    bytes.Buffer
}

type Container struct {
	Root    xml.Name `xml:"container"`
	ID      string   `xml:"id,attr"`
	Nodes   Nodes    `xml:"nodes"`
	Clients Clients  `xml:"clients"`
}

type Content struct {
	ID    string `xml:"id,attr"`
	Nodes Nodes  `xml:"nodes"`
}

type Clients struct {
	Client []Client `xml:"client"`
}

type Client struct {
	Id          string      `xml:"id,attr"`
	Certificate Certificate `xml:"certificate"`
}

type Certificate struct {
	File string `xml:"file,attr"`
}

type Nodes struct {
	Count     string     `xml:"count,attr"`
	Resources *Resources `xml:"resources,omitempty"`
}

type Resources struct {
	Vcpu   string `xml:"vcpu,attr"`
	Memory string `xml:"memory,attr"`
	Disk   string `xml:"disk,attr"`
}

func (s Services) String() string { return s.rawXML.String() }

// Reads file paths from services.xml
func (s Services) CertPaths() []string {
	var certificates []string
	for _, container := range s.Container {
		for _, client := range container.Clients.Client {
			certificates = append(certificates, client.Certificate.File)
		}
	}
	return certificates
}

// Replace replaces any elements of name found under parentName with data.
func (s *Services) Replace(parentName, name string, data interface{}) error {
	rewritten, err := Replace(&s.rawXML, parentName, name, data)
	if err != nil {
		return err
	}
	newXML, err := ReadServices(strings.NewReader(rewritten))
	if err != nil {
		return err
	}
	*s = newXML
	return nil
}

func (r Resources) String() string {
	return fmt.Sprintf("vcpu=%s,memory=%s,disk=%s", r.Vcpu, r.Memory, r.Disk)
}

// ReadDeployment reads deployment.xml from reader r.
func ReadDeployment(r io.Reader) (Deployment, error) {
	var deployment Deployment
	var rawXML bytes.Buffer
	dec := xml.NewDecoder(io.TeeReader(r, &rawXML))
	if err := dec.Decode(&deployment); err != nil {
		return Deployment{}, err
	}
	deployment.rawXML = rawXML
	return deployment, nil
}

// ReadServices reads services.xml from reader r.
func ReadServices(r io.Reader) (Services, error) {
	var services Services
	var rawXML bytes.Buffer
	dec := xml.NewDecoder(io.TeeReader(r, &rawXML))
	if err := dec.Decode(&services); err != nil {
		return Services{}, err
	}
	services.rawXML = rawXML
	return services, nil
}

// Regions returns given region names as elements.
func Regions(names ...string) []Region {
	var regions []Region
	for _, z := range names {
		regions = append(regions, Region{Name: z})
	}
	return regions
}

// ParseResources parses nodes resources from string s.
func ParseResources(s string) (Resources, error) {
	var parts []string
	inRange := false
	var sb strings.Builder
	for _, c := range s {
		if inRange {
			if c == ']' {
				inRange = false
			}
		} else {
			if c == '[' {
				inRange = true
			} else if c == ',' {
				parts = append(parts, sb.String())
				sb.Reset()
				continue
			}
		}
		sb.WriteRune(c)
	}
	parts = append(parts, sb.String())
	if len(parts) != 3 {
		return Resources{}, fmt.Errorf("invalid resources: %q", s)
	}
	vcpu, err := parseResource("vcpu", parts[0])
	if err != nil {
		return Resources{}, err
	}
	memory, err := parseResource("memory", parts[1])
	if err != nil {
		return Resources{}, err
	}
	disk, err := parseResource("disk", parts[2])
	if err != nil {
		return Resources{}, err
	}
	return Resources{Vcpu: vcpu, Memory: memory, Disk: disk}, nil
}

// ParseNodeCount parses a node count range from string s.
func ParseNodeCount(s string) (int, int, error) {
	parseErr := fmt.Errorf("invalid node count: %q", s)
	min, max := 0, 0
	n, err := strconv.Atoi(s)
	if err == nil {
		min = n
		max = n
	} else if strings.HasPrefix(s, "[") && strings.HasSuffix(s, "]") {
		parts := strings.Split(s[1:len(s)-1], ",")
		if len(parts) != 2 {
			return 0, 0, parseErr
		}
		min, err = strconv.Atoi(strings.TrimSpace(parts[0]))
		if err != nil {
			return 0, 0, parseErr
		}
		max, err = strconv.Atoi(strings.TrimSpace(parts[1]))
		if err != nil {
			return 0, 0, parseErr
		}
	} else {
		return 0, 0, parseErr
	}

	if min <= 0 || min > max {
		return 0, 0, parseErr
	}
	return min, max, nil
}

// IsProdRegion returns whether string s is a valid production region.
func IsProdRegion(s string, system vespa.System) bool {
	switch system.Name {
	case vespa.CDSystem.Name:
		switch s {
		case "aws-us-east-1a", "cd-us-east-1",
			"cd-us-west-1":
			return true
		}
	case vespa.MainSystem.Name:
		switch s {
		case "prod.ap-northeast-1", "prod.ap-northeast-2",
			"prod.ap-southeast-1", "prod.aws-ap-northeast-2a",
			"prod.aws-apse1-az1", "prod.aws-apse1-az3",
			"prod.aws-ap-southeast-1a", "prod.aws-euw1-az1",
			"prod.aws-euw1-az3", "prod.aws-eu-west-1a",
			"prod.aws-use1-az2", "prod.aws-us-east-1a",
			"prod.aws-us-east-1b", "prod.aws-us-east-2a",
			"prod.aws-usw2-az2", "prod.aws-usw2-az3",
			"prod.aws-us-west-2a", "prod.eu-west-1",
			"prod.us-central-1", "prod.us-east-3",
			"prod.us-west-1":
			return true
		}
	case vespa.PublicCDSystem.Name:
		switch s {
		case "aws-us-east-1c", "gcp-us-central1-f":
			return true
		}
	case vespa.PublicSystem.Name:
		switch s {
		case "aws-us-east-1c", "aws-us-west-2a",
			"aws-eu-west-1a", "aws-ap-northeast-1a",
			"gcp-europe-west3-b", "gcp-us-central1-f":
			return true
		}
	}
	return false
}

func parseResource(field, s string) (string, error) {
	parts := strings.SplitN(s, "=", 2)
	if len(parts) != 2 || strings.TrimSpace(parts[0]) != field {
		return "", fmt.Errorf("invalid value for %s field: %q", field, s)
	}
	return strings.TrimSpace(parts[1]), nil
}

// ReplaceRaw finds all elements of name in rawXML and replaces their contents with value.
func ReplaceRaw(rawXML, name, value string) string {
	startElement := "<" + name + ">"
	endElement := "</" + name + ">"
	re := regexp.MustCompile(regexp.QuoteMeta(startElement) + ".*" + regexp.QuoteMeta(endElement))
	return re.ReplaceAllString(rawXML, startElement+value+endElement)
}

// Replace looks for an element name in the XML read from reader r, appearing inside a element named parentName.
//
// Any matching elements found are replaced with data. If parentName contains an ID selector, e.g. "email#my-id", only
// the elements inside the parent element with the attribute id="my-id" are replaced.
//
// If data is nil, any matching elements are removed instead of replaced.
func Replace(r io.Reader, parentName, name string, data interface{}) (string, error) {
	var buf bytes.Buffer
	dec := xml.NewDecoder(r)
	enc := xml.NewEncoder(&buf)
	enc.Indent("", "  ")

	parts := strings.SplitN(parentName, "#", 2)
	id := ""
	if len(parts) > 1 {
		parentName = parts[0]
		id = parts[1]
	}

	foundParent := false
	replacing := false
	done := false
	for {
		token, err := dec.Token()
		if err == io.EOF {
			break
		} else if err != nil {
			return "", err
		}
		token = joinNamespace(token)
		if isEndElement(parentName, token) {
			foundParent = false
			done = false
		}
		if _, ok := getStartElement(parentName, id, token); ok {
			foundParent = true
		}
		if foundParent {
			if isEndElement(name, token) {
				replacing = false
				continue
			}
			replacableElement, ok := getStartElement(name, "", token)
			if ok {
				replacing = true
			}
			if replacing {
				if !done && data != nil {
					replacableElement.Attr = nil // Clear any existing attributes as given data should contain the wanted ones
					if err := enc.EncodeElement(data, replacableElement); err != nil {
						return "", err
					}
					done = true
				}
				continue
			}
		}
		if err := enc.EncodeToken(token); err != nil {
			return "", err
		}
	}
	if err := enc.Flush(); err != nil {
		return "", err
	}
	var sb strings.Builder
	scanner := bufio.NewScanner(&buf)
	for scanner.Scan() {
		line := scanner.Text()
		// Skip lines containing only whitespace
		if strings.TrimSpace(line) == "" {
			continue
		}
		sb.WriteString(line)
		sb.WriteRune('\n')
	}
	if err := scanner.Err(); err != nil {
		return "", err
	}
	return sb.String(), nil
}

func joinNamespace(token xml.Token) xml.Token {
	// Hack to work around the broken namespace support in Go
	// https://github.com/golang/go/issues/13400
	if startElement, ok := token.(xml.StartElement); ok {
		attr := make([]xml.Attr, 0, len(startElement.Attr))
		for _, a := range startElement.Attr {
			if a.Name.Space != "" {
				a.Name.Space = ""
				a.Name.Local = "xmlns:" + a.Name.Local
			}
			attr = append(attr, a)
		}
		startElement.Attr = attr
		return startElement
	}
	return token
}

func getStartElement(name, id string, token xml.Token) (xml.StartElement, bool) {
	startElement, ok := token.(xml.StartElement)
	if !ok {
		return xml.StartElement{}, false
	}
	matchingID := id == ""
	for _, attr := range startElement.Attr {
		if attr.Name.Local == "id" && attr.Value == id {
			matchingID = true
		}
	}
	return startElement, startElement.Name.Local == name && matchingID
}

func isEndElement(name, token xml.Token) bool {
	endElement, ok := token.(xml.EndElement)
	return ok && endElement.Name.Local == name
}
