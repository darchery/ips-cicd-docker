# Resumen CI/CD Docker - Práctica Completa

## 📋 Tabla de Contenidos
1. [Introducción](#introducción)
2. [Arquitectura](#arquitectura)
3. [Flujo CI/CD](#flujo-cicd)
4. [Componentes](#componentes)
5. [Despliegue](#despliegue)
6. [Verificación](#verificación)
7. [Errores Comunes](#errores-comunes)

---

## Introducción

Esta práctica integra **CI (Continuous Integration)**, **Containerización** y **CD (Continuous Deployment)** en un flujo automatizado:

```
Código → Docker → GitHub Actions → Kubernetes
```

**Objetivo:** Desplegar una aplicación Spring Boot en Kubernetes de forma automática y sin downtime.

---

## Arquitectura

### Stack Tecnológico

```
Lenguaje: Java 21
├─ Framework: Spring Boot 3.2 (REST API)
├─ ORM: Spring Data JPA + H2 (BD en memoria)
├─ Testing: JUnit 5, MockMvc
└─ Docs: Swagger UI

Containerización: Docker (multi-stage build)
├─ Etapa BUILD: Compila con Maven
└─ Etapa RUNTIME: Ejecuta con JRE

CI/CD: GitHub Actions
├─ Job 1: build-and-publish (Ubuntu - GitHub)
└─ Job 2: deploy-to-k8s (self-hosted - Cluster)

Orquestación: Kubernetes
├─ Namespace: ips
├─ Deployment: backend-app (2 replicas)
├─ Service: NodePort (puerto 30007)
└─ RBAC: Role + RoleBinding
```

---

## Flujo CI/CD

### Cuando haces `git push main`:

```
1. GitHub detecta push
   ↓
2. GitHub Actions inicia workflow `docker-publish.yml`
   ↓
3. Job: build-and-publish (ubuntu-latest)
   ├─ Checkout código
   ├─ Setup Docker Buildx + QEMU
   ├─ Login a Docker Hub
   ├─ Compila Dockerfile
   ├─ Genera tags (latest, sha-xxxxx)
   └─ Push a Docker Hub
   ↓
4. Job: deploy-to-k8s (self-hosted en cluster)
   ├─ Checkout manifests
   ├─ Install kubectl
   ├─ kubectl set image deployment/backend-app (nueva imagen)
   ├─ kubectl rollout status (espera completarse)
   └─ kubectl verify (muestra estado)
   ↓
5. Kubernetes en namespace `ips`
   ├─ Termina 2 pods viejos (graceful)
   ├─ Lanza 2 pods nuevos (rolling update)
   └─ Service redirige tráfico (sin downtime)
   ↓
6. ✅ Aplicación actualizada y accesible
```

---

## Componentes

### 1. GitHub Actions Secrets & Variables

**Secrets** (Settings → Secrets and variables):
- `DOCKERHUB_USERNAME`: Tu usuario Docker Hub
- `DOCKERHUB_TOKEN`: Token generado desde Docker Hub

**Variables**:
- `REGISTRY`: docker.io
- `DEPLOYMENT_NAME`: backend-app
- `NAMESPACE`: ips

### 2. GitHub Personal Access Token (PAT)

Generado en: GitHub Settings → Developer settings → Tokens (classic)
- **Name:** k8s-runner-token
- **Scope:** repo
- **Token:** ghp_xxxxx...

### 3. Dockerfile (Multi-etapa)

```dockerfile
# BUILD: Compila con Maven
FROM maven:3.9.9-eclipse-temurin-21 AS builder
RUN mvn dependency:resolve
RUN mvn clean package -DskipTests

# RUNTIME: Ejecuta con JRE optimizado
FROM eclipse-temurin:21-jre-jammy
COPY --from=builder /app/target/app.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Ventaja:** Reduce tamaño imagen (800MB → 400MB)

### 4. Kubernetes Manifests

#### 4.1 Namespace
```bash
kubectl create namespace ips
```

#### 4.2 Docker Registry Secret
```bash
kubectl create secret docker-registry mi-registro-secreto \
  --docker-server=https://index.docker.io/v1/ \
  --docker-username=tu_usuario \
  --docker-password=tu_token \
  --docker-email=tu_email@ejemplo.com \
  --namespace=ips
```

⚠️ **CRÍTICO:** Incluir `--namespace=ips` (el profesor NO lo puso en el README original)

#### 4.3 Deployment (`deployment.yml`)
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: backend-app
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
      - name: backend-app
        image: darchery/ips-cicd-docker:latest
        imagePullPolicy: Always
        ports:
        - containerPort: 8080
```

**Qué hace:**
- Crea 2 copias del pod (replicas)
- Usa imagen desde Docker Hub
- Usa secreto para descargar imagen privada
- Expone puerto 8080

#### 4.4 Service (`service.yml`)
```yaml
apiVersion: v1
kind: Service
metadata:
  name: mi-app-service
  namespace: ips
spec:
  type: NodePort
  selector:
    app: mi-app
  ports:
    - protocol: TCP
      port: 8080
      nodePort: 30007
```

**Qué hace:**
- NodePort: Expone aplicación en puerto 30007
- Acceso: http://localhost:30007

### 5. GitHub Actions Workflow (`docker-publish.yml`)

#### Job 1: build-and-publish
```yaml
build-and-publish:
  runs-on: ubuntu-latest
  steps:
    - Checkout código
    - Setup Docker Buildx + QEMU
    - Login a Docker Hub
    - Extract metadata (tags)
    - Build & Push imagen
```

**Descripción:**
- Compila la aplicación Spring Boot
- Crea imagen Docker optimizada (multi-stage)
- Sube imagen a Docker Hub con tags automáticos
- Se ejecuta en servidores de GitHub (ubuntu-latest)

#### Job 2: deploy-to-k8s
```yaml
deploy-to-k8s:
  needs: build-and-publish  # Espera a que termine job anterior
  runs-on: self-hosted      # Corre en runner dentro del cluster
  steps:
    - Checkout manifests
    - Install kubectl
    - kubectl set image (actualiza deployment)
    - kubectl rollout status (espera completarse)
    - kubectl verify (muestra estado)
```

**Descripción:**
- Se ejecuta SOLO después de que build-and-publish termine
- Corre en el cluster Kubernetes (self-hosted runner vía ARC)
- Actualiza la imagen del deployment con la versión nueva
- Espera a que los pods nuevos levanten correctamente

### 6. ARC (Actions Runner Controller)

Permite que tus jobs de GitHub Actions corra dentro del cluster Kubernetes.

#### Instalación:

**ARC Controller:**
```bash
helm install arc \
  --namespace ips \
  --create-namespace \
  oci://ghcr.io/actions/actions-runner-controller-charts/gha-runner-scale-set-controller
```

**Runner Scale Set:**
```bash
helm install self-hosted \
  --namespace ips \
  --set githubConfigUrl="https://github.com/darchery/ipscicd-docker" \
  --set githubConfigSecret.github_token="ghp_xxx..." \
  oci://ghcr.io/actions/actions-runner-controller-charts/gha-runner-scale-set
```

**Qué hace:**
- Controller: Administra el ciclo de vida del runner
- Scale Set: Pool de runners que ejecutan jobs de GitHub Actions

### 7. RBAC (Role-Based Access Control)

#### Role (`runner-permissions.yml`)
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

**Qué define:**
- Permisos para manipular Deployments y ReplicaSets
- Permisos para leer/modificar Pods y Services
- Scope: Solo namespace `ips`

#### RoleBinding (conecta Role con ServiceAccount)
```yaml
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
```

**Qué hace:**
- Asigna el Role `gha-runner-role` al ServiceAccount del runner
- Permite que el runner ejecute `kubectl set image`, `kubectl rollout`, etc.

---

## Despliegue

### Pasos de Despliegue Manual

```bash
# 1. Crear namespace
kubectl create namespace ips

# 2. Crear secret Docker Hub
kubectl create secret docker-registry mi-registro-secreto \
  --docker-server=https://index.docker.io/v1/ \
  --docker-username=tu_usuario \
  --docker-password=tu_token \
  --docker-email=tu_email \
  --namespace=ips

# 3. Aplicar RBAC (Role)
kubectl apply -f runner-permissions.yml

# 4. Aplicar RoleBinding
kubectl apply -f runner-rolebinding.yml

# 5. Aplicar manifests Kubernetes
kubectl apply -f deployment.yml
kubectl apply -f service.yml

# 6. Instalar ARC Controller
helm install arc --namespace ips \
  oci://ghcr.io/actions/actions-runner-controller-charts/gha-runner-scale-set-controller

# 7. Instalar Runner Scale Set
helm install self-hosted \
  --namespace ips \
  --set githubConfigUrl="https://github.com/tu_user/tu_repo" \
  --set githubConfigSecret.github_token="ghp_xxx" \
  oci://ghcr.io/actions/actions-runner-controller-charts/gha-runner-scale-set
```

### Despliegue Automático

Solo haz `git push main` y GitHub Actions se encarga de todo:
1. Compila imagen Docker
2. Sube a Docker Hub
3. Actualiza deployment en Kubernetes
4. Pods nuevos levantan automáticamente
5. Sin downtime (rolling update)

---

## Verificación

### Estado del Cluster

```bash
# Ver todos los recursos
kubectl get all -n ips

# Ver pods
kubectl get pods -n ips

# Ver deployment
kubectl get deployment backend-app -n ips

# Ver service
kubectl get service mi-app-service -n ips

# Ver logs de un pod
kubectl logs -f deployment/backend-app -n ips

# Verificar permisos del runner
kubectl auth can-i patch deployment/backend-app \
  --as=system:serviceaccount:ips:self-hosted-gha-rs-no-permission \
  -n ips
# Respuesta esperada: yes

# Ver eventos del namespace
kubectl get events -n ips --sort-by='.lastTimestamp'
```

### Acceder a la Aplicación

```bash
# Opción 1: localhost (si en Docker Desktop)
curl http://localhost:30007

# Opción 2: IP del nodo
curl http://tu-ip:30007

# Swagger UI (documentación API)
http://localhost:30007/swagger-ui.html

# Health check
curl http://localhost:30007/actuator/health
```

### Monitorear Despliegue en GitHub Actions

1. Ve a tu repo GitHub
2. Actions tab
3. Selecciona el workflow más reciente
4. Observa:
   - Job `build-and-publish` (ubuntu-latest) - Construye imagen
   - Job `deploy-to-k8s` (self-hosted) - Despliega en K8s
5. Verifica que ambos pasan ✅

---

## Errores Comunes

| Error | Causa | Solución |
|---|---|---|
| `ImagePullBackOff` | Secret no en namespace ips | Incluir `--namespace=ips` en comando secret |
| `no permission` en deploy-to-k8s | RoleBinding no creado | Aplicar `runner-rolebinding.yml` |
| Runner no aparece en GitHub | Token inválido o URL incorrecta | Regenerar PAT y helm install |
| `kubectl: not found` | kubectl no instalado en runner | Se instala automático en step |
| Pod no levanta | Imagen privada sin secreto | Verificar `imagePullSecrets` en deployment |
| Deploy sin cambios | Imagen tag es igual | Asegurar nuevo tag en build (SHA) |
| Pods old no terminan | Timeout en rollout | Aumentar timeout o checkear logs |

---

## Conceptos Clave

### Continuous Integration (CI)
Proceso automatizado de compilar, testear y empaquetar código después de cada push.

**En esta práctica:**
- GitHub Actions compila automáticamente
- Docker build crea imagen
- Docker push sube a registry

### Continuous Deployment (CD)
Despliegue automático de nuevas versiones en producción sin intervención manual.

**En esta práctica:**
- ARC runner ejecuta en cluster
- kubectl actualiza deployment
- Rolling update sin downtime

### Rolling Update
Técnica de despliegue que termina pods viejos gradualmente y lanza nuevos.

**Ventaja:** Cero downtime

**Cómo funciona:**
1. Crea pod nuevo con imagen nueva
2. Si es healthy, termina un pod viejo
3. Repite hasta todos sean nuevos

### Multi-stage Docker Build
Dockerfile con múltiples etapas (BUILD + RUNTIME) para optimizar tamaño.

**Ventaja:** Imagen final sin herramientas de compilación (400MB vs 800MB)

---

## Resumen Ejecutivo

✅ **La práctica demuestra:**
- **CI:** Compilación automática con GitHub Actions
- **Containerización:** Dockerfile optimizado multi-etapa
- **CD:** Despliegue automático en Kubernetes
- **Orquestación:** Rolling updates sin downtime
- **Automatización:** Un push = aplicación actualizada
- **Seguridad:** RBAC + secretos + runners self-hosted

**Flujo completo:**
```
Developer Push → GitHub → Build Docker → Push Registry → Deploy K8s → App Live ✅
```

**Conclusión:** Pipeline CI/CD completo desde código a producción. 🚀

---

## Referencias

- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [Docker Multi-stage Builds](https://docs.docker.com/build/building/multi-stage/)
- [GitHub Actions Documentation](https://docs.github.com/en/actions)
- [Kubernetes Documentation](https://kubernetes.io/docs/)
- [Actions Runner Controller (ARC)](https://github.com/actions/actions-runner-controller)
