import React from 'react';
import { Navbar, Container, Nav } from 'react-bootstrap';

function CustomNavbar() {
  return (
    <Navbar collapseOnSelect variant="default" expand="sm">
      <Container>
        <Navbar.Brand href="https://vespa.ai">
          Vespa. Big data. Real time.
        </Navbar.Brand>
        <Navbar.Toggle aria-controls="responsive-navbar-nav" />
        <Navbar.Collapse id="responsive-navbar-nav">
          <Nav>
            <Nav.Link href="https://blog.vespa.ai/">Blog</Nav.Link>
            <Nav.Link variant="link" href="https://twitter.com/vespaengine">
              Twitter
            </Nav.Link>
            <Nav.Link variant="link" href="https://docs.vespa.ai">
              Docs
            </Nav.Link>
            <Nav.Link variant="link" href="https://github.com/vespa-engine">
              GitHub
            </Nav.Link>
            <Nav.Link
              variant="link"
              href="https://docs.vespa.ai/en/getting-started.html"
            >
              Get Started Now
            </Nav.Link>
          </Nav>
        </Navbar.Collapse>
      </Container>
    </Navbar>
  );
}

export default CustomNavbar;
