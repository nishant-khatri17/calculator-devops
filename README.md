# Calculator REST API — CI/CD with Jenkins, Docker & Docker Compose

A small calculator REST API in **plain Java** (no framework — it uses the JDK's built-in
`com.sun.net.httpserver.HttpServer`), wrapped in a fully automated pipeline:

```
git push ──► GitHub ──► Jenkins: Checkout → Build → Test → Docker Build → Push → Deploy ──► email
                                                              │              │
                                                         Docker Hub    docker compose up -d
                                                                        (nginx :9090 → calculator :8080)
```

## API

| Operation | Endpoint | Example response |
|-----------|----------|------------------|
| Add       | `GET /api/v1/add?a=2&b=3`      | `{"operation":"add","a":2,"b":3,"result":5}` |
| Subtract  | `GET /api/v1/subtract?a=2&b=3` | `{"operation":"subtract","a":2,"b":3,"result":-1}` |
| Multiply  | `GET /api/v1/multiply?a=3&b=4` | `{"operation":"multiply","a":3,"b":4,"result":12}` |
| Divide    | `GET /api/v1/divide?a=10&b=4`  | `{"operation":"divide","a":10,"b":4,"result":2.5}` |
| Health    | `GET /actuator/health`         | `{"status":"UP"}` |

Errors return **HTTP 400** with a JSON body, e.g. dividing by zero:

```json
{"status":400,"error":"Bad Request","message":"Division by zero is not allowed"}
```

Other errors: a missing parameter (`Missing required parameter 'b'`), a non-numeric value
(`Parameter 'a' must be a valid number`), an unknown path (404), and a non-GET method (405).
Numbers use `BigDecimal`, so `0.1 + 0.2` gives exactly `0.3`.

## Project layout

```
├── src/main/java/com/spe/calculator/
│   ├── Calculator.java          # the maths (add/subtract/multiply/divide)
│   ├── CalculatorServer.java    # HTTP endpoints, validation, main()
│   └── Json.java                # builds the JSON responses
├── src/test/java/com/spe/calculator/
│   ├── CalculatorTest.java                    # JUnit 5 unit tests
│   └── CalculatorServerIntegrationTest.java   # starts the real server, calls it over HTTP
├── pom.xml                # Maven build → target/calculator-api.jar
├── Dockerfile             # multi-stage: Maven/JDK build → slim JRE runtime
├── .dockerignore
├── docker-compose.yml     # calculator + nginx reverse proxy
├── nginx/default.conf
├── Jenkinsfile            # declarative CI/CD pipeline
└── docs/SETUP_GUIDE.md    # Jenkins / GitHub / Docker Hub setup + demo script
```

## Build, test, run

Requirements: Java 21+, Maven 3.9+, Docker.

```bash
mvn clean package          # compile, run tests, build target/calculator-api.jar
mvn test                   # tests only; JUnit XML reports land in target/surefire-reports/
java -jar target/calculator-api.jar        # runs on http://localhost:8080 (set PORT to change)
```

### Docker (single container)

```bash
docker build -t calculator-api .
docker run --rm -p 8080:8080 calculator-api
curl "http://localhost:8080/api/v1/add?a=2&b=3"
```

### Docker Compose (full stack)

```bash
docker compose up -d --build     # nginx on http://localhost:9090 → calculator
docker compose ps                # both containers should show (healthy)
curl "http://localhost:9090/api/v1/divide?a=10&b=4"
docker compose down
```

On a fresh machine, Compose first prints `pull access denied for local/calculator-api`.
That is expected: the image is not on Docker Hub under that name, so Compose builds it from the `Dockerfile`.

## CI/CD pipeline (Jenkinsfile)

| Stage | What it does |
|-------|--------------|
| Checkout     | `checkout scm` pulls the commit that triggered the build |
| Build        | `mvn clean package -DskipTests` |
| Test         | `mvn test`, publishes the JUnit XML; a failing test fails the build |
| Docker Build | builds `<user>/calculator-api:<BUILD_NUMBER>` and `:latest` |
| Push         | logs in to Docker Hub with the Jenkins credential `dockerhub-creds` and pushes both tags |
| Deploy       | `docker compose up -d`, then polls `/actuator/health` until it reports `UP` |
| post         | sends an email on success or failure |

A GitHub webhook triggers the pipeline, with Poll SCM every 2 minutes as a fallback. Re-running the pipeline is safe:
`docker compose up -d` only recreates containers that changed, and builds never run in parallel.

See **[docs/SETUP_GUIDE.md](docs/SETUP_GUIDE.md)** for the one-time Jenkins setup.
