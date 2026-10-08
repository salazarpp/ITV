# Home lab Kubernetes delivery

The current deployment choice is Docker Compose with the existing shared PostgreSQL service. These Kubernetes resources are retained as inactive alternatives and are not used by GitHub Actions deployment.

The destination is the Kubernetes home lab on `jl-s`. The GitHub environment is `k8s-dev`; the Kubernetes namespace is `pokesync-dev`. There is no Azure dependency.

## Pipeline and images

The existing backend/frontend CI jobs run on GitHub-hosted runners. After both succeed on `main`, the image job builds and publishes:

- `ghcr.io/salazarpp/itv-backend:<commit-sha>`
- `ghcr.io/salazarpp/itv-frontend:<commit-sha>`

Pull requests run validation without publishing images. Publication authenticates with GitHub's automatically supplied `GITHUB_TOKEN` and job-scoped `packages: write`; no registry password needs to be configured for publication. Image builds skip backend tests because the preceding CI jobs run them.

New GHCR packages are private by default, even when their source repository is public. Either make these demonstration images public in package settings, or configure a Kubernetes image pull Secret with a token that has `read:packages`. Never use the short-lived CI publication token as a persistent cluster credential.

## GitHub environment and secrets

`k8s-dev` was created in `salazarpp/ITV` on 2026-10-07 and allows the `main` branch. Two independently generated secrets were uploaded directly to GitHub without printing their values or saving local secret files:

| Environment secret | Consumer |
| --- | --- |
| `DB_PASSWORD` | PostgreSQL password and backend database connection |
| `JWT_SECRET` | Backend HS256 signing key; Base64 encoding of 32 random bytes |

GitHub does not return stored secret values. The deployment job must reference `environment: k8s-dev` to receive these secrets and supply them as the Kubernetes Secret `pokesync-runtime`, with keys `DB_PASSWORD` and `JWT_SECRET`. The manifests only contain Secret references.

Cluster access is still undecided: a runner on `jl-s`, or a GitHub-hosted runner with network access and cluster credentials. The automated deployment job is pending that decision. CI and image publication do not consume runtime secrets or access the home lab.

## Kubernetes resources

`deploy/k8s` contains a Kustomize configuration with:

- Namespace and non-secret application ConfigMap.
- Single-instance PostgreSQL Deployment, internal Service and a 5 GiB persistent volume claim.
- Backend Deployment with startup, readiness and liveness probes, and an internal Service named `backend` for nginx.
- Frontend Deployment and internal Service, preserving same-origin API routing.

The cluster needs a default StorageClass that can provision a `ReadWriteOnce` volume. Adjust the claim for the actual home lab storage before applying. These manifests are a development setup; PostgreSQL backups and production availability are not configured. Password changes in a Secret do not update the password of an already initialized PostgreSQL volume; coordinate future password rotation with the database.

Application images are deliberate placeholders until a successful CI commit is selected. From a temporary copy of `deploy/k8s`, use Kustomize's `images` entries to map `pokesync-backend` and `pokesync-frontend` to the GHCR image names and the same complete commit SHA. Do not deploy mutable `latest` tags.

Keep `SPRING_PROFILES_ACTIVE: default` for normal operation. Set it to `demo` only when you intentionally want the documented demonstration account and seed Pokemon.

## Commands for the operator

After configuring image mappings, cluster access, storage and the runtime Secret:

```bash
kubectl apply -k deploy/k8s
kubectl -n pokesync-dev rollout status deployment/database --timeout=300s
kubectl -n pokesync-dev rollout status deployment/backend --timeout=300s
kubectl -n pokesync-dev rollout status deployment/frontend --timeout=180s
kubectl -n pokesync-dev port-forward service/frontend 3000:80
```

Open `http://localhost:3000`. Database and backend Services stay internal. Ingress, TLS, DNS and external exposure await the actual home lab configuration.

These commands have not been executed. YAML syntax was checked; Kubernetes schema/rendering and cluster behavior remain unverified because `kubectl` and a deployment target are not available locally.

References: [GitHub image publishing](https://docs.github.com/en/actions/tutorials/publish-packages/publish-docker-images), [GHCR access](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-container-registry), [GitHub environments](https://docs.github.com/en/actions/how-tos/deploy/configure-and-manage-deployments/manage-environments), [Kustomize](https://kubernetes.io/docs/tasks/manage-kubernetes-objects/kustomization/).
