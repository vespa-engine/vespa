# Vespa Testcontainers
[Testcontainers](https://testcontainers.com/) integration for Vespa.

## Running tests
To successfully pass the unit tests, Docker or Podman is required.

> [!NOTE]
> When using Podman instead of Docker, set
> ```bash
> export DOCKER_HOST="unix://"$(podman machine inspect --format {{.ConnectionInfo.PodmanSocket.Path}})
> export TESTCONTAINERS_RYUK_DISABLED=true
> ```
> before running unit tests.

## Installation
**Gradle:**
```groovy
testImplementation 'ai.vespa:vespa-testcontainers:1.0.0'
```
**Maven:**
```xml
<dependency>
    <groupId>ai.vespa</groupId>
    <artifactId>vespa-testcontainers</artifactId>
    <version>8-SNAPSHOT</version>
    <scope>test</scope>
</dependency>
```
## Example Usage
```java
import ai.vespa.testcontainers.VespaContainer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;


class MySearchTest {
    static VespaContainer vespa;
    // Other containers etc.

    @BeforeAll
    static void setUp() {
        vespa = new VespaContainer().withApplicationPackage("app");
        vespa.start();
        // feed documents, set up clients, etc.
    }

    @Test
    void testOne() { ... }

    @Test
    void testTwo() { ... }

    @AfterAll
    static void tearDown() {
        vespa.close();
        // Close other containers, clients, etc.
    }
}
```

A specific Vespa version can be requested:

```java
new VespaContainer("vespaengine/vespa:8.640.27")
```

[//]: # (See the [book-search]&#40;https://github.com/vespa-engine/sample-apps/tree/edvardwd/book-search/examples/book-search&#41; sample app for a complete example with feeding and querying.)

## API
| Method | Description |
|--------|-------------|
| `withApplicationPackage(String)` | Classpath resource path to app package, deployed on startup |
| `withApplicationPackage(Path)` | Host path to app package, deployed on startup |
| `withDeployWaitTime(Duration)` | Override default 5-minute deploy timeout |
| `deployApplicationPackage(String)` | Copy and deploy an app package on a running container |
| `getEndpoint()` | Base URL of the query/document API (`http://host:8080`) |
| `getConfigEndpoint()` | Base URL of the config server (`http://host:19071`) |

