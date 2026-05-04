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
  --namespace=ips
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

---

# Promt de la última parte

📋 PLAN DETALLADO: Completar CI/CD con ARC y Despliegue Automático en Kubernetes
Estado Actual Confirmado ✅
- ✅ Proyecto: ipscicd-docker (user: darchery)
- ✅ Repositorio GitHub: https://github.com/darchery/ipscicd-docker
- ✅ Kubernetes manifests: deployment.yml, service.yml, runner-permissions.yml listos
- ✅ Workflow GitHub Actions: docker-publish.yml con ambos jobs (build + deploy-to-k8s)
- ✅ Variables de GitHub configuradas (asumiendo que ya lo hiciste)
⚠️ Fases Pendientes
Te quedan 5 fases críticas para tener el CI/CD 100% automático funcionando:
---
FASE 1: Generar GitHub Personal Access Token (PAT)
Objetivo
Crear un token con permisos limitados para que ARC pueda autenticarse contra tu repositorio GitHub.
Pasos (en orden):
1. Acceder a GitHub Settings:
   - Ve a https://github.com/settings/tokens
2. Generar un nuevo token clásico:
   - Click en "Generate new token (classic)"
   - Name: k8s-runner-token
   - Expiration: Elige según tus necesidades (recomendado: 90 días para desarrollo)
   - Scopes requeridos: Marca solo repo (permisos de repositorio)
   - NO marques nada más (minimizar permisos por seguridad)
3. Copiar el token generado:
   - GitHub te mostrará algo como: ghp_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
   - GUARDA ESTE TOKEN EN UN LUGAR SEGURO (solo lo ves una vez)
   - Si lo pierdes, tienes que generar otro
4. Notas de seguridad:
   - Nunca lo commitees al repo
   - Nunca lo pongas en plain text en manifests
   - Solo úsalo en variables de Helm o secretos de Kubernetes
---
FASE 2: Instalar ARC (Actions Runner Controller)
Objetivo
Desplegar el controlador de ARC en tu cluster de Kubernetes que gestiona los runners.
Prerequisites:
- Helm 3.x instalado en tu máquina
- Acceso a tu cluster local (kubectl configurado y apuntando a tu cluster)
- Namespace ips creado (ya lo hiciste en pasos anteriores)
Pasos (en orden):
1. Instalar el ARC Controller:
      NAMESPACE="ips"
   helm install arc \
       --namespace "${NAMESPACE}" \
       --create-namespace \
       oci://ghcr.io/actions/actions-runner-controller-charts/gha-runner-scale-set-controller
   
2. Verificar que el controlador está corriendo:
      kubectl get pods -n ips | grep arc
      - Debe haber pods con nombres como arc-gha-runner-scale-set-controller-...
   - Estado: Running y 1/1 Ready
3. Esperar a que esté listo (puede tardar 1-2 minutos):
      kubectl wait --for=condition=Ready pod -l app.kubernetes.io/name=gha-runner-scale-set-controller -n ips --timeout=300s
   
---
FASE 3: Instalar el Runner Scale Set
Objetivo
Crear el "pool" de runners que ejecutarán tus jobs de GitHub Actions dentro del cluster.
Pasos (en orden):
1. Preparar las variables de entorno (en tu terminal):
      INSTALLATION_NAME="self-hosted"
   NAMESPACE="ips"
   GITHUB_CONFIG_URL="https://github.com/darchery/ipscicd-docker"
   GITHUB_PAT="ghp_YOUR_TOKEN_HERE"  # ⚠️ REEMPLAZA CON TU TOKEN DEL PASO 1
   
2. Instalar el Runner Scale Set con Helm:
      helm install "${INSTALLATION_NAME}" \
       --namespace "${NAMESPACE}" \
       --create-namespace \
       --set githubConfigUrl="${GITHUB_CONFIG_URL}" \
       --set githubConfigSecret.github_token="${GITHUB_PAT}" \
       oci://ghcr.io/actions/actions-runner-controller-charts/gha-runner-scale-set
   
3. Verificar que el runner está registrado:
      kubectl get pods -n ips -l app.kubernetes.io/name=gha-runner-scale-set
   
4. Validar en GitHub:
   - Ve a tu repo GitHub → Settings → Actions → Runners
   - Deberías ver un runner nuevo con nombre tipo self-hosted-... en estado Idle
---
FASE 4: Aplicar RBAC (Role-Based Access Control)
Objetivo
Dar permisos específicos al runner para que pueda hacer kubectl set image, kubectl rollout, etc.
Pasos (en orden):
1. Revisar tu runner-permissions.yml actual:
   - Tu archivo existe y contiene un Role con permisos para deployments, replicasets, pods y services.
   - ✅ Está bien configurado.
2. Aplicar el RBAC al cluster:
      kubectl apply -f runner-permissions.yml
   
3. Verificar que el Role se creó:
      kubectl get role -n ips
      - Deberías ver gha-runner-role
4. Crear RoleBinding (conectar el Role con el ServiceAccount del runner):
   - El nombre del ServiceAccount del runner depende de cómo ARC lo crée.
   - Típicamente es algo como self-hosted-gha-rs o similar.
   - Necesitarás crear un RoleBinding. Opciones:
   Opción A: Hacerlo manualmente con kubectl
      kubectl create rolebinding gha-runner-rolebinding \
       --clusterrole=gha-runner-role \
       --serviceaccount=ips:self-hosted-gha-rs \
       --namespace=ips
      ⚠️ Reemplaza self-hosted-gha-rs con el nombre correcto de tu SA.
   Opción B: Crear un manifiesto YAML (recomendado)
   Crear runner-rolebinding.yml:
      apiVersion: rbac.authorization.k8s.io/v1
   kind: RoleBinding
   metadata:
     namespace: ips
     name: gha-runner-rolebinding
   roleRef:
     apiGroup: rbac.authorization.k8s.io
     kind: Role
     name: gha-runner-role
   subjects:
   - kind: ServiceAccount
     name: self-hosted-gha-rs
     namespace: ips
      Luego:
      kubectl apply -f runner-rolebinding.yml
   
5. Descubrir el nombre correcto del ServiceAccount:
      kubectl get serviceaccount -n ips
      - Busca el que empieza con self-hosted
---
FASE 5: Verificar que todo funciona (Testing)
Objetivo
Confirmar que el runner tiene permisos para ejecutar comandos de Kubernetes.
Pasos (en orden):
1. Verificar permisos del runner (comando del README):
      kubectl auth can-i patch deployment/backend-app \
       --as=system:serviceaccount:ips:self-hosted-gha-rs \
       -n ips
      - Respuesta esperada: yes
   - Si responde no, revisa que el RoleBinding esté correcto.
2. Ver los pods del runner en acción:
      kubectl get pods -n ips -w
      - Deberías ver runners con nombres tipo self-hosted-xxxxx
3. Hacer un push a tu repo para lanzar el workflow:
   - Haz un pequeño cambio en el repo (ej. comentario en README)
   - Push a main
   - Ve a GitHub Actions y observa que se lance el workflow
4. Monitorear el job en el runner:
   - Si todo está bien, verás que el job build-and-publish corre.
   - Luego, el job deploy-to-k8s debería correr en tu runner self-hosted.
   - Si ambos pasan en verde, ¡el pipeline está 100% operativo! 🎉
5. Validar que el deployment se actualizó:
      kubectl get deployment backend-app -n ips -o yaml | grep image
      - La imagen debe reflejar el SHA del commit que acaba de hacer push.
---
FASE 6: Troubleshooting Común
Si algo no funciona, revisa esto:
Problema	Causa probable	Solución
Runner no aparece en GitHub	Token inválido o URL de repo incorrecta	Regenera el token, verifica URL en helm install
Runner aparece pero dice "No permission"	RBAC no configurado correctamente	Verifica RoleBinding y nombre de SA
Job de deploy falla con "kubectl: not found"	kubectl no instalado en runner	El step "Set up kubectl" fallará; se reinstala automáticamente
Image pull fails en pod	Secreto docker-registry no en namespace ips	Ya lo corregiste en Paso 2, pero verifica con kubectl get secrets -n ips
Deployment no actualiza imagen	Variables de GitHub no configuradas	Verifica Settings > Secrets and variables > Actions

---

## Este comando me daba no: kubectl auth can-i patch deployment/spring3 --as=system:serviceaccount:ips:self-hosted-gha-rs-no-permission -n ips
Con este prompt lo he conseguido arreglar:
---
🔴 DIAGNÓSTICO: ¿Por qué responde "no"?
El comando que ejecutaste:
kubectl auth can-i patch deployment/spring3 --as=system:serviceaccount:ips:self-hosted-gha-rs-no-permission -n ips
Responde "no" porque hay dos problemas críticos:
Problema 1: El ServiceAccount NO está vinculado al Role correcto
Estado actual detectado:
- ✅ El Role gha-runner-role EXISTE con los permisos correctos (deployments, pods, services)
- ✅ El ServiceAccount self-hosted-gha-rs-no-permission EXISTE
- ❌ NO HAY ningún RoleBinding que conecte el SA con gha-runner-role
RoleBindings que sí existen:
- arc-gha-rs-controller-listener → ServiceAccount: arc-gha-rs-controller
- self-hosted-gha-rs-manager → ServiceAccount: arc-gha-rs-controller (no el runner)
- self-hosted-56b8f5b8-listener → ServiceAccount: self-hosted-56b8f5b8-listener (solo permisos limitados)
El runner que ejecuta tus jobs (self-hosted-gha-rs-no-permission) no tiene ningún RoleBinding vinculado.
Problema 2: El nombre del SA es extraño ("no-permission")
El nombre self-hosted-gha-rs-no-permission sugiere que intencionalmente se creó SIN permisos. Esto es anómalo.
---
SOLUCIÓN REQUERIDA: Crear RoleBinding faltante
Paso 1: Crear el RoleBinding necesario
Necesitas conectar el ServiceAccount del runner con tu Role de permisos.
Opción A: Comando kubectl directo
kubectl create rolebinding runner-deploy-permissions \
  --role=gha-runner-role \
  --serviceaccount=ips:self-hosted-gha-rs-no-permission \
  --namespace=ips
Opción B: Manifiesto YAML (recomendado)
Crear archivo runner-rolebinding.yml:
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  namespace: ips
  name: runner-deploy-permissions
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: Role
  name: gha-runner-role
subjects:
- kind: ServiceAccount
  name: self-hosted-gha-rs-no-permission
  namespace: ips
Luego:
kubectl apply -f runner-rolebinding.yml
Paso 2: Verificar que ahora SÍ tiene permisos
kubectl auth can-i patch deployment/backend-app \
  --as=system:serviceaccount:ips:self-hosted-gha-rs-no-permission \
  -n ips
Respuesta esperada: yes
---