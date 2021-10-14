// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa deploy API
// Author: bratseth

package vespa

import (
	"archive/zip"
	"bytes"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"io/ioutil"
	"mime/multipart"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"time"

	"github.com/vespa-engine/vespa/client/go/util"
)

var DefaultApplication = ApplicationID{Tenant: "default", Application: "application", Instance: "default"}

type ApplicationID struct {
	Tenant      string
	Application string
	Instance    string
}

type ZoneID struct {
	Environment string
	Region      string
}

type Deployment struct {
	Application ApplicationID
	Zone        ZoneID
}

type DeploymentOpts struct {
	ApplicationPackage ApplicationPackage
	Target             Target
	Deployment         Deployment
	APIKey             []byte
}

type ApplicationPackage struct {
	Path     string
	TestPath string
}

func (a ApplicationID) String() string {
	return fmt.Sprintf("%s.%s.%s", a.Tenant, a.Application, a.Instance)
}

func (a ApplicationID) SerializedForm() string {
	return fmt.Sprintf("%s:%s:%s", a.Tenant, a.Application, a.Instance)
}

func (d Deployment) String() string {
	return fmt.Sprintf("deployment of %s in %s", d.Application, d.Zone)
}

func (d DeploymentOpts) String() string {
	return fmt.Sprintf("%s to %s", d.Deployment, d.Target.Type())
}

func (d *DeploymentOpts) IsCloud() bool { return d.Target.Type() == cloudTargetType }

func (d *DeploymentOpts) url(path string) (*url.URL, error) {
	service, err := d.Target.Service(deployService, 0, 0)
	if err != nil {
		return nil, err
	}
	return url.Parse(service.BaseURL + path)
}

func (ap *ApplicationPackage) HasCertificate() bool {
	return ap.hasFile(filepath.Join("security", "clients.pem"), "security/clients.pem")
}

func (ap *ApplicationPackage) HasDeployment() bool { return ap.hasFile("deployment.xml", "") }

func (ap *ApplicationPackage) hasFile(filename, zipName string) bool {
	if zipName == "" {
		zipName = filename
	}
	if ap.IsZip() {
		r, err := zip.OpenReader(ap.Path)
		if err != nil {
			return false
		}
		defer r.Close()
		for _, f := range r.File {
			if f.Name == zipName {
				return true
			}
		}
		return false
	}
	return util.PathExists(filepath.Join(ap.Path, filename))
}

func (ap *ApplicationPackage) IsZip() bool { return isZip(ap.Path) }

func (ap *ApplicationPackage) IsJava() bool {
	if ap.IsZip() {
		r, err := zip.OpenReader(ap.Path)
		if err != nil {
			return false
		}
		defer r.Close()
		for _, f := range r.File {
			if filepath.Ext(f.Name) == ".jar" {
				return true
			}
		}
		return false
	}
	return util.PathExists(filepath.Join(ap.Path, "pom.xml"))
}

func (ap *ApplicationPackage) zipReader(test bool) (io.ReadCloser, error) {
	zipFile := ap.Path
	if test {
		zipFile = ap.TestPath
	}
	if !ap.IsZip() {
		tempZip, err := ioutil.TempFile("", "vespa")
		if err != nil {
			return nil, fmt.Errorf("Could not create a temporary zip file for the application package: %w", err)
		}
		defer func() {
			tempZip.Close()
			os.Remove(tempZip.Name())
		}()
		if err := zipDir(ap.Path, tempZip.Name()); err != nil {
			return nil, err
		}
		zipFile = tempZip.Name()
	}
	f, err := os.Open(zipFile)
	if err != nil {
		return nil, fmt.Errorf("Could not open application package at %s: %w", ap.Path, err)
	}
	return f, nil
}

// FindApplicationPackage finds the path to an application package from the zip file or directory zipOrDir.
func FindApplicationPackage(zipOrDir string, requirePackaging bool) (ApplicationPackage, error) {
	if isZip(zipOrDir) {
		return ApplicationPackage{Path: zipOrDir}, nil
	}
	if util.PathExists(filepath.Join(zipOrDir, "pom.xml")) {
		zip := filepath.Join(zipOrDir, "target", "application.zip")
		if util.PathExists(zip) {
			testZip := filepath.Join(zipOrDir, "target", "application-test.zip")
			return ApplicationPackage{Path: zip, TestPath: testZip}, nil
		}
		if requirePackaging {
			return ApplicationPackage{}, errors.New("pom.xml exists but no target/application.zip. Run mvn package first")
		}
	}
	if util.PathExists(filepath.Join(zipOrDir, "src", "main", "application")) {
		return ApplicationPackage{Path: filepath.Join(zipOrDir, "src", "main", "application")}, nil
	}
	if util.PathExists(filepath.Join(zipOrDir, "services.xml")) {
		return ApplicationPackage{Path: zipOrDir}, nil
	}
	return ApplicationPackage{}, errors.New("Could not find an application package source in '" + zipOrDir + "'")
}

func ApplicationFromString(s string) (ApplicationID, error) {
	parts := strings.Split(s, ".")
	if len(parts) != 3 {
		return ApplicationID{}, fmt.Errorf("invalid application: %q", s)
	}
	return ApplicationID{Tenant: parts[0], Application: parts[1], Instance: parts[2]}, nil
}

func ZoneFromString(s string) (ZoneID, error) {
	parts := strings.Split(s, ".")
	if len(parts) != 2 {
		return ZoneID{}, fmt.Errorf("invalid zone: %q", s)
	}
	return ZoneID{Environment: parts[0], Region: parts[1]}, nil
}

// Prepare deployment and return the session ID
func Prepare(deployment DeploymentOpts) (int64, error) {
	if deployment.IsCloud() {
		return 0, fmt.Errorf("prepare is not supported with %s target", deployment.Target.Type())
	}
	sessionURL, err := deployment.url("/application/v2/tenant/default/session")
	if err != nil {
		return 0, err
	}
	sessionID, err := uploadApplicationPackage(sessionURL, deployment)
	if err != nil {
		return 0, err
	}
	prepareURL, err := deployment.url(fmt.Sprintf("/application/v2/tenant/default/session/%d/prepared", sessionID))
	if err != nil {
		return 0, err
	}
	req, err := http.NewRequest("PUT", prepareURL.String(), nil)
	if err != nil {
		return 0, err
	}
	serviceDescription := "Deploy service"
	response, err := util.HttpDo(req, time.Second*30, serviceDescription)
	if err != nil {
		return 0, err
	}
	defer response.Body.Close()
	if err := checkResponse(req, response, serviceDescription); err != nil {
		return 0, err
	}
	return sessionID, nil
}

// Activate deployment with sessionID from a past prepare
func Activate(sessionID int64, deployment DeploymentOpts) error {
	if deployment.IsCloud() {
		return fmt.Errorf("activate is not supported with %s target", deployment.Target.Type())
	}
	u, err := deployment.url(fmt.Sprintf("/application/v2/tenant/default/session/%d/active", sessionID))
	if err != nil {
		return err
	}
	req, err := http.NewRequest("PUT", u.String(), nil)
	if err != nil {
		return err
	}
	serviceDescription := "Deploy service"
	response, err := util.HttpDo(req, time.Second*30, serviceDescription)
	if err != nil {
		return err
	}
	defer response.Body.Close()
	return checkResponse(req, response, serviceDescription)
}

func Deploy(opts DeploymentOpts) (int64, error) {
	path := "/application/v2/tenant/default/prepareandactivate"
	if opts.IsCloud() {
		if err := checkDeploymentOpts(opts); err != nil {
			return 0, err
		}
		if opts.Deployment.Zone.Environment == "" || opts.Deployment.Zone.Region == "" {
			return 0, fmt.Errorf("%s: missing zone", opts)
		}
		path = fmt.Sprintf("/application/v4/tenant/%s/application/%s/instance/%s/deploy/%s-%s",
			opts.Deployment.Application.Tenant,
			opts.Deployment.Application.Application,
			opts.Deployment.Application.Instance,
			opts.Deployment.Zone.Environment,
			opts.Deployment.Zone.Region)
	}
	u, err := opts.url(path)
	if err != nil {
		return 0, err
	}
	return uploadApplicationPackage(u, opts)
}

func copyToPart(dst *multipart.Writer, src io.Reader, fieldname, filename string) error {
	var part io.Writer
	var err error
	if filename == "" {
		part, err = dst.CreateFormField(fieldname)
	} else {
		part, err = dst.CreateFormFile(fieldname, filename)
	}
	if err != nil {
		return err
	}
	if _, err := io.Copy(part, src); err != nil {
		return err
	}
	return nil
}

func Submit(opts DeploymentOpts) error {
	if !opts.IsCloud() {
		return fmt.Errorf("%s: submit is unsupported", opts)
	}
	if err := checkDeploymentOpts(opts); err != nil {
		return err
	}
	path := fmt.Sprintf("/application/v4/tenant/%s/application/%s/submit", opts.Deployment.Application.Tenant, opts.Deployment.Application.Application)
	u, err := opts.url(path)
	if err != nil {
		return err
	}
	var body bytes.Buffer
	writer := multipart.NewWriter(&body)
	if err := copyToPart(writer, strings.NewReader("{}"), "submitOptions", ""); err != nil {
		return err
	}
	applicationZip, err := opts.ApplicationPackage.zipReader(false)
	if err != nil {
		return err
	}
	if err := copyToPart(writer, applicationZip, "applicationZip", "application.zip"); err != nil {
		return err
	}
	testApplicationZip, err := opts.ApplicationPackage.zipReader(true)
	if err != nil {
		return err
	}
	if err := copyToPart(writer, testApplicationZip, "applicationTestZip", "application-test.zip"); err != nil {
		return err
	}
	if err := writer.Close(); err != nil {
		return err
	}
	request := &http.Request{
		URL:    u,
		Method: "POST",
		Body:   ioutil.NopCloser(&body),
		Header: make(http.Header),
	}
	request.Header.Set("Content-Type", writer.FormDataContentType())
	signer := NewRequestSigner(opts.Deployment.Application.SerializedForm(), opts.APIKey)
	if err := signer.SignRequest(request); err != nil {
		return err
	}
	serviceDescription := "Submit service"
	response, err := util.HttpDo(request, time.Minute*10, serviceDescription)
	if err != nil {
		return err
	}
	defer response.Body.Close()
	return checkResponse(request, response, serviceDescription)
}

func checkDeploymentOpts(opts DeploymentOpts) error {
	if !opts.ApplicationPackage.HasCertificate() {
		return fmt.Errorf("%s: missing certificate in package", opts)
	}
	if opts.APIKey == nil {
		return fmt.Errorf("%s: missing api key", opts.String())
	}
	return nil
}

func uploadApplicationPackage(url *url.URL, opts DeploymentOpts) (int64, error) {
	zipReader, err := opts.ApplicationPackage.zipReader(false)
	if err != nil {
		return 0, err
	}
	header := http.Header{}
	header.Add("Content-Type", "application/zip")
	request := &http.Request{
		URL:    url,
		Method: "POST",
		Header: header,
		Body:   ioutil.NopCloser(zipReader),
	}
	if opts.APIKey != nil {
		signer := NewRequestSigner(opts.Deployment.Application.SerializedForm(), opts.APIKey)
		if err := signer.SignRequest(request); err != nil {
			return 0, err
		}
	}
	serviceDescription := "Deploy service"
	response, err := util.HttpDo(request, time.Minute*10, serviceDescription)
	if err != nil {
		return 0, err
	}
	defer response.Body.Close()

	var jsonResponse struct {
		SessionID string `json:"session-id"` // Config server
		RunID     int64  `json:"run"`        // Controller
	}
	jsonResponse.SessionID = "0" // Set a default session ID for responses that don't contain int (e.g. cloud deployment)
	if err := checkResponse(request, response, serviceDescription); err != nil {
		return 0, err
	}
	jsonDec := json.NewDecoder(response.Body)
	jsonDec.Decode(&jsonResponse) // Ignore error in case this is a non-JSON response
	if jsonResponse.RunID > 0 {
		return jsonResponse.RunID, nil
	}
	return strconv.ParseInt(jsonResponse.SessionID, 10, 64)
}

func checkResponse(req *http.Request, response *http.Response, serviceDescription string) error {
	if response.StatusCode/100 == 4 {
		return fmt.Errorf("Invalid application package (%s)\n\n%s", response.Status, extractError(response.Body))
	} else if response.StatusCode != 200 {
		return fmt.Errorf("Error from %s at %s (%s):\n%s", strings.ToLower(serviceDescription), req.URL.Host, response.Status, util.ReaderToJSON(response.Body))
	}
	return nil
}

func isZip(filename string) bool { return filepath.Ext(filename) == ".zip" }

func zipDir(dir string, destination string) error {
	if filepath.IsAbs(dir) {
		message := "Path must be relative, but '" + dir + "'"
		return errors.New(message)
	}
	if !util.PathExists(dir) {
		message := "'" + dir + "' should be an application package zip or dir, but does not exist"
		return errors.New(message)
	}
	if !util.IsDirectory(dir) {
		message := "'" + dir + "' should be an application package dir, but is a (non-zip) file"
		return errors.New(message)
	}

	file, err := os.Create(destination)
	if err != nil {
		message := "Could not create a temporary zip file for the application package: " + err.Error()
		return errors.New(message)
	}
	defer file.Close()

	w := zip.NewWriter(file)
	defer w.Close()

	walker := func(path string, info os.FileInfo, err error) error {
		if err != nil {
			return err
		}
		if info.IsDir() {
			return nil
		}
		file, err := os.Open(path)
		if err != nil {
			return err
		}
		defer file.Close()

		zippath := strings.TrimPrefix(path, dir)
		zipfile, err := w.Create(zippath)
		if err != nil {
			return err
		}

		_, err = io.Copy(zipfile, file)
		if err != nil {
			return err
		}
		return nil
	}
	return filepath.Walk(dir, walker)
}

// Returns the error message in the given JSON, or the entire content if it could not be extracted
func extractError(reader io.Reader) string {
	responseData, _ := ioutil.ReadAll(reader)
	var response map[string]interface{}
	json.Unmarshal(responseData, &response)
	if response["error-code"] == "INVALID_APPLICATION_PACKAGE" {
		return strings.ReplaceAll(response["message"].(string), ": ", ":\n")
	} else {
		var prettyJSON bytes.Buffer
		parseError := json.Indent(&prettyJSON, responseData, "", "    ")
		if parseError != nil { // Not JSON: Print plainly
			return string(responseData)
		}
		return prettyJSON.String()
	}
}
