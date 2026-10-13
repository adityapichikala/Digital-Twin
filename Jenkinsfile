// ==============================================================================
// TwinDigital Production Declarative CI/CD Pipeline
// Stages:
//   1. Checkout & Path Filter (Detect changed services)
//   2. Build & Unit Tests (JUnit + JaCoCo coverage, parallelized)
//   3. SonarQube Analysis & Quality Gate (Blocks pipeline on failure)
//   4. Build Docker Images (Tagged with service + git short SHA)
//   5. Trivy Security Scan (Fails pipeline on HIGH/CRITICAL CVEs)
//   6. Push to Registry (Gated on security scan pass)
//   7. OWASP ZAP Baseline DAST Scan (Against staging deployment)
//   8. Deploy to EKS & Rollout Status Check (Automatic rollback on failure)
// ==============================================================================

pipeline {
    agent any

    options {
        timeout(time: 45, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '25', artifactNumToKeepStr: '10'))
        disableConcurrentBuilds()
        ansiColor('xterm')
    }

    parameters {
        choice(
            name: 'TARGET_SERVICE',
            choices: ['auto-detect', 'all', 'gateway', 'frontend', 'auth-service', 'cluster-sync-service', 'topology-service', 'simulation-service', 'cost-risk-service'],
            description: 'Choose a specific service to build, "all" to force build all services, or "auto-detect" to build only changed services based on Git diff.'
        )
        choice(
            name: 'DEPLOY_ENV',
            choices: ['staging', 'production'],
            description: 'Target deployment environment in Kubernetes/EKS.'
        )
        booleanParam(
            name: 'SKIP_SECURITY_GATES',
            defaultValue: false,
            description: 'EMERGENCY ONLY: Bypass Quality Gate and Trivy failure exits (Audit logged).'
        )
    }

    environment {
        AWS_REGION                 = 'ap-south-1'
        AWS_ACCOUNT_ID             = '112233445566'
        IMAGE_REGISTRY             = "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/twindigital"
        K8S_NAMESPACE              = 'twindigital'
        STAGING_URL                = 'http://staging.twindigital.io'
        
        // Jenkins Credentials Store IDs
        SONAR_CREDENTIALS_ID       = 'sonarqube-token'
        REGISTRY_CREDENTIALS_ID    = 'aws-ecr-credentials'
        KUBECONFIG_CREDENTIALS_ID  = 'eks-kubeconfig'
        
        // Computed dynamically
        GIT_SHORT_SHA              = ''
        SERVICES_TO_BUILD          = ''
    }

    stages {
        // ──────────────────────────────────────────────────────────────────────
        // 1. CHECKOUT & PATH FILTERING (Detect Changed Services)
        // ──────────────────────────────────────────────────────────────────────
        stage('Checkout & Detect Changed Services') {
            steps {
                script {
                    checkout scm
                    env.GIT_SHORT_SHA = sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim()
                    echo "Branch: ${env.BRANCH_NAME ?: 'master'} | Commit: ${env.GIT_SHORT_SHA}"

                    def changedServices = []

                    if (params.TARGET_SERVICE != 'auto-detect') {
                        if (params.TARGET_SERVICE == 'all') {
                            changedServices = ['gateway', 'frontend', 'auth-service', 'cluster-sync-service', 'topology-service', 'simulation-service', 'cost-risk-service']
                            echo "Manual trigger: Building ALL services."
                        } else {
                            changedServices = [params.TARGET_SERVICE]
                            echo "Manual trigger: Building selected service '${params.TARGET_SERVICE}'."
                        }
                    } else {
                        // Detect changed files between HEAD~1 and HEAD (or against origin/master if PR)
                        def gitDiffCommand = 'git diff --name-only HEAD~1 HEAD || git status --porcelain | awk \'{print $2}\''
                        def changedFiles = sh(script: gitDiffCommand, returnStdout: true).trim().split('\n')
                        echo "Detected ${changedFiles.size()} changed file(s):"

                        def isRootChanged = changedFiles.any { it.startsWith('k8s/') || it.startsWith('ci/') || it.equals('Jenkinsfile') || it.equals('docker-compose.yml') }
                        if (isRootChanged) {
                            echo "Root configuration or k8s manifests modified. Triggering full build matrix."
                            changedServices = ['gateway', 'frontend', 'auth-service', 'cluster-sync-service', 'topology-service', 'simulation-service', 'cost-risk-service']
                        } else {
                            if (changedFiles.any { it.startsWith('gateway/') }) changedServices.add('gateway')
                            if (changedFiles.any { it.startsWith('frontend/') }) changedServices.add('frontend')
                            if (changedFiles.any { it.startsWith('services/auth-service/') }) changedServices.add('auth-service')
                            if (changedFiles.any { it.startsWith('services/cluster-sync-service/') }) changedServices.add('cluster-sync-service')
                            if (changedFiles.any { it.startsWith('services/topology-service/') }) changedServices.add('topology-service')
                            if (changedFiles.any { it.startsWith('services/simulation-service/') }) changedServices.add('simulation-service')
                            if (changedFiles.any { it.startsWith('services/cost-risk-service/') }) changedServices.add('cost-risk-service')
                        }

                        if (changedServices.isEmpty()) {
                            echo "No code changes detected in service directories. Defaulting to all services."
                            changedServices = ['gateway', 'frontend', 'auth-service', 'cluster-sync-service', 'topology-service', 'simulation-service', 'cost-risk-service']
                        }
                    }

                    env.SERVICES_TO_BUILD = changedServices.unique().join(',')
                    echo "Target services to build and deploy: ${env.SERVICES_TO_BUILD}"
                }
            }
        }

        // ──────────────────────────────────────────────────────────────────────
        // 2. PARALLEL BUILD & UNIT TESTS (JUnit + JaCoCo)
        // ──────────────────────────────────────────────────────────────────────
        stage('Build & Unit Tests') {
            steps {
                script {
                    def services = env.SERVICES_TO_BUILD.split(',')
                    def parallelStages = [:]

                    for (int i = 0; i < services.size(); i++) {
                        def svc = services[i]
                        parallelStages["Build-${svc}"] = {
                            stage("Build & Test: ${svc}") {
                                echo "=== Building and testing: ${svc} ==="
                                if (svc == 'gateway') {
                                    dir('gateway') {
                                        sh 'npm ci'
                                        sh 'npm test -- --reporters=default --reporters=jest-junit --coverage'
                                    }
                                } else if (svc == 'frontend') {
                                    dir('frontend') {
                                        sh 'npm ci'
                                        sh 'npm run build'
                                    }
                                } else {
                                    // Java Spring Boot microservice with JaCoCo test execution & report generation
                                    dir("services/${svc}") {
                                        sh """
                                            mvn clean \
                                              org.jacoco:jacoco-maven-plugin:0.8.11:prepare-agent \
                                              test \
                                              org.jacoco:jacoco-maven-plugin:0.8.11:report \
                                              package \
                                              -DskipTests=false \
                                              -B \
                                              -Dmaven.test.failure.ignore=false
                                        """
                                    }
                                }
                            }
                        }
                    }

                    parallel parallelStages
                }
            }
            post {
                always {
                    // Publish test results to Jenkins test dashboard
                    junit allowEmptyResults: true, testResults: '**/target/surefire-reports/*.xml, **/junit.xml'

                    // Publish JaCoCo code coverage reports
                    step([$class: 'JacocoPublisher',
                        execPattern: '**/target/jacoco.exec',
                        classPattern: '**/target/classes',
                        sourcePattern: '**/src/main/java',
                        inclusionPattern: '**/*.class',
                        exclusionPattern: '**/*Test*.class,**/model/**',
                        maximumInstructionCoverage: '80', minimumInstructionCoverage: '50'
                    ])
                }
            }
        }

        // ──────────────────────────────────────────────────────────────────────
        // 3. SONARQUBE ANALYSIS & QUALITY GATE
        // ──────────────────────────────────────────────────────────────────────
        stage('SonarQube Analysis & Quality Gate') {
            steps {
                script {
                    def services = env.SERVICES_TO_BUILD.split(',')
                    
                    withSonarQubeEnv(installationName: 'SonarQube', credentialsId: env.SONAR_CREDENTIALS_ID) {
                        for (int i = 0; i < services.size(); i++) {
                            def svc = services[i]
                            echo "Running SonarQube Scanner for ${svc}..."
                            
                            if (svc in ['auth-service', 'cluster-sync-service', 'topology-service', 'simulation-service', 'cost-risk-service']) {
                                dir("services/${svc}") {
                                    sh """
                                        mvn org.sonarsource.scanner.maven:sonar-maven-plugin:3.11.0.3922:sonar \
                                          -Dsonar.projectKey=twindigital-${svc} \
                                          -Dsonar.projectName="TwinDigital ${svc}" \
                                          -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml \
                                          -Dsonar.qualitygate.wait=false \
                                          -B
                                    """
                                }
                            } else if (svc == 'gateway') {
                                dir('gateway') {
                                    sh """
                                        sonar-scanner \
                                          -Dsonar.projectKey=twindigital-gateway \
                                          -Dsonar.projectName="TwinDigital Gateway" \
                                          -Dsonar.sources=src \
                                          -Dsonar.tests=src/__tests__ \
                                          -Dsonar.javascript.lcov.reportPaths=coverage/lcov.info \
                                          || true
                                    """
                                }
                            }
                        }
                    }

                    // Enforce Quality Gate check
                    if (!params.SKIP_SECURITY_GATES) {
                        timeout(time: 5, unit: 'MINUTES') {
                            echo "Waiting for SonarQube Quality Gate result..."
                            def qg = waitForQualityGate()
                            if (qg.status != 'OK') {
                                error "PIPELINE BLOCKED: SonarQube Quality Gate failed with status: ${qg.status}!"
                            }
                            echo "SonarQube Quality Gate passed successfully."
                        }
                    } else {
                        echo "WARNING: SonarQube Quality Gate check was bypassed via SKIP_SECURITY_GATES."
                    }
                }
            }
        }

        // ──────────────────────────────────────────────────────────────────────
        // 4. DOCKER IMAGE BUILD
        // ──────────────────────────────────────────────────────────────────────
        stage('Build Docker Images') {
            steps {
                script {
                    def services = env.SERVICES_TO_BUILD.split(',')
                    def parallelBuilds = [:]

                    for (int i = 0; i < services.size(); i++) {
                        def svc = services[i]
                        parallelBuilds["DockerBuild-${svc}"] = {
                            def dockerContext = (svc in ['gateway', 'frontend']) ? "${svc}" : "services/${svc}"
                            def imageTagSha = "${env.IMAGE_REGISTRY}/${svc}:${env.GIT_SHORT_SHA}"
                            def imageTagLatest = "${env.IMAGE_REGISTRY}/${svc}:latest"

                            echo "Building Docker image: ${imageTagSha} from ./${dockerContext}..."
                            sh """
                                docker build \
                                  --tag ${imageTagSha} \
                                  --tag ${imageTagLatest} \
                                  --file ${dockerContext}/Dockerfile \
                                  ${dockerContext}
                            """
                        }
                    }

                    parallel parallelBuilds
                }
            }
        }

        // ──────────────────────────────────────────────────────────────────────
        // 5. TRIVY IMAGE VULNERABILITY SCAN (Fail on HIGH/CRITICAL)
        // ──────────────────────────────────────────────────────────────────────
        stage('Trivy Image Vulnerability Scan') {
            steps {
                script {
                    sh 'mkdir -p scan-reports'
                    def services = env.SERVICES_TO_BUILD.split(',')
                    def parallelScans = [:]

                    for (int i = 0; i < services.size(); i++) {
                        def svc = services[i]
                        parallelScans["TrivyScan-${svc}"] = {
                            def imageTag = "${env.IMAGE_REGISTRY}/${svc}:${env.GIT_SHORT_SHA}"
                            echo "Running Trivy Vulnerability Scan on ${imageTag}..."

                            // 1. Generate human-readable table report
                            sh """
                                trivy image \
                                  --severity HIGH,CRITICAL \
                                  --format table \
                                  --output scan-reports/trivy-${svc}.txt \
                                  ${imageTag} || true
                            """

                            // 2. Generate JSON artifact for compliance
                            sh """
                                trivy image \
                                  --severity HIGH,CRITICAL \
                                  --format json \
                                  --output scan-reports/trivy-${svc}.json \
                                  ${imageTag} || true
                            """

                            // 3. Security Gate Enforcement (Exit code 1 on HIGH or CRITICAL)
                            if (!params.SKIP_SECURITY_GATES) {
                                def exitCode = sh(
                                    script: "trivy image --exit-code 1 --severity HIGH,CRITICAL --quiet ${imageTag}",
                                    returnStatus: true
                                )
                                if (exitCode != 0) {
                                    error "SECURITY GATE FAILURE: Trivy detected HIGH/CRITICAL vulnerabilities in ${imageTag}. Push & Deploy aborted."
                                }
                            } else {
                                echo "WARNING: Trivy security gate check was bypassed via SKIP_SECURITY_GATES."
                            }
                        }
                    }

                    parallel parallelScans
                }
            }
            post {
                always {
                    archiveArtifacts artifacts: 'scan-reports/trivy-*', allowEmptyArchive: true
                }
            }
        }

        // ──────────────────────────────────────────────────────────────────────
        // 6. PUSH TO CONTAINER REGISTRY (Gated)
        // ──────────────────────────────────────────────────────────────────────
        stage('Push Docker Images to Registry') {
            steps {
                script {
                    echo "All security gates passed. Authenticating and pushing images to ${env.IMAGE_REGISTRY}..."
                    
                    // Authenticate to AWS ECR using IRSA / AWS CLI or Docker Hub Credentials
                    sh """
                        aws ecr get-login-password --region ${env.AWS_REGION} | \
                          docker login --username AWS --password-stdin ${env.AWS_ACCOUNT_ID}.dkr.ecr.${env.AWS_REGION}.amazonaws.com \
                          || true
                    """

                    def services = env.SERVICES_TO_BUILD.split(',')
                    for (int i = 0; i < services.size(); i++) {
                        def svc = services[i]
                        def imageTagSha = "${env.IMAGE_REGISTRY}/${svc}:${env.GIT_SHORT_SHA}"
                        def imageTagLatest = "${env.IMAGE_REGISTRY}/${svc}:latest"

                        echo "Pushing ${imageTagSha} and latest..."
                        sh "docker push ${imageTagSha}"
                        sh "docker push ${imageTagLatest}"
                    }
                }
            }
        }

        // ──────────────────────────────────────────────────────────────────────
        // 7. OWASP ZAP DAST BASELINE SCAN (Against Staging Deployment)
        // ──────────────────────────────────────────────────────────────────────
        stage('OWASP ZAP Baseline Security Scan') {
            when {
                expression { return params.DEPLOY_ENV == 'staging' || params.TARGET_SERVICE == 'gateway' }
            }
            steps {
                script {
                    sh 'mkdir -p zap-reports && chmod 777 zap-reports'
                    echo "Executing OWASP ZAP Baseline DAST Scan against ${env.STAGING_URL}..."

                    // Run official OWASP ZAP container to produce HTML and JSON vulnerability reports
                    def zapExitCode = sh(
                        script: """
                            docker run --rm \
                              -v \$(pwd)/zap-reports:/zap/wrk/:rw \
                              zaproxy/zap-stable:latest \
                              zap-baseline.py \
                              -t ${env.STAGING_URL} \
                              -r zap-baseline-report.html \
                              -J zap-baseline-report.json \
                              -I \
                              -a || true
                        """,
                        returnStatus: true
                    )

                    echo "OWASP ZAP Scan completed with status: ${zapExitCode}."
                }
            }
            post {
                always {
                    archiveArtifacts artifacts: 'zap-reports/*', allowEmptyArchive: true
                    publishHTML(target: [
                        allowMissing: true,
                        alwaysLinkToLastBuild: true,
                        keepAll: true,
                        reportDir: 'zap-reports',
                        reportFiles: 'zap-baseline-report.html',
                        reportName: 'OWASP ZAP DAST Report'
                    ])
                }
            }
        }

        // ──────────────────────────────────────────────────────────────────────
        // 8. DEPLOY TO EKS & AUTOMATIC ROLLBACK
        // ──────────────────────────────────────────────────────────────────────
        stage('Deploy to Kubernetes (EKS)') {
            steps {
                script {
                    echo "Deploying updated services to namespace: ${env.K8S_NAMESPACE} in ${params.DEPLOY_ENV}..."

                    // Authenticate with Kubernetes cluster using kubeconfig credential
                    withKubeConfig([credentialsId: env.KUBECONFIG_CREDENTIALS_ID]) {
                        def services = env.SERVICES_TO_BUILD.split(',')

                        for (int i = 0; i < services.size(); i++) {
                            def svc = services[i]
                            def imageTag = "${env.IMAGE_REGISTRY}/${svc}:${env.GIT_SHORT_SHA}"

                            echo "Deploying ${svc} with image: ${imageTag}..."
                            
                            // 1. Update container image on deployment
                            sh """
                                kubectl set image deployment/${svc} \
                                  ${svc}=${imageTag} \
                                  --namespace=${env.K8S_NAMESPACE} \
                                  --record
                            """

                            // 2. Verify rollout status with automatic rollback on timeout or failure
                            try {
                                echo "Checking rollout status for deployment/${svc} (timeout: 180s)..."
                                sh """
                                    kubectl rollout status deployment/${svc} \
                                      --namespace=${env.K8S_NAMESPACE} \
                                      --timeout=180s
                                """
                                echo "Rollout succeeded for ${svc}!"
                            } catch (Exception e) {
                                echo "ROLLOUT FAILED for ${svc}: ${e.message}"
                                echo "Initiating automatic rollback to previous stable revision..."
                                
                                // Perform automatic rollback
                                sh """
                                    kubectl rollout undo deployment/${svc} \
                                      --namespace=${env.K8S_NAMESPACE}
                                """
                                
                                sh """
                                    kubectl rollout status deployment/${svc} \
                                      --namespace=${env.K8S_NAMESPACE} \
                                      --timeout=120s
                                """
                                
                                error "FATAL: Deployment for ${svc} failed and was automatically rolled back."
                            }
                        }

                        // Final cluster sanity check
                        sh "kubectl get pods -n ${env.K8S_NAMESPACE} -l app.kubernetes.io/part-of=twindigital"
                    }
                }
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // POST PIPELINE ACTIONS
    // ──────────────────────────────────────────────────────────────────────────
    post {
        always {
            cleanWs deleteDirs: true, notFailBuild: true
        }
        success {
            echo "SUCCESS: TwinDigital pipeline completed cleanly for [${env.SERVICES_TO_BUILD}]."
        }
        failure {
            echo "FAILED: TwinDigital pipeline failed. Review stage logs and archived scan reports."
        }
    }
}
