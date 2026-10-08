# Home lab delivery with Compose

## Deployment decision

PokeSync will run on jl-S using Docker Compose. The server already runs Compose stacks and has a shared PostgreSQL container on the external homelab network. Its former k3s service is inactive. Reusing that topology avoids introducing another orchestration system or another general-purpose database instance for this application.

The deployment Compose file contains only backend and frontend services. The backend joins homelab to reach the existing postgres service, using a dedicated pokesync database and login role. Both application services share their own Compose network so nginx can reach the backend without exposing an additional backend port.

The frontend binds port 18082 to the server's Tailscale IPv4 address, 100.104.27.43. That port was free during host inspection. The intended application address is http://100.104.27.43:18082, subject to tailnet access policy. PostgreSQL remains unpublished on the host.

The default application profile is used. This deployment does not automatically create the demonstration account. Restart policies allow the application containers to return after Docker restarts, unless an operator explicitly stopped them.

## Delivery pipeline

Backend and frontend validation run on GitHub-hosted runners. Successful validation on main permits image publication to GHCR. Deployment then uses both images from the same commit SHA, pulls them before updating the application and checks backend health through nginx followed by frontend availability.

The deployment job targets a dedicated Linux x64 runner labeled pokesync-dev on jl-S. Runners registered for other repositories are not reused. Deployment jobs are serialized, and new pushes do not cancel an active main-branch deployment. Pull requests continue to validate without publishing or deploying.

The repository variable HOMELAB_DEPLOY_ENABLED must be true before the deployment job can run. It was enabled for the first release and reset to false after successful deployment and runner cleanup. Future delivery requires an available runner and deliberate activation; the running application continues independently of this variable.

The existing GitHub environment remains named k8s-dev to preserve its configured secrets and branch restriction; the name is a historical label and does not select Kubernetes. Its HOMELAB_BIND_IP variable identifies the target bind address. DB_PASSWORD and JWT_SECRET are delivered only to the deployment steps that need them.

The runner stores the deployment definition in /home/jl/dev/pokesync/compose.yml. Runtime values are supplied through the job environment; no local secret file is generated. Docker retains the container configuration required for restart. Temporary registry authentication is isolated from the runner's usual Docker configuration and removed after the job.

This workflow does not provide automatic rollback. If startup fails, the job reports failure; an operator must inspect the issue and deliberately redeploy a suitable revision. Database migrations also require compatibility consideration before reverting an application version.

## One-time database provisioning

The operator runs the provisioning helper directly on jl-S. It requires Docker access, OpenSSL and authenticated GitHub CLI access to salazarpp/ITV.

```bash
bash deploy/provision-home-lab.sh
```

The helper checks that the pokesync role and database do not already exist. It generates a random application password, updates the GitHub environment secret and creates a login role and an owned database. It does not grant superuser privileges or change other applications' databases.

Provisioning is deliberately separate from the deployment job. The pipeline receives only the application's database password, not an administrative PostgreSQL password. Flyway creates the application tables when the backend starts.

Initial provisioning succeeded on 2026-10-08 under an explicit one-time release authorization. If provisioning fails after storing the secret or creating the role, stop and inspect the partial result before trying again. The helper refuses to overwrite an existing role or database. The new password is stored in GitHub before database creation, so successful provisioning and future deployment use the same value.

## Runner and activation

The first release used a one-job ephemeral runner for salazarpp/ITV, operated by jl on jl-S, with the additional label pokesync-dev. Its listener started only after the expected main-branch validation and image jobs succeeded. It removed its registration after deployment, and its temporary installation was deleted; no permanent runner service was installed. A future dedicated runner needs Docker, Compose and curl and must be running before deployment is enabled. Registration credentials must be supplied directly to the runner tooling, never committed or printed in documentation.

Because the repository is public, a persistent runner with access to the home lab and Docker introduces risk from untrusted workflow code. The deployment job is restricted to main, but labels and conditions in this workflow do not prevent another workflow from targeting the same runner. Do not treat this file as isolation; assess runner isolation and workflow approval policy before registering a persistent runner on the shared server.

Once the database and runner are ready, the operator enables delivery:

```bash
gh variable set HOMELAB_DEPLOY_ENABLED --repo salazarpp/ITV --body true
```

The changed workflow must first be committed and pushed by the operator. The assistant does not commit or push from jl-l. The next successful main-branch pipeline can then publish and deploy its images.

## Verified and pending

Host inspection verified Docker and Compose, the shared homelab network, the running PostgreSQL container's health status and available host resources. YAML and shell syntax can be checked without starting containers.

The first release completed successfully on 2026-10-08, deploying application commit f8a43f7. The dedicated database and role were created, all validation jobs passed, both images were published and the Compose deployment job passed its HTTP checks. Independent HTTP checks confirmed health UP, frontend status 200 and live Pokemon browsing returning Bulbasaur. Existing server containers retained their running status during the release.

Full manual authenticated CRUD and visual review remain pending. A future release still needs an available runner; the temporary first-release runner does not provide permanent automation. The Kubernetes manifests remain inactive alternatives; they are not used by this delivery job.
