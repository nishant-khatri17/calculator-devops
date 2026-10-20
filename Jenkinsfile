pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()                       // two runs never fight over the same deployment
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }

    // githubPush(): build instantly when the GitHub webhook fires (see docs/SETUP_GUIDE.md).
    // pollSCM: fallback that checks GitHub every ~2 minutes if no webhook is set up.
    triggers {
        githubPush()
        pollSCM('H/2 * * * *')
    }

    environment {
        // ---- CHANGE THESE TWO ----
        DOCKERHUB_USER = 'nishantkhatri17'
        NOTIFY_EMAIL   = 'khatrinishant36@gmail.com'
        // --------------------------
        IMAGE_NAME = "${DOCKERHUB_USER}/calculator-api"
        IMAGE_TAG  = "${BUILD_NUMBER}"
        // Jenkins started as a service often has a short PATH; add mvn and docker locations.
        PATH = "/opt/homebrew/bin:/usr/local/bin:/Applications/Docker.app/Contents/Resources/bin:${env.PATH}"
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build') {
            steps {
                sh 'mvn -B clean package -DskipTests'
            }
        }

        stage('Test') {
            steps {
                sh 'mvn -B test'            // a failing test fails the build
            }
            post {
                always {
                    junit testResults: 'target/surefire-reports/*.xml', allowEmptyResults: true
                }
            }
        }

        stage('Docker Build') {
            steps {
                sh 'docker build -t ${IMAGE_NAME}:${IMAGE_TAG} -t ${IMAGE_NAME}:latest .'
            }
        }

        stage('Push') {
            steps {
                // 'dockerhub-creds' = Username/Password credential stored in Jenkins
                withCredentials([usernamePassword(credentialsId: 'dockerhub-creds',
                                                  usernameVariable: 'DH_USER',
                                                  passwordVariable: 'DH_PASS')]) {
                    sh '''
                        # remember which Docker engine we talk to, then use a temporary
                        # docker config so the login is not kept after the build
                        export DOCKER_HOST=$(docker context inspect --format '{{.Endpoints.docker.Host}}')
                        export DOCKER_CONFIG=$(mktemp -d)
                        trap 'rm -rf "$DOCKER_CONFIG"' EXIT
                        echo "$DH_PASS" | docker login -u "$DH_USER" --password-stdin
                        docker push ${IMAGE_NAME}:${IMAGE_TAG}
                        docker push ${IMAGE_NAME}:latest
                    '''
                }
            }
        }

        stage('Deploy') {
            steps {
                // 'up -d' only recreates what changed, so re-running is safe
                sh 'docker compose up -d --remove-orphans'
                sh '''
                    echo "Waiting for the stack to become healthy..."
                    for i in $(seq 1 30); do
                        if curl -fsS http://localhost:9090/actuator/health | grep -q UP; then
                            echo "Deployment is healthy"
                            exit 0
                        fi
                        sleep 2
                    done
                    echo "Health check failed"
                    docker compose ps
                    docker compose logs --tail=50
                    exit 1
                '''
            }
        }
    }

    post {
        success {
            mail to: "${NOTIFY_EMAIL}",
                 subject: "SUCCESS: ${JOB_NAME} #${BUILD_NUMBER}",
                 body: "Build #${BUILD_NUMBER} succeeded and is deployed.\nImage: ${IMAGE_NAME}:${IMAGE_TAG}\nDetails: ${BUILD_URL}"
        }
        failure {
            mail to: "${NOTIFY_EMAIL}",
                 subject: "FAILURE: ${JOB_NAME} #${BUILD_NUMBER}",
                 body: "Build #${BUILD_NUMBER} failed.\nSee the console output: ${BUILD_URL}console"
        }
    }
}
