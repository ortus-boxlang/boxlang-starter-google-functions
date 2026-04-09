# BoxLang Starter for Google Cloud Functions

Production-ready starter template for building and deploying BoxLang handlers on Google Cloud Functions (Gen 2, Java 21).

This project uses the BoxLang Google Functions runtime and gives you a thin Java/Gradle wrapper so your app logic stays in BoxLang files under `src/main/bx`.

## What This Starter Includes

- Google Cloud Functions runtime via `io.boxlang:boxlang-google-functions`
- Convention-based BoxLang handlers in `src/main/bx`
- Local HTTP execution via the official GCF Java Function Invoker
- JUnit + Google Truth integration tests
- Deployable ZIP package generation

## Prerequisites

- Java 21+
- Google Cloud SDK (`gcloud`) for deployment
- A Google Cloud project with billing enabled

## Project Layout

```text
src/
  main/
    bx/
      Application.bx
      Lambda.bx
  resources/
    boxlang.json
    boxlang_modules/
  test/
    java/com/myproject/
workbench/
  sampleRequests/
    event-local.json
build.gradle
gradle.properties
```

## Quick Start (Local Launch)

This is the fastest path to running your function locally.

1. Install dependencies and compile:

```bash
./gradlew clean test
```

1. Start the local function server (default port `9099`):

```bash
./gradlew runFunction
```

1. In another terminal, call the function:

```bash
curl http://localhost:9099/
```

1. Call a specific BoxLang method using the `x-bx-function` header:

```bash
curl -H "x-bx-function: anotherLambda" http://localhost:9099/
```

You can also send JSON:

```bash
curl -X POST http://localhost:9099/ \
  -H "Content-Type: application/json" \
  -d @workbench/sampleRequests/event-local.json
```

## How Routing Works

The runtime routes based on the first URI segment:

- `/` -> `Lambda.bx`
- `/customers` -> `Customers.bx`
- `/products/123` -> `Products.bx`

To add a route, create a matching PascalCase BoxLang file in `src/main/bx`.

Example: `src/main/bx/Customers.bx`

```js
class {
    function run( event, context, response ) {
        response.statusCode = 200
        response.body = {
            "error": false,
            "data": [ "Customer A", "Customer B" ]
        }
    }
}
```

## BoxLang Handler Contract

Each handler method receives:

- `event`: mapped HTTP request data
- `context`: GCF metadata (function name, project, request id, etc.)
- `response`: mutable response struct (`statusCode`, `headers`, `body`, `cookies`)

Default entry method is `run()`.

Current sample in `src/main/bx/Lambda.bx`:

```js
class{

	function run( event, context, response ){
		response.body = {
			"error": false,
			"messages": [],
			"data": "====> Incoming event " & event.toString()
		}
		response.statusCode = 200
	}

	function anotherLambda( event, context, response ){
		return "Hola!!"
	}
}
```

## Build Artifacts

Create a deployable package:

```bash
./gradlew shadowJar buildLambdaZip
```

Output:

- `build/distributions/boxlang-google-function-project-<version>.zip`

The ZIP includes:

- BoxLang handlers (`.bx`) at the package root
- `boxlang.json`
- `boxlang_modules/`
- `lib/` with the runtime JAR and dependencies

## Deploy to Google Cloud Functions (Gen 2)

1. Authenticate and select your project:

```bash
gcloud auth login
gcloud config set project YOUR_PROJECT_ID
```

1. Build the deployment ZIP:

```bash
./gradlew clean shadowJar buildLambdaZip
```

1. Deploy:

```bash
gcloud functions deploy YOUR_FUNCTION_NAME \
  --gen2 \
  --runtime=java21 \
  --region=us-central1 \
  --entry-point=ortus.boxlang.runtime.gcp.FunctionRunner \
  --trigger-http \
  --allow-unauthenticated \
  --source=build/distributions/boxlang-google-function-project-1.0.0.zip
```

If you changed `version` in `gradle.properties`, update the ZIP filename in `--source` accordingly.

## Configuration

Key `gradle.properties` values:

- `boxlangVersion=1.12.0`
- `jdkVersion=21`
- `testPort=9099`
- `debugMode=true`

Run locally with overrides:

```bash
./gradlew runFunction -PtestPort=8080 -PdebugMode=false
```

Runtime environment variables (optional):

- `BOXLANG_GCP_ROOT`: root directory for `.bx` handlers
- `BOXLANG_GCP_CLASS`: override default handler path
- `BOXLANG_GCP_DEBUGMODE`: enable verbose runtime logging
- `BOXLANG_GCP_CONFIG`: custom `boxlang.json` path

## Testing

Run all tests:

```bash
./gradlew test
```

Run only the main integration test class:

```bash
./gradlew test --tests "com.myproject.FunctionRunnerTest"
```

Test report:

- `build/reports/tests/test/index.html`

## Common Commands

```bash
./gradlew clean test                 # clean + run tests
./gradlew runFunction                # start local HTTP function server
./gradlew shadowJar buildLambdaZip   # build deployable artifacts
./gradlew build                      # full build lifecycle
```

## Troubleshooting

- Port in use: run with a different port using `-PtestPort=8080`
- Java mismatch: verify `java -version` is 21+
- Function not behaving as expected: run with `-PdebugMode=true`
- Deploy source not found: confirm ZIP exists under `build/distributions/`

## Next Steps

1. Replace `src/main/bx/Lambda.bx` with your own handler logic.
2. Add route-specific handlers (for example, `Customers.bx`, `Products.bx`).
3. Deploy to GCF with the command above.
4. Add CI/CD pipeline automation once your deployment flow is validated.
