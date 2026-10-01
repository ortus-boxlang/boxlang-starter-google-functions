# ⚡︎ BoxLang Google Cloud Functions Starter Template

```
|:------------------------------------------------------:|
| ⚡︎ B o x L a n g ⚡︎
| Dynamic : Modular : Productive
|:------------------------------------------------------:|
```

<blockquote>
	Copyright Since 2023 by Ortus Solutions, Corp
	<br>
	<a href="https://www.boxlang.io">www.boxlang.io</a> |
	<a href="https://www.ortussolutions.com">www.ortussolutions.com</a>
</blockquote>

<p>&nbsp;</p>

## 🚀 Welcome

This is the starter template for building **BoxLang serverless applications on Google Cloud Functions** (Gen 2, Java 21). It bootstraps everything you need: the [BoxLang Google Functions runtime](https://github.com/ortus-boxlang/boxlang-google-functions), the convention-based `handlers/` routing setup, a Gradle build with a local HTTP invoker, and a ready-to-run test suite.

> 💡 This template is intentionally structured the same way as our [AWS Lambda](https://github.com/ortus-boxlang/boxlang-starter-aws-lambda) and [Azure Functions](https://github.com/ortus-boxlang/boxlang-starter-azure-functions) starter templates. Your `.bx` handler code can move between all three providers unmodified - only the deployment step differs.

## 📦 What This Starter Includes

- The BoxLang Google Functions runtime, pre-wired as your GCF entry point
- Convention-based `handlers/` routing, backed by a build-time `manifest.json`
- A Gradle build with a shaded JAR, `buildLambdaZip` packaging, and the official GCF Java Function Invoker for local HTTP testing
- JUnit + Google Truth integration tests that exercise the full request pipeline
- Ready-to-use GitHub Actions workflows for test, snapshot, and release builds

## 📋 Prerequisites

- **Java 21+**
- **Google Cloud SDK** (`gcloud`), authenticated - for deployment
- A Google Cloud project with billing enabled

## 🏗️ Project Structure

```
.
├── build.gradle                    # Gradle build, generateManifest task, shadowJar/buildLambdaZip
├── src/
│   ├── main/
│   │   └── bx/
│   │       ├── Application.bx      # Application lifecycle hooks
│   │       ├── Lambda.bx           # Default handler (fallback for unmatched routes)
│   │       └── handlers/
│   │           ├── Products.bx     # Routed handler -> /products
│   │           └── api/
│   │               └── Test.bx     # Nested routed handler -> /api/test
│   ├── resources/
│   │   ├── boxlang.json            # BoxLang runtime configuration
│   │   └── boxlang_modules/        # Local BoxLang modules (auto-packaged)
│   └── test/
│       └── java/com/myproject/     # JUnit integration tests + mocks
├── workbench/
│   └── sampleRequests/             # Sample event payloads for local testing
├── .github/workflows/              # Test, snapshot, and release CI/CD pipelines
├── box.json                        # BoxLang module dependencies
└── gradle.properties               # version, jdkVersion, boxlangVersion, testPort
```

## 🧭 URI Routing with `handlers/`

Only files under `src/main/bx/handlers/` (or listed in the build-time-generated `manifest.json`) are ever reachable by URI. `Application.bx` and the default `Lambda.bx` are never routable, no matter what's on disk.

| Incoming URI | Handler File |
|---|---|
| `/products` | `handlers/Products.bx` |
| `/api/test` | `handlers/api/Test.bx` |
| `/user-profiles` | `handlers/UserProfiles.bx` (hyphens map to PascalCase) |
| `/` or anything unmatched | `Lambda.bx` (the default handler) |

Add a new route by creating a `.bx` file under `handlers/`:

```boxlang
// src/main/bx/handlers/Customers.bx
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

`./gradlew generateManifest` scans `handlers/` and writes `src/main/bx/manifest.json` - it's wired via `dependsOn` into `test`, `runFunction`, and `buildLambdaZip`, so it's always regenerated fresh and can never silently drift. `manifest.json` is gitignored, never hand-edited or committed.

If `manifest.json` is ever missing or invalid, the runtime falls back to scanning `handlers/` directly, and if that directory doesn't exist either, to scanning the function root for backward compatibility with pre-`handlers/` deployments; set `BOXLANG_ENABLE_ROOT_SCAN=false` to disable that last-resort scan entirely and restrict routing to the default `Lambda.bx` handler only.

`manifest.json`'s `reserved` and `defaultHandler` fields are enforced by the runtime, not just documentation - a manifest can never route to a reserved file (`Application.bx`, `Lambda.bx`, or anything else it lists), and `defaultHandler.file`/`method` is honored as the fallback handler for unmatched routes when present.

As with `Lambda.bx`, the `x-bx-function` header can call an alternative method on a handler - only ever a method you declared, since BoxLang's public/remote scope rules are exactly what gates it: don't make a method public if you don't want it externally callable.

## 🔧 Application Lifecycle

Use `src/main/bx/Application.bx` for initialization and per-request hooks. It fires for every request, whether served by `Lambda.bx` or by a routed handler under `handlers/`:

```java
class {
    this.name = "My-Google-Cloud-Function"

    function onApplicationStart() {
        // Initialize databases, caches, etc. - runs once, on cold start
        return true;
    }

    function onRequestStart( targetPage ) {
        // Per-request initialization
        return true;
    }
}
```

`run()` and every request lifecycle hook (`onRequestStart`, `onRequestEnd`, `onError`, `onAbort`) receive the same `response` struct as their last argument. A returned value is stored in `response.body` before `onRequestEnd` runs, so a hook can wrap it, and a handled error defaults to status `500` unless `onError` sets one:

```js
class {

    function onRequestEnd( target, event, context, response ) {
        response.body = { ok: true, data: response.body }
    }

    function onError( exception, eventName, event, context, response ) {
        response.body = { ok: false, error: exception.message }
    }

}
```

If `Application.bx` defines `onError`, the error counts as handled; rethrow from the hook to fail the invocation.

## 📋 Handler Contract

Every handler - `Lambda.bx` or anything under `handlers/` - implements `run( event, context, response )` (or an alternate method called via the `x-bx-function` header):

```boxlang
class{
    function run( event, context, response ){
        response.body = {
            "error": false,
            "messages": [],
            "data": "Incoming event: " & event.toString()
        }
        response.statusCode = 200
    }

    // Call with header: x-bx-function: anotherLambda
    function anotherLambda( event, context, response ){
        return "Hola!!"
    }
}
```

- **`event`** - mapped HTTP request data (method, path, headers, body, query string)
- **`context`** - GCF metadata (function name, project, request id, etc.)
- **`response`** - the struct returned to the caller, with a standard shape: `statusCode` (default `200`), `headers`, `body`, `cookies` (array), plus any other property you add

You can either populate `response` or simply `return` a value - both are auto-serialized to JSON.

## 🛠️ Local Development

```bash
./gradlew clean test                          # run the test suite
./gradlew runFunction                         # start the local server (default port 9099)
./gradlew runFunction -PtestPort=8080         # custom port
./gradlew runFunction -PdebugMode=true        # disable class cache for hot reload
```

```bash
curl http://localhost:9099/
curl -H "x-bx-function: anotherLambda" http://localhost:9099/
curl -X POST http://localhost:9099/ \
  -H "Content-Type: application/json" \
  -d @workbench/sampleRequests/event-local.json
```

## 🧪 Testing

```bash
./gradlew test
```

Tests in `src/test/java/com/myproject/` exercise the full request pipeline using `FunctionRunner` directly with mock request/context objects - no live GCP environment required, so they run fast in CI. Test report: `build/reports/tests/test/index.html`.

Run only the main integration test class:

```bash
./gradlew test --tests "com.myproject.FunctionRunnerTest"
```

## 🔨 Build Tasks

| Task | Description |
|---|---|
| `build` | Full build lifecycle (clean, compile, test, package) |
| `test` | Run the JUnit test suite |
| `generateManifest` | Scan `handlers/` and (re)generate `manifest.json` |
| `shadowJar` | Create the uber-JAR with all dependencies |
| `buildLambdaZip` | Package the GCF deployment ZIP (`build/distributions/*.zip`) |
| `runFunction` | Start the local HTTP function server |
| `spotlessApply` / `spotlessCheck` | Auto-format / check Java source formatting |

## ☁️ Deploy to Google Cloud Functions (Gen 2)

```bash
# Authenticate and select your project
gcloud auth login
gcloud config set project YOUR_PROJECT_ID

# Build the deployment ZIP
./gradlew clean shadowJar buildLambdaZip

# Deploy
gcloud functions deploy YOUR_FUNCTION_NAME \
  --gen2 \
  --runtime=java21 \
  --region=us-central1 \
  --entry-point=ortus.boxlang.runtime.gcp.FunctionRunner \
  --trigger-http \
  --allow-unauthenticated \
  --source=build/distributions/boxlang-google-function-project-1.0.0.zip
```

If you changed `version` in `gradle.properties`, update the ZIP filename in `--source` accordingly. The ZIP includes your BoxLang handlers at the package root, `boxlang.json`, `boxlang_modules/`, and `lib/` with the runtime JAR and dependencies.

## 🤖 CI/CD Workflows

`.github/workflows/` ships three ready-to-use pipelines:

| Workflow | Trigger | Purpose |
|---|---|---|
| `tests.yml` | Called by the other workflows | Reusable Java 21 test run with report artifacts |
| `snapshot.yml` | Push to any non-`main` branch, PRs | Development builds with snapshot versioning |
| `release.yml` | Push to `main`, manual dispatch | Full build, test, and package; optional Google Cloud deployment (commented out by default) |

To enable automatic GCF deployment on release: deploy your function once via the `gcloud` command above, add your GCP service account credentials as GitHub Secrets, then uncomment the deployment step in `release.yml`.

## ⚙️ Configuration

### `boxlang.json`

`src/resources/boxlang.json` controls BoxLang runtime behavior: class-generation caching (`trustedCache`), debug mode, logging, request timeouts, and more. Set `trustedCache: false` and `debugMode: true` for local development; flip both for production.

### Environment Variables

| Variable | Description | Default |
|---|---|---|
| `BOXLANG_GCP_ROOT` | Root directory for `.bx` files | `/workspace` |
| `BOXLANG_GCP_CLASS` | Override the default handler path | *(unset)* |
| `BOXLANG_GCP_DEBUGMODE` | Verbose logging, disables class caching | `false` |
| `BOXLANG_GCP_CONFIG` | Path to a custom `boxlang.json` | `boxlang.json` in root |
| `BOXLANG_ENABLE_ROOT_SCAN` | Allow the legacy root-directory routing fallback (see URI Routing above) | `true`. Shared across every BoxLang serverless runtime (AWS/GCP/Azure). |

## 📦 Adding BoxLang Modules

```bash
box install {moduleName} --production --directory=src/resources/boxlang_modules
```

Or declare them in `box.json` under `dependencies`/`installPaths` and run `box install --production`. Modules are automatically packaged into your deployment ZIP under `boxlang_modules/`.

## 🐛 Troubleshooting

| Problem | Solution |
|---|---|
| Port already in use | Run with a different port: `-PtestPort=8080` |
| Java mismatch | Verify `java -version` is 21+ |
| Function not behaving as expected | Run with `-PdebugMode=true` for verbose logging and no class caching |
| Deploy source not found | Confirm the ZIP exists under `build/distributions/` |
| Routing looks off after a deploy | Check your function logs for a manifest `WARNING`; confirm `generateManifest` ran |

## 📚 Additional Resources

- **BoxLang Google Functions Runtime** - [boxlang-google-functions](https://github.com/ortus-boxlang/boxlang-google-functions)
- **BoxLang Documentation** - [boxlang.ortusbooks.com](https://boxlang.ortusbooks.com)
- **AWS Lambda Starter** - [boxlang-starter-aws-lambda](https://github.com/ortus-boxlang/boxlang-starter-aws-lambda)
- **Azure Functions Starter** - [boxlang-starter-azure-functions](https://github.com/ortus-boxlang/boxlang-starter-azure-functions)

## License

Apache License, Version 2.0.

## Open-Source & Professional Support

This project is a professional open source project and is available as FREE and open source to use.  Ortus Solutions, Corp provides commercial support, training and commercial subscriptions which include the following:

- Professional Support and Priority Queuing
- Remote Assistance and Troubleshooting
- New Feature Requests and Custom Development
- Custom SLAs
- Application Modernization and Migration Services
- Performance Audits
- Enterprise Modules and Integrations
- Much More

https://www.boxlang.io/plans

<p>&nbsp;</p>

<blockquote>
"We ❤️ Open Source and BoxLang" - Luis Majano
</blockquote>

### THE DAILY BREAD

> "I am the way, and the truth, and the life; no one comes to the Father, but by me (JESUS)" Jn 14:1-12
