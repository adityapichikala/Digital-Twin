# ==============================================================================
# TwinDigital Jenkins CI/CD Agent Image
# Includes:
#   • Java: OpenJDK 17
#   • Build Tools: Maven 3.9+, Node.js 20, npm
#   • Security Scanners: Trivy, Sonar-Scanner CLI
#   • Deployment Tools: kubectl, AWS CLI v2, Docker CLI
# ==============================================================================

FROM ubuntu:22.04

LABEL maintainer="TwinDigital DevOps Team"
LABEL description="Full-featured Jenkins Agent for TwinDigital CI/CD pipelines"

ENV DEBIAN_FRONTEND=noninteractive
ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
ENV PATH="${JAVA_HOME}/bin:/opt/sonar-scanner/bin:${PATH}"

# 1. Install base utilities and OpenJDK 17 + Maven
RUN apt-get update && apt-get install -y --no-install-recommends \
    apt-transport-https \
    ca-certificates \
    curl \
    git \
    gnupg \
    jq \
    maven \
    openjdk-17-jdk \
    unzip \
    tar \
    wget \
    && rm -rf /var/lib/apt/lists/*

# 2. Install Node.js 20.x & npm
RUN curl -fsSL https://deb.nodesource.com/setup_20.x | bash - \
    && apt-get install -y --no-install-recommends nodejs \
    && npm install -g npm@latest \
    && rm -rf /var/lib/apt/lists/*

# 3. Install Docker CLI
RUN install -m 0755 -d /etc/apt/keyrings \
    && curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc \
    && chmod a+r /etc/apt/keyrings/docker.asc \
    && echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | tee /etc/apt/sources.list.d/docker.list > /dev/null \
    && apt-get update \
    && apt-get install -y --no-install-recommends docker-ce-cli docker-buildx-plugin \
    && rm -rf /var/lib/apt/lists/*

# 4. Install AWS CLI v2
RUN curl -sSL "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "/tmp/awscliv2.zip" \
    && unzip -q /tmp/awscliv2.zip -d /tmp \
    && /tmp/aws/install \
    && rm -rf /tmp/aws /tmp/awscliv2.zip

# 5. Install kubectl
ARG KUBECTL_VERSION=v1.29.3
RUN curl -sSL -o /usr/local/bin/kubectl "https://dl.k8s.io/release/${KUBECTL_VERSION}/bin/linux/amd64/kubectl" \
    && chmod 0755 /usr/local/bin/kubectl

# 6. Install Trivy Container Vulnerability Scanner
RUN curl -sfL https://raw.githubusercontent.com/aquasecurity/trivy/main/contrib/install.sh | sh -s -- -b /usr/local/bin

# 7. Install Sonar-Scanner CLI
ARG SONAR_SCANNER_VERSION=5.0.1.3006
RUN curl -sSLo /tmp/sonar-scanner.zip "https://binaries.sonarsource.com/Distribution/sonar-scanner-cli/sonar-scanner-cli-${SONAR_SCANNER_VERSION}-linux.zip" \
    && unzip -q /tmp/sonar-scanner.zip -d /opt \
    && mv /opt/sonar-scanner-${SONAR_SCANNER_VERSION}-linux /opt/sonar-scanner \
    && ln -s /opt/sonar-scanner/bin/sonar-scanner /usr/local/bin/sonar-scanner \
    && rm -f /tmp/sonar-scanner.zip

# 8. Create jenkins user & workspace directory
RUN groupadd -g 1000 jenkins \
    && useradd -u 1000 -g jenkins -m -d /home/jenkins jenkins \
    && usermod -aG root jenkins

WORKDIR /home/jenkins

USER jenkins

CMD ["/bin/bash"]
