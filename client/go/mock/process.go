package mock

import "fmt"

type Exec struct {
	ProgramPath    string
	CombinedOutput string
}

func (c *Exec) LookPath(name string) (string, error) {
	if c.ProgramPath == "" {
		return "", fmt.Errorf("no program path set in this mock")
	}
	return c.ProgramPath, nil
}

func (c *Exec) Run(name string, args ...string) ([]byte, error) {
	return []byte(c.CombinedOutput), nil
}
