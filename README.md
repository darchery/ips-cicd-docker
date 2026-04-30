#  UMA — CI/CD Book System

A Spring Boot REST API for managing a library system, developed for the **Infraestructure and Process Support** course at the University of Málaga.

The objective of this repository is to demonstrate how to build container images using GitHub Actions and deploy a Spring application into a self-hosted Kubernetes cluster using GitHub Runners.

---

## Requirements

| Tool  | Min version |
|-------|-------------|
| Java  | 21          |
| Maven | 3.8+        |

No external database required — H2 in-memory is used for tests.

---

## Running the application

```bash
./mvnw spring-boot:run
```

API available at `http://localhost:8080`.  


## Technology Stack

- Java 21 · Spring Boot 3.2
- Spring Data JPA + H2
- JUnit 5 · MockMvc · WebTestClient
- SpringDoc OpenAPI (Swagger UI)
- Maven · GitHub Actions


## GitHub Actions workflow for CI/CD of containerss 

Before using it, you must configure the following:
- Create repository secrets:
    - DOCKERHUB_USERNAME with your dockerhub username
    - DOCKERHUB_TOKEN with your dockerhub token
- Define the repository variable:
    - REGISTRY

To generate the DOCKERHUB_TOKEN, refer to the official [Docker Hub documentation](https://docs.docker.com/security/access-tokens/#create-a-personal-access-token).

Next, create the `docker-publish.yaml` file inside the `.github/workflows directory`:

```yaml
name: Docker Build & Push on Github

on:
  push:
    branches: [ "main" ]
    tags: [ 'v*.*.*' ]
  pull_request:
    branches: [ "main" ]

env:
  # Dynamic construction: username/github-repo-name
  IMAGE_NAME: ${{ secrets.DOCKERHUB_USERNAME }}/${{ github.event.repository.name }}

jobs:
  build-and-publish:
    runs-on: ubuntu-latest 
    
    permissions:
      contents: read
      packages: write

    steps:
      - name: Checkout repository
        uses: actions/checkout@v4

      - name: Set up QEMU
        uses: docker/setup-qemu-action@v3

      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v3

      - name: Log into registry ${{ vars.REGISTRY }}
        if: github.event_name != 'pull_request'
        uses: docker/login-action@v3
        with:
          registry: ${{ vars.REGISTRY }}
          username: ${{ secrets.DOCKERHUB_USERNAME }}
          password: ${{ secrets.DOCKERHUB_TOKEN }}

      - name: Extract Docker metadata
        id: meta
        uses: docker/metadata-action@v5
        with:
          images: ${{ vars.REGISTRY }}/${{ env.IMAGE_NAME }}
          tags: |
            type=ref,event=branch
            type=ref,event=tag
            type=sha,format=short
            type=raw,value=latest,enable={{is_default_branch}}


      - name: Build and push Docker image
        id: build-and-push
        uses: docker/build-push-action@v5
        with:
          context: .
          push: ${{ github.event_name != 'pull_request' }}
          tags: ${{ steps.meta.outputs.tags }}
          labels: ${{ steps.meta.outputs.labels }}
          cache-from: type=gha
          cache-to: type=gha,mode=max
          platforms: linux/amd64,linux/arm64
          retrying-max-attempts: 3
```

### Deploying the Application to Kubernetes

Attention, a local kubernetes cluster (e.g., Docker Desktop). is required for the next steps.

First, create the namespace where your application will run in your cluster:

```bash
kubectl create namespace ips
```

If your Docker images are private, create a registry secret. Replace `DOCKER_HUB_USER_NAME`, `DOCKER_HUB_USER_TOKEN`, and your email:

```
kubectl create secret docker-registry mi-registro-secreto \
  --docker-server=https://index.docker.io/v1/ \
  --docker-username=DOCKER_HUB_USER_NAME \
  --docker-password=DOCKER_HUB_USER_TOKEN \
  --docker-email=tu-email@ejemplo.com \
```


### Deployment

Modify the name of your repository and create the file `deployment.yml`. anc change
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: backend-app  # This should match the var DEPLOYMENT_NAME
  namespace: ips
spec:
  replicas: 2
  selector:
    matchLabels:
      app: mi-app
  template:
    metadata:
      labels:
        app: mi-app
    spec:
      imagePullSecrets:
        - name: mi-registro-secreto
      containers:
      - name: backend-app  # This should match the var DEPLOYMENT_NAME
        image: user/repository:latest # TODO: modify with your repo
        imagePullPolicy: Always
        ports:
        - containerPort: 8080
```

Apply the deployment:

```bash
kubectl -f deployment.yaml
```

### Service 

```yaml
apiVersion: v1
kind: Service
metadata:
  name: mi-app-service
  namespace: ips
spec:
  type: NodePort # Or LoadBalancer if you are on the cloud (AWS/Azure/GCP)
  selector:
    app: mi-app # It has to match the label in the Deployment
  ports:
    - protocol: TCP
      port: 8080 # Port that the service will expose
      nodePort: 30007 # NodePort to access the service from outside the cluster (30000-32767 range)
```
Apply the service:

```bash
kubectl -f service.yaml
```

### GitHub Actions Workflow for Kubernetes Deployment

To deploy automatically to your Kubernetes cluster, you need a self-hosted GitHub Runner.

```yaml
 deploy-to-k8s:
    needs: build-and-publish # It will not start until the previous job has successfully completed.
    runs-on: self-hosted 
    
    steps:
      - name: Code checkout (Manifiesto K8s)
        uses: actions/checkout@v4

      - name: Set up kubectl
        run: |
          # Install kubectl if not already available
          if ! command -v kubectl &> /dev/null; then
            curl -LO "https://dl.k8s.io/release/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"
            chmod +x kubectl
            sudo mv kubectl /usr/local/bin/
          fi

      - name: Update deployment image
        run: |
          # Update the deployment with the new image using abbreviated SHA
          kubectl set image deployment/${{ vars.DEPLOYMENT_NAME }} \
            ${{ vars.DEPLOYMENT_NAME }}=${{ vars.REGISTRY }}/${{ env.IMAGE_NAME }}:sha-${GITHUB_SHA::7} \
            --namespace=${{ vars.NAMESPACE }}

      - name: Wait for deployment rollout
        run: |
          # Wait for the deployment to complete
          kubectl rollout status deployment/${{ vars.DEPLOYMENT_NAME }} \
            --namespace=${{ vars.NAMESPACE }} \
            --timeout=30s
      
      - name: Verify deployment
        run: |
          # Get deployment status
          kubectl get deployment ${{ vars.DEPLOYMENT_NAME }} --namespace=${{ vars.NAMESPACE }}
          
          # Get pod status
          kubectl get pods -l name=pod-${{ vars.DEPLOYMENT_NAME }} --namespace=${{ vars.NAMESPACE }}
          
          # Show recent events
          kubectl get events --namespace=${{ vars.NAMESPACE }} --sort-by='.lastTimestamp' | tail -10
```
### GitHub Personal Access Token

To authenticate the runner, generate a GitHub Personal Access Token:
1.	Go to GitHub Settings → Developer settings → Personal access tokens → Tokens (classic)
2.	Click Generate new token (classic)
3.	Name: k8s-runner-token
4.	Expiration: choose according to your needs
5.	Scope: select repo
6.	Copy the generated token (starts with ghp_...) and use it in your configuration

### Continuous Deployment with ARC (Actions Runner Controller)

Install the controller (required component):

```bash
NAMESPACE="ips"
helm install arc \
    --namespace "${NAMESPACE}" \
    --create-namespace \
    oci://ghcr.io/actions/actions-runner-controller-charts/gha-runner-scale-set-controller
```

Install the runner scale set:
```bash
INSTALLATION_NAME="self-hosted"
NAMESPACE="ips"
GITHUB_CONFIG_URL="https://github.com/<your_enterprise/org/repo>"
GITHUB_PAT="<PAT>"
helm install "${INSTALLATION_NAME}" \
    --namespace "${NAMESPACE}" \
    --create-namespace \
    --set githubConfigUrl="${GITHUB_CONFIG_URL}" \
    --set githubConfigSecret.github_token="${GITHUB_PAT}" \
    oci://ghcr.io/actions/actions-runner-controller-charts/gha-runner-scale-set
```

Install the runner scale set:

```bash
INSTALLATION_NAME="self-hosted"
NAMESPACE="ips"
GITHUB_CONFIG_URL="https://github.com/<your_enterprise/org/repo>"
GITHUB_PAT="<PAT>"
helm install "${INSTALLATION_NAME}" \
    --namespace "${NAMESPACE}" \
    --create-namespace \
    --set githubConfigUrl="${GITHUB_CONFIG_URL}" \
    --set githubConfigSecret.github_token="${GITHUB_PAT}" \
    oci://ghcr.io/actions/actions-runner-controller-charts/gha-runner-scale-set
```

### RBAC Configuration

Grant permissions to allow the runner to deploy resources `runner-permissions.yml`:

```yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  namespace: ips
  name: gha-runner-role
rules:
  - apiGroups: ["apps"]
    resources: ["deployments", "replicasets"]
    verbs: ["get", "list", "watch", "create", "update", "patch", "delete"]
  - apiGroups: [""]
    resources: ["pods", "services"]
    verbs: ["get", "list", "watch", "create", "update", "patch", "delete"]
```

Apply the permissions:

```bash
kubectl -f runner-permissions.yml
```

Verify permissions:
```
kubectl auth can-i patch deployment/spring3 --as=system:serviceaccount:ips:self-hosted-gha-rs-no-permission -n ips
```
If the response is yes, your setup is correct.

### Final Step

You are now ready to perform automatic deployments to Kubernetes using GitHub Actions 🚀