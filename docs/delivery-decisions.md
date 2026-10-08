# Configuration, containers and delivery decisions

## Runtime configuration

Environment variables configure database access, JWT signing, token lifetime and the external provider address. The same application artifact can therefore be configured for local execution or container execution without embedding credentials into source code.

Ordinary configuration has explicit defaults where appropriate. Database passwords and signing keys remain required values. This prevents a deployment from silently starting with a shared example credential.

Flyway applies versioned database migrations, while Hibernate validates the resulting schema. The database structure is therefore described by migrations instead of being implicitly altered from entity mappings during application startup.

The demo profile supplies an account and a saved Pokemon for evaluation. It uses local seed information and checks for existing records before creating them. This avoids depending on PokeAPI availability for the initial demonstration data and allows repeated normal demo startup without duplicating those fixtures.

## Docker packaging

Backend and frontend use separate multi-stage Docker builds. Build tools remain in the build stages, while runtime stages contain the packaged Java application or the generated frontend assets. This separates compilation requirements from application execution.

The backend runs under a non-root user. The frontend is served by nginx, which also forwards API requests to the backend. The signing key remains a backend runtime setting and never becomes part of the generated browser assets.

Docker Compose provides the local development topology: PostgreSQL, backend and frontend. PostgreSQL uses a persistent volume, and backend startup depends on its health check. Local port bindings use loopback addresses. Container shutdown can preserve the volume so stopping the application does not automatically remove saved records.

## GitHub Actions

Backend and frontend validation run as independent jobs. This gives each area its own result and allows validation to run concurrently. Reports are uploaded even when a validation job fails, making failures available for inspection after the runner exits.

On the main branch, image publication depends on both validation jobs succeeding. Pull requests validate changes without publishing images. The image job authenticates to GitHub Container Registry using the automatically supplied workflow token and a package-write permission scoped to that job.

Both application images are tagged with the complete commit SHA. A deployment can select backend and frontend artifacts from the same revision instead of relying on a moving latest tag. Source and revision metadata are also attached to the images.

The first home lab release passed backend and frontend validation, published both images and deployed the application successfully with Compose on 2026-10-08. These checks verify the implemented delivery path; Kubernetes was not used.

## Compose home lab

The selected deployment direction is Docker Compose on jl-S. Inspection confirmed that the server already uses Compose and its former k3s service is inactive. The application will use the existing shared PostgreSQL service with a dedicated database and role, rather than starting a second database instance.

The deployment Compose file contains only backend and frontend services. A private application network connects nginx to the backend; the backend also joins homelab to reach PostgreSQL. The frontend binds port 18082 to the verified Tailscale address. Restart policies match the existing server topology.

The GitHub environment remains named k8s-dev to preserve its secrets and branch restriction. That historical name does not select the runtime platform. Database provisioning is a separate operator step so the deployment pipeline needs only application credentials, not database administration access.

The deployment job follows successful image publication, selects both images from the same commit and checks application health after Compose updates the containers. A repository variable keeps deployment disabled until the application's database and dedicated runner are ready.

The public repository makes a persistent runner on the shared home lab a security decision: restricting this job to main does not prevent another workflow from targeting that runner. Runner isolation and approval policy must be considered before permanent registration. The first release used a one-job ephemeral runner under an explicit one-time authorization, and its dedicated database was provisioned successfully. Image publication and startup passed in the release pipeline. The runner was removed after the job, and future deployment was disabled until a durable runner/access solution is selected. The Kubernetes resources remain inactive alternatives.
