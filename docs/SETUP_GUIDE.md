# Setup guide (one-time)

## 1. Edit two lines in the `Jenkinsfile`

```groovy
DOCKERHUB_USER = 'YOUR_DOCKERHUB_USERNAME'
NOTIFY_EMAIL   = 'YOUR_EMAIL@example.com'
```

## 2. GitHub repository and Git history

The rubric asks for real history, at least one feature branch and a merge or pull request.
Suggested flow:

```bash
git init -b main
git add pom.xml .gitignore src/main README.md
git commit -m "Add calculator REST API in plain Java"
git add src/test
git commit -m "Add JUnit unit and integration tests"
git add Dockerfile .dockerignore
git commit -m "Add multi-stage Dockerfile"
git add docker-compose.yml nginx
git commit -m "Add Docker Compose stack with nginx reverse proxy"
git remote add origin https://github.com/<you>/calculator-devops.git
git push -u origin main

# feature branch -> pull request
git checkout -b feature/jenkins-pipeline
git add Jenkinsfile docs
git commit -m "Add Jenkins CI/CD pipeline and setup guide"
git push -u origin feature/jenkins-pipeline
# open a Pull Request on GitHub, merge it, then:
git checkout main && git pull
```

## 3. Docker Hub

1. Create a **public** repository named `calculator-api`.
2. Account Settings → Personal access tokens → create a token with Read & Write access.
   Use this token as the password in Jenkins, not your real password.

## 4. Jenkins (on the same Mac as Docker)

```bash
brew install jenkins-lts
brew services start jenkins-lts      # opens on http://localhost:8080
```

1. Install the suggested plugins. Also make sure these are installed: **Pipeline**,
   **Git**, **GitHub**, **JUnit**, **Credentials Binding**, **Mailer**, **Timestamper**.
2. **Docker Hub credential:** Manage Jenkins → Credentials → (global) → Add →
   *Username with password*. Enter your Docker Hub username and the token,
   and set **ID = `dockerhub-creds`** (the Jenkinsfile uses this exact ID).
3. **Email (Gmail):** Manage Jenkins → System → *E-mail Notification*:
   - SMTP server `smtp.gmail.com`, Use SMTP Authentication, Use SSL, port `465`
   - username = your Gmail address, password = a Gmail **App Password**
     (Google Account → Security → 2-Step Verification → App passwords)
   - Also set *System Admin e-mail address* (top of the page) to the same Gmail address.
   - Click *Test configuration* to check it.
4. **Job:** New Item → *Pipeline* → name `calculator-devops`.
   - Pipeline → Definition: *Pipeline script from SCM* → Git → your repo URL, branch `*/main`,
     Script Path `Jenkinsfile`.
   - Save, then click **Build Now** once. Jenkins reads the `triggers {}` block on the first
     run; after that, the webhook trigger and polling are both active.

## 5. GitHub webhook (the brief prefers this; polling is the fallback)

GitHub cannot reach `localhost`, so expose Jenkins with ngrok (free account needed):

```bash
brew install ngrok
ngrok http 8080
```

- GitHub repo → Settings → Webhooks → Add: Payload URL `https://<ngrok-id>.ngrok-free.app/github-webhook/`,
  content type `application/json`, *Just the push event*.
- The Jenkinsfile's `githubPush()` already ticks **GitHub hook trigger for GITScm polling** in the job
  after the first build. You don't need to change any job settings.
- Check it works: in the webhook's *Recent Deliveries* tab you should see a green ✓. If ngrok
  restarts with a new URL, update the webhook; polling keeps the pipeline working meanwhile.

## 6. Screenshots for the report

- [ ] Green pipeline run (Stage View showing all 6 stages)
- [ ] Test Result page (JUnit results published)
- [ ] The success email in your inbox
- [ ] `docker ps` with both containers `(healthy)`
- [ ] `curl` output for add, divide, and divide-by-zero (400)
- [ ] Docker Hub repo page showing the build-number tags

## 7. Live demo script (Class 17)

1. Show `docker compose ps` (stack running) and `curl "localhost:9090/api/v1/add?a=2&b=3"`.
2. Make a visible change, e.g. in `CalculatorServer.java` change the health body to
   `{"status":"UP","version":"2"}` and update the expected string in the integration test.
3. `git commit -am "Show version in health" && git push`
4. Watch Jenkins pick it up and run every stage, then `curl localhost:9090/actuator/health`
   shows the new version. Point out the new tag on Docker Hub and the email.
5. Optional: push a broken test to show the build failing and the failure email arriving,
   while the old deployment keeps running.

## 8. Things you may be asked to explain

- **Why no Spring Boot?** The JDK's `HttpServer` is enough for 5 GET endpoints. The pipeline is the
  same either way: Maven builds a JAR, tests produce JUnit XML, and Docker runs `java -jar`.
- **Why multi-stage?** The build stage has Maven and the full JDK (large). The runtime stage has only
  the JRE and the JAR, so the image is smaller and has less attack surface.
- **Why copy `pom.xml` before `src`?** Docker caches layers. Dependencies are only re-downloaded
  when `pom.xml` changes, not on every code change.
- **Why `resolver 127.0.0.11` in nginx?** On redeploy the calculator container gets a new IP.
  Without re-resolving the name, nginx would keep the old IP and return 502 errors.
- **Why `name: calculator-devops` in compose?** Jenkins runs from its own workspace folder. A fixed
  project name means Jenkins and your terminal manage the *same* stack, so there are no duplicate
  containers fighting over port 9090.
- **How is the password kept secret?** It lives in the Jenkins credential store. `withCredentials`
  injects it and masks it in logs. It goes to `docker login` via `--password-stdin`, and a temporary
  `DOCKER_CONFIG` means nothing stays on disk afterwards.
- **Idempotent?** `docker compose up -d` compares desired and actual state and only changes the
  difference. `disableConcurrentBuilds()` stops two deployments from overlapping.
- **Port 9090, not 8080?** Jenkins already uses 8080.
