# BoxLang Starter — Google Cloud Functions

## Project Overview

This is a **starter template** for deploying BoxLang code on Google Cloud Functions (Gen 2, Java 21 runtime). Developers clone this repo and add their own `.bx` handler files in `src/main/bx/`. The Java layer is thin infrastructure — most logic lives in BoxLang.

### Key Stack

- **Runtime**: `io.boxlang:boxlang-google-functions` (version in `gradle.properties`)
- **Entry point**: `ortus.boxlang.runtime.gcp.FunctionRunner` (from the runtime JAR — do not create this class here)
- **Handlers**: `src/main/bx/*.bx` — one `.bx` file per route
- **Tests**: `src/test/java/com/myproject/` using JUnit 5 + Google Truth

---

## Project Structure

```
src/
  main/bx/
    Application.bx      # Application lifecycle hooks (cold-start init)
    Lambda.bx           # Default handler — entry point for all unmatched routes
    <Route>.bx          # Additional route handlers (e.g. Products.bx, Customers.bx)
  test/java/com/myproject/
    FunctionRunnerTest.java         # Main integration test — tests src/main/bx handlers
    LambdaIntegrationTest.java      # Integration test for Lambda.bx specifically
    mocks/
      MockHttpRequest.java          # GCF HttpRequest test double
      MockHttpResponse.java         # GCF HttpResponse test double
    runner/
      LocalLambdaRunner.java        # Local CLI runner — uses FunctionRunner directly
workbench/
  sampleRequests/                   # JSON request body payloads for LocalLambdaRunner
gradle.properties                   # boxlangVersion, testPort (9099), debugMode
```

---

## BoxLang Handler Conventions

### Function Signature

Every `.bx` handler must expose a `run()` method (or any method callable via `x-bx-function` header):

```boxlang
class {
    function run( event, context, response ) {
        response.body       = { "error": false, "data": "..." }
        response.statusCode = 200
    }
}
```

### Route Resolution

The runtime maps the first URI path segment to a `.bx` file in PascalCase:
- `/` or no match → `Lambda.bx`
- `/products` or `/products/123` → `Products.bx`
- `/user-profiles` → `UserProfiles.bx`

Add new routes by creating `src/main/bx/<RouteName>.bx`.

### Response Struct

Always set `response.statusCode` and `response.body`. Do NOT use `return` for the main response — populate the `response` struct. Returning a value bypasses structured response mapping.

### No Semicolons

BoxLang does not require semicolons. **Do not add them** except in property declarations inside classes (`property name="x" type="string";`).

---

## Build & Run

**Key `gradle.properties` values:**

| Property | Default | Purpose |
|---|---|---|
| `boxlangVersion` | `1.12.0` | Runtime JAR version |
| `testPort` | `9099` | Local dev server port |
| `debugMode` | `true` | Verbose BoxLang output; also disables class cache |

**Key commands:**

```bash
./gradlew test                    # Run all tests
./gradlew cleanTest test          # Force a fresh test run
./gradlew runFunction             # Start local HTTP server on port 9099
./gradlew runFunction -PtestPort=8080 -PdebugMode=false
./gradlew shadowJar               # Build fat JAR
./gradlew buildLambdaZip          # Build deployable GCF zip
```

**GCF Deployment:**
- Entry point: `ortus.boxlang.runtime.gcp.FunctionRunner`
- Runtime: `java21`
- Upload the zip from `build/distributions/`

---

## Testing Conventions

### Always test `src/main/bx/` — not test fixtures

Tests in `FunctionRunnerTest` and `LambdaIntegrationTest` point at `src/main/bx/Lambda.bx`. **Never change the path to `src/test/resources/`** for integration tests — that's for unit tests of the runtime itself.

### Test setup pattern

```java
@TestInstance( TestInstance.Lifecycle.PER_CLASS )
public class FunctionRunnerTest {
    private static final Path LAMBDA_PATH = Path.of( "src", "main", "bx", "Lambda.bx" );
    private FunctionRunner runner;

    @BeforeAll
    void setUpRunner() {
        runner = new FunctionRunner( LAMBDA_PATH, true );
    }
}
```

### Request/response pattern

```java
MockHttpRequest  req = new MockHttpRequest( "POST", "/" )
    .withContentType( "application/json" )
    .withBody( "{\"name\":\"Ortus\"}" )
    .withHeader( "x-bx-function", "myMethod" );
MockHttpResponse res = new MockHttpResponse();

runner.service( req, res );

assertThat( res.getStatusCode() ).isEqualTo( 200 );
assertThat( res.getBody() ).contains( "Ortus" );
assertThat( res.getHeader( "Content-Type" ) ).isNotNull();
```

### Assertion library

Use **Google Truth** (`assertThat(...)`) — not JUnit `assertEquals`. Do not mix the two.

### One shared runner per test class

The BoxLang runtime is expensive to start. Use `@BeforeAll` + `@TestInstance(PER_CLASS)` to share a single `FunctionRunner` across all tests in a class. Only create a separate runner when testing a specific path or debug-mode behavior.

---

## Mock Classes

`MockHttpRequest` and `MockHttpResponse` live in `com.myproject.mocks` and implement the GCF `HttpRequest`/`HttpResponse` interfaces. They depend on `com.google.cloud.functions:functions-framework-api` which is declared as `testImplementation` in `build.gradle`.

**Do not reference** `ortus.boxlang.runtime.gcp.mocks` — that package is internal to the runtime JAR. Use `com.myproject.mocks` versions instead.

---

## Environment Variables (GCF)

| Variable | Purpose |
|---|---|
| `BOXLANG_GCP_ROOT` | Root directory for `.bx` files (default `/workspace`) |
| `BOXLANG_GCP_CLASS` | Override the default `Lambda.bx` path |
| `BOXLANG_GCP_DEBUGMODE` | Enable verbose logging; also skips class cache |
| `BOXLANG_GCP_CONFIG` | Path to `boxlang.json` config file |
| `K_SERVICE` | Function name (auto-set by GCF) |
| `K_REVISION` | Revision (auto-set by GCF) |
| `GOOGLE_CLOUD_PROJECT` | GCP project ID |

---

## What NOT to Do

- **Do not create `FunctionRunner.java`** — it comes from the runtime JAR dependency
- **Do not add AWS imports** (`com.amazonaws`, `LambdaRunner`, `TestContext`) — this is GCF, not AWS Lambda
- **Do not use `runner.handleRequest()`** — the GCF method is `runner.service(req, res)`
- **Do not test against `src/test/resources/Lambda.bx`** for integration tests — use `src/main/bx/`
- **Do not import `ortus.boxlang.runtime.gcp.mocks`** in test code — use `com.myproject.mocks`
