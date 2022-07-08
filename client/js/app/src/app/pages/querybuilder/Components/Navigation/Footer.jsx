import React from 'react';
import { Table } from 'react-bootstrap';

export default function Footer() {
  return (
    <footer>
      <Table borderless size="sm">
        <thead className="footer-title">
          <tr>
            <th>Resources</th>
            <th>Contact</th>
            <th>Community</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <th>
              <a href="https://docs.vespa.ai/en/vespa-quick-start.html">
                Getting Started
              </a>
            </th>
            <th>
              <a href="https://twitter.com/vespaengine">Twitter</a>
            </th>
            <th>
              <a href="https://github.com/vespa-engine/vespa/blob/master/CONTRIBUTING.md">
                Contributing
              </a>
            </th>
          </tr>
          <tr>
            <th>
              <a href="https://docs.vespa.ai">Documentation</a>
            </th>
            <th>
              <a href="mailto:info@vespa.ai">info@vespa.ai</a>
            </th>
            <th>
              <a href="https://stackoverflow.com/questions/tagged/vespa">
                Stack Overflow
              </a>
            </th>
          </tr>
          <tr>
            <th>
              <a href="https://github.com/vespa-engine/vespa">Open source</a>
            </th>
            <th>
              <a href="https://github.com/vespa-engine/vespa/issues">Issues</a>
            </th>
            <th>
              <a href="https://gitter.im/vespa-engine/Lobby">Gitter</a>
            </th>
          </tr>
        </tbody>
      </Table>
      <div className="credits">
        <span>Copyright Yahoo</span>
        Licensed under{' '}
        <a href="https://github.com/vespa-engine/vespa/blob/master/LICENSE">
          Apache License 2.0
        </a>
        , <a href="https://github.com/y7kim/agency-jekyll-theme">Theme</a> by
        Rick K.
      </div>
    </footer>
  );
}
