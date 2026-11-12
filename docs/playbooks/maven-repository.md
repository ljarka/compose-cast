# Playbook — Maven repository (Reposilite on the VPS)

Hosts `io.composecast:*` artifacts and the `io.composecast.app` Gradle plugin
([ADR 0019](../decisions/0019-self-hosted-maven-reposilite.md)). Done once by the owner on the VPS;
agents only change this file when the setup changes. Commands verified against the Reposilite docs
(https://reposilite.com/guide/docker, /tokens, /routes, /gradle) on 2026-11-08; the end-to-end
setup is checked by T-024.

## 1. Cloudflare Tunnel
Cloudflare dashboard → Zero Trust → Networks → Tunnels → create tunnel → copy its token.
Public hostname: `maven.<domain>` → service `http://reposilite:8080`.
Do **not** put Cloudflare Access in front of `/releases` (Gradle cannot log in).

## 2. `~/maven/docker-compose.yml` on the VPS
```yaml
services:
  reposilite:
    image: dzikoysk/reposilite:3.6.6
    restart: unless-stopped
    environment:
      JAVA_OPTS: "-Xmx256M"
      # first start only, remove after step 3:
      REPOSILITE_OPTS: "--token admin:${REPOSILITE_BOOTSTRAP_SECRET}"
    volumes:
      - reposilite-data:/app/data
  cloudflared:
    image: cloudflare/cloudflared:2026.10.7
    restart: unless-stopped
    command: tunnel --no-autoupdate run --token ${CLOUDFLARE_TUNNEL_TOKEN}
volumes:
  reposilite-data:
```
`~/maven/.env`: `REPOSILITE_BOOTSTRAP_SECRET=<random>`, `CLOUDFLARE_TUNNEL_TOKEN=<from step 1>`.
No `ports:`: only the tunnel reaches Reposilite.

```bash
docker compose up -d && docker compose logs -f reposilite   # wait for "Uptime"
```

## 3. Tokens (web UI `https://maven.<domain>` → log in as `admin` → Console)
```
token-generate admin-<you> m                 # your permanent manager token (note the secret)
token-generate ci                            # publisher token for GitHub Actions (note the secret)
route-add ci /releases rw
```
Then remove `REPOSILITE_OPTS` from the compose file and `docker compose up -d`. In the settings,
keep the `releases` repository **public** (anonymous read) and redeployment **off** (a released
version can never change).

## 4. GitHub
Repository → Settings → Secrets → Actions: `COMPOSECAST_MAVEN_USERNAME=ci`,
`COMPOSECAST_MAVEN_PASSWORD=<ci secret>`; variable `COMPOSECAST_MAVEN_URL=https://maven.<domain>/releases`.

## 5. Check
```bash
curl -fsS -o /dev/null -w '%{http_code}\n' https://maven.<domain>/releases/   # 200 without auth
```
After the first release:
`curl -fsS https://maven.<domain>/releases/io/composecast/sdk-android/maven-metadata.xml`.

## Consumers (app repositories)
Template with these blocks: `distribution/android-app/` ([new-app-repository.md](new-app-repository.md)).
```kotlin
// settings.gradle.kts
pluginManagement {
    repositories { maven("https://maven.<domain>/releases"); google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositories { maven("https://maven.<domain>/releases"); google(); mavenCentral() }
}
```

## Backup / upgrade
Back up the `reposilite-data` volume (`docker run --rm -v maven_reposilite-data:/d -v $PWD:/b alpine
tar czf /b/reposilite-$(date +%F).tgz -C /d .`). Upgrade: bump the image tag, `docker compose pull &&
docker compose up -d`; read the Reposilite release notes first.
