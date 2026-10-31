// ==============================================================================
// TwinDigital DevSecOps CI/CD Declarative Pipeline
// Stages:
//   1. Checkout
//   2. Maven Build & Unit Tests
//   3. OWASP Dependency-Check (SCA - Software Composition Analysis)
//   4. SonarQube Static Analysis & Quality Gate (SAST)
//   5. Docker Multi-Stage Build
//   6. Trivy Container Image Security Scan
//   7. Docker Push (Gated on Security Scan Pass)
//   8. Kubernetes Deployment (Zero-Downtime Rollout)
// ==============================================================================

pipeline {
    agent any

    tools {
        maven 'Maven-3.9'
        jdk 'JDK-17'
    }

    parameters {
        choice(
            name: 'DEPLOY_ENV',
            choices: ['staging', 'prod'],
            description: 'Target deployment environment'
        )
        booleanParam(
            name: 'ENFORCE_SECURITY_GATES',
            defaultValue: true,
            description: 'Enforce blocking Quality Gates for Trivy, SonarQube, and OWASP Dependency-Check'
        )
    }

    environment {
        DOCKERHUB_CREDENTIALS_ID   = 'dockerhub-credentials'
        SONAR_CREDENTIALS_ID      = 'sonarqube-token'
        KUBECONFIG_CREDENTIALS_ID = 'k8s-kubeconfig'
        IMAGE_REPO                = 'twindigital'
        GIT_SHORT_SHA             = ''
        CVSS_THRESHOLD            = '7.0'
        K8S_NAMESPACE             = 'twindigital'
    }

    options {
        timeout(time: 35, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '15', artifactNumToKeepStr: '5'))
        disableConcurrentBuilds()
        ansiColor('xterm')
    }

    stages {
        // ──────────────────────────────────────────────────────────────────────
        // 1. CHECKOUT
        // ──────────────────────────────────────────────────────────────────────
        stage('Checkout') {
            steps {
                script {
                    checkout scm
                    env.GIT_SHORT_SHA = sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim()
                    echo "Checked out commit: ${env.GIT_SHORT_SHA} on branch: ${env.BRANCH_NAME ?: 'main'}"
                }
            }
        }

        // ──────────────────────────────────────────────────────────────────────
        // 2. MAVEN BUILD & UNIT TESTS
        // ──────────────────────────────────────────────────────────────────────
        stage('Maven Build & Unit Tests') {
            steps {
                echo "Compiling microservices and executing automated test suites..."
                dir('services/cluster-sync-service') {
                    sh 'mvn clean test -B'
                }
                dir('services/simulation-service') {
                    sh 'mvn clean test -B'
                }
            }
            post {
                always {
                    junit testResults: '**/target/surefire-reports/*.xml', allowEmptyResults: true
                }
            }
        }

        // ──────────────────────────────────────────────────────────────────────
        // 3. OWASP DEPENDENCY-CHECK (SCA - Software Composition Analysis)
        // ──────────────────────────────────────────────────────────────────────
        stage('OWASP Dependency-Check (SCA)') {
            steps {
                echo "Scanning third-party dependencies for known CVEs (CVSS Threshold: ${env.CVSS_THRESHOLD})..."
                script {
                    def failFlag = params.ENFORCE_SECURITY_GATES ? "-DfailBuildOnCVSS=${env.CVSS_THRESHOLD}" : ""
                    sh """
                        mvn org.owasp:dependency-check-maven:check \
                          -Dformat=ALL \
                          -DautoUpdate=false \
                          ${failFlag} \
                          -B
                    """
                }
            }
            post {
                always {
                    // Archive and publish HTML & XML dependency reports
                    archiveArtifacts artifacts: '**/target/dependency-check-report.html', allowEmptyArchive: true
                }
            }
        }

        // ──────────────────────────────────────────────────────────────────────
        // 4. SONARQUBE ANALYSIS & QUALITY GATE (SAST)
        // ──────────────────────────────────────────────────────────────────────
        stage('SonarQube Analysis & Quality Gate (SAST)') {
            steps {
                echo "Running SonarQube static code quality & security vulnerability scan..."
                withCredentials([string(credentialsId: env.SONAR_CREDENTIALS_ID, variable: 'SONAR_TOKEN')]) {
                    withSonarQubeEnv('SonarQube-Server') {
                        dir('services/cluster-sync-service') {
                            sh """
                                mvn sonar:sonar \
                                  -Dsonar.projectKey=twindigital-cluster-sync \
                                  -Dsonar.projectName='TwinDigital - Cluster Sync' \
                                  -Dsonar.login=${SONAR_TOKEN}
                            """
                        }
                        dir('services/simulation-service') {
                            sh """
                                mvn sonar:sonar \
                                  -Dsonar.projectKey=twindigital-simulation \
                                  -Dsonar.projectName='TwinDigital - Simulation' \
                                  -Dsonar.login=${SONAR_TOKEN}
                            """
                        }
                    }
                }

                // Evaluate SonarQube Quality Gate Status
                timeout(time: 3, unit: 'MINUTES') {
                    script {
                        try {
                            def qg = waitForQualityGate()
                            if (qg.status != 'OK' && params.ENFORCE_SECURITY_GATES) {
                                error "Pipeline aborted due to SonarQube Quality Gate failure: ${qg.status}"
                            } else {
                                echo "SonarQube Quality Gate Status: ${qg.status}"
                            }
                        } catch (Exception e) {
                            if (params.ENFORCE_SECURITY_GATES) {
                                throw e
                            } else {
                                echo "Non-blocking SonarQube Quality Gate bypass: ${e.message}"
                            }
                        }
                    }
                }
            }
        }

        // ──────────────────────────────────────────────────────────────────────
        // 5. DOCKER BUILD
        // ──────────────────────────────────────────────────────────────────────
        stage('Docker Multi-Stage Build') {
            steps {
                echo "Building minimal, unprivileged container images..."
                sh """
                    docker build -t ${IMAGE_REPO}/cluster-sync:${GIT_SHORT_SHA} -t ${IMAGE_REPO}/cluster-sync:latest services/cluster-sync-service
                    docker build -t ${IMAGE_REPO}/simulation:${GIT_SHORT_SHA} -t ${IMAGE_REPO}/simulation:latest services/simulation-service
                    docker build -t ${IMAGE_REPO}/gateway:${GIT_SHORT_SHA} -t ${IMAGE_REPO}/gateway:latest gateway
                    docker build -t ${IMAGE_REPO}/frontend:${GIT_SHORT_SHA} -t ${IMAGE_REPO}/frontend:latest frontend
                """
            }
        }

        // ──────────────────────────────────────────────────────────────────────
        // 6. TRIVY CONTAINER SECURITY SCAN
        // ──────────────────────────────────────────────────────────────────────
        stage('Trivy Container Security Scan') {
            steps {
                echo "Scanning container images for OS and package vulnerabilities..."
                script {
                    def images = [
                        "${IMAGE_REPO}/gateway:${GIT_SHORT_SHA}",
                        "${IMAGE_REPO}/cluster-sync:${GIT_SHORT_SHA}",
                        "${IMAGE_REPO}/simulation:${GIT_SHORT_SHA}",
                        "${IMAGE_REPO}/frontend:${GIT_SHORT_SHA}"
                    ]

                    for (img in images) {
                        echo "==> Scanning image: ${img}"
                        def exitCodeFlag = params.ENFORCE_SECURITY_GATES ? "--exit-code 1" : "--exit-code 0"
                        sh """
                            # 1. Output human-readable table to console
                            trivy image --severity HIGH,CRITICAL --no-progress ${img} || true

                            # 2. Enforce zero CRITICAL vulnerabilities gate
                            trivy image --severity CRITICAL --ignore-unfixed ${exitCodeFlag} --no-progress ${img}
                        """
                    }
                }
            }
        }

        // ──────────────────────────────────────────────────────────────────────
        // 7. DOCKER PUSH
        // ──────────────────────────────────────────────────────────────────────
        stage('Docker Push') {
            steps {
                echo "Publishing verified secure images to Registry..."
                withCredentials([usernamePassword(
                    credentialsId: env.DOCKERHUB_CREDENTIALS_ID,
                    usernameVariable: 'DOCKER_USER',
                    passwordVariable: 'DOCKER_PASS'
                )]) {
                    sh """
                        echo "\$DOCKER_PASS" | docker login -u "\$DOCKER_USER" --password-stdin
                        docker push ${IMAGE_REPO}/cluster-sync:${GIT_SHORT_SHA}
                        docker push ${IMAGE_REPO}/cluster-sync:latest
                        docker push ${IMAGE_REPO}/simulation:${GIT_SHORT_SHA}
                        docker push ${IMAGE_REPO}/simulation:latest
                        docker push ${IMAGE_REPO}/gateway:${GIT_SHORT_SHA}
                        docker push ${IMAGE_REPO}/gateway:latest
                        docker push ${IMAGE_REPO}/frontend:${GIT_SHORT_SHA}
                        docker push ${IMAGE_REPO}/frontend:latest
                    """
                }
            }
        }

        // ──────────────────────────────────────────────────────────────────────
        // 8. KUBERNETES DEPLOYMENT
        // ──────────────────────────────────────────────────────────────────────
        stage('Kubernetes Deployment') {
            steps {
                script {
                    def targetEnv = params.DEPLOY_ENV ?: 'staging'
                    echo "Executing zero-downtime rolling update for environment: ${targetEnv} in namespace '${env.K8S_NAMESPACE}'..."
                    withKubeConfig([credentialsId: env.KUBECONFIG_CREDENTIALS_ID]) {
                        // Deploy staging and prod to namespace twindigital via kustomize overlay
                        // Avoid -n flag conflict by omitting -n on kubectl apply -k
                        sh """
                            kubectl apply -k k8s/overlays/${targetEnv}

                            # Verify zero-downtime rollout status across deployed microservices
                            for dep in gateway auth-service cluster-sync-service topology-service simulation-service cost-risk-service frontend; do
                                kubectl rollout status deployment/\$dep -n ${env.K8S_NAMESPACE} --timeout=180s
                            done
                        """
                    }
                }
            }
        }
    }

    post {
        success {
            echo "=========================================================================="
            echo "DevSecOps Pipeline Succeeded: All security gates (SAST, SCA, Container) passed!"
            echo "Commit: ${env.GIT_SHORT_SHA} deployed to Kubernetes successfully."
            echo "=========================================================================="
        }
        failure {
            echo "=========================================================================="
            echo "DevSecOps Security Gate Violation! Inspect scan reports above."
            echo "=========================================================================="
        }
        always {
            cleanWs deleteDirs: true, notFailBuild: true
        }
    }
}
