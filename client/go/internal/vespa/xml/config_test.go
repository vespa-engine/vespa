package xml

import (
	"reflect"
	"strings"
	"testing"
)

func TestReplaceDeployment(t *testing.T) {
	in := `
<deployment version="1.0">
    <prod>
        <region>us-north-1</region>
        <region>eu-north-2</region>
    </prod>
</deployment>`

	out := `<deployment version="1.0">
  <prod>
    <region>eu-south-1</region>
    <region>us-central-1</region>
  </prod>
</deployment>
`
	regions := Regions("eu-south-1", "us-central-1")
	assertReplace(t, in, out, "prod", "region", regions)
}

func TestReplaceDeploymentWithInstance(t *testing.T) {
	in := `
<deployment version="1.0">
    <instance id="default">
        <prod>
            <region>us-north-1</region>
        </prod>
    </instance>
    <instance id="beta">
        <prod>
            <region>eu-south-1</region>
        </prod>
    </instance>
</deployment>`

	out := `<deployment version="1.0">
  <instance id="default">
    <prod>
      <region>us-central-1</region>
      <region>eu-west-1</region>
    </prod>
  </instance>
  <instance id="beta">
    <prod>
      <region>us-central-1</region>
      <region>eu-west-1</region>
    </prod>
  </instance>
</deployment>
`
	regions := Regions("us-central-1", "eu-west-1")
	assertReplace(t, in, out, "prod", "region", regions)
}

func TestReplaceServices(t *testing.T) {
	in := `
<services xmlns:deploy="vespa" xmlns:preprocess="properties">
  <container id="qrs">
    <search/>
    <document-api/>
    <nodes count="2">
      <resources vcpu="4" memory="8Gb" disk="50Gb"/>
    </nodes>
  </container>
  <content id="music">
    <redundancy>2</redundancy>
    <nodes count="3">
      <resources vcpu="8" memory="32Gb" disk="200Gb"/>
    </nodes>
    <documents>
      <document type="music"/>
    </documents>
  </content>
</services>
`

	out := `<services xmlns:deploy="vespa" xmlns:preprocess="properties">
  <container id="qrs">
    <search></search>
    <document-api></document-api>
    <nodes count="4">
      <resources vcpu="2" memory="4Gb" disk="50Gb"></resources>
    </nodes>
  </container>
  <content id="music">
    <redundancy>2</redundancy>
    <nodes count="3">
      <resources vcpu="8" memory="32Gb" disk="200Gb"></resources>
    </nodes>
    <documents>
      <document type="music"></document>
    </documents>
  </content>
</services>
`
	nodes := Nodes{Count: "4", Resources: &Resources{Vcpu: "2", Memory: "4Gb", Disk: "50Gb"}}
	assertReplace(t, in, out, "container#qrs", "nodes", nodes)
}

func TestReplaceServicesEmptyResources(t *testing.T) {
	in := `<services xmlns:deploy="vespa" xmlns:preprocess="properties">
  <container id="movies">
    <search></search>
    <document-api></document-api>
    <nodes count="4"/>
  </container>
</services>
`
	out := `<services xmlns:deploy="vespa" xmlns:preprocess="properties">
  <container id="movies">
    <search></search>
    <document-api></document-api>
    <nodes count="5">
      <resources vcpu="4" memory="8Gb" disk="100Gb"></resources>
    </nodes>
  </container>
</services>
`
	nodes := Nodes{Count: "5", Resources: &Resources{Vcpu: "4", Memory: "8Gb", Disk: "100Gb"}}
	assertReplace(t, in, out, "container#movies", "nodes", nodes)
}

func TestReplaceRemovesElement(t *testing.T) {
	in := `
<deployment version="1.0">
    <prod>
        <region>eu-south-1</region>
        <region>us-central-1</region>
        <test>us-central-1</test>
    </prod>
</deployment>`

	out := `<deployment version="1.0">
  <prod>
    <region>eu-south-1</region>
    <region>us-central-1</region>
  </prod>
</deployment>
`
	assertReplace(t, in, out, "prod", "test", nil)
}

func TestReplaceRaw(t *testing.T) {
	in := `
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>ai.vespa.cloud.docsearch</groupId>
    <artifactId>vespacloud-docsearch</artifactId>
    <packaging>container-plugin</packaging>
    <version>1.0.0</version>

    <parent>
        <groupId>com.yahoo.vespa</groupId>
        <artifactId>cloud-tenant-base</artifactId>
        <version>[7,999)</version>
    </parent>

    <properties>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <tenant>tenant</tenant>
        <application>app</application>
        <instance>instance</instance>
    </properties>

</project>
`
	replacements := map[string]string{
		"tenant":      "vespa-team",
		"application": "music",
		"instance":    "default",
	}
	rewritten := in
	for element, value := range replacements {
		rewritten = ReplaceRaw(rewritten, element, value)
	}

	out := `
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>ai.vespa.cloud.docsearch</groupId>
    <artifactId>vespacloud-docsearch</artifactId>
    <packaging>container-plugin</packaging>
    <version>1.0.0</version>

    <parent>
        <groupId>com.yahoo.vespa</groupId>
        <artifactId>cloud-tenant-base</artifactId>
        <version>[7,999)</version>
    </parent>

    <properties>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <tenant>vespa-team</tenant>
        <application>music</application>
        <instance>default</instance>
    </properties>

</project>
`
	if rewritten != out {
		t.Errorf("got:\n%s\nwant:\n%s\n", rewritten, out)
	}
}

func TestReadServicesNoResources(t *testing.T) {
	s := `
<services xmlns:deploy="vespa" xmlns:preprocess="properties">
  <container id="qrs">
     <nodes count="2">
      <resources vcpu="4" memory="8Gb" disk="50Gb"/>
    </nodes>
  </container>
  <content id="music">
    <redundancy>2</redundancy>
    <nodes count="3"/>
    <documents>
      <document type="music"/>
    </documents>
  </content>
</services>
`
	services, err := ReadServices(strings.NewReader(s))
	if err != nil {
		t.Fatal(err)
	}
	if got := services.Content[0].Nodes.Resources; got != nil {
		t.Errorf("got %+v, want nil", got)
	}
}

func TestParseResources(t *testing.T) {
	assertResources(t, "foo", Resources{}, true)
	assertResources(t, "vcpu=2,memory=4Gb", Resources{}, true)
	assertResources(t, "memory=4Gb,vcpu=2,disk=100Gb", Resources{}, true)
	assertResources(t, "vcpu=2,memory=4Gb,disk=100Gb", Resources{Vcpu: "2", Memory: "4Gb", Disk: "100Gb"}, false)
	assertResources(t, "  vcpu = 4, memory =8Gb,  disk=500Gb ", Resources{Vcpu: "4", Memory: "8Gb", Disk: "500Gb"}, false)
	assertResources(t, "vcpu=[2.5,  8],memory=[32Gb,150Gb],disk=[100Gb, 1Tb]", Resources{Vcpu: "[2.5,  8]", Memory: "[32Gb,150Gb]", Disk: "[100Gb, 1Tb]"}, false)
}

func TestParseNodeCount(t *testing.T) {
	assertNodeCount(t, "2", 2, 2, false)
	assertNodeCount(t, "[4,8]", 4, 8, false)
	assertNodeCount(t, "[ 4,  8 ]", 4, 8, false)

	assertNodeCount(t, "foo", 0, 0, true)
	assertNodeCount(t, "[foo,bar]", 0, 0, true)

	assertNodeCount(t, "0", 0, 0, true)
	assertNodeCount(t, "-1", 0, 0, true)
	assertNodeCount(t, "[2, 1]", 0, 0, true)
}

func assertReplace(t *testing.T, input, want, parentElement, element string, data interface{}) {
	got, err := Replace(strings.NewReader(input), parentElement, element, data)
	if err != nil {
		t.Fatal(err)
	}
	if got != want {
		t.Errorf("got:\n%s\nwant:\n%s\n", got, want)
	}
}

func assertNodeCount(t *testing.T, input string, wantMin, wantMax int, wantErr bool) {
	min, max, err := ParseNodeCount(input)
	if wantErr {
		if err == nil {
			t.Errorf("want error for input %q", input)
		}
		return
	}
	if min != wantMin || max != wantMax {
		t.Errorf("got min = %d, max = %d, want min = %d, max = %d", min, max, wantMin, wantMax)
	}
}

func assertResources(t *testing.T, input string, want Resources, wantErr bool) {
	got, err := ParseResources(input)
	if wantErr {
		if err == nil {
			t.Errorf("want error for %q", input)
		}
		return
	}
	if err != nil {
		t.Fatal(err)
	}
	if !reflect.DeepEqual(want, got) {
		t.Errorf("got %+v, want %+v", got, want)
	}
}
