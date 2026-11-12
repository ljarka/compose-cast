# Playbook — Maven repository (Reposilite on the VPS)

**Current instance: `https://maven.jarka.club`** (repository `releases`), set up 2026-11-12; it runs on
the owner's machine for now (`~/maven`, port `127.0.0.1:8090`, reached through a Cloudflare tunnel
run outside Docker) — publishing works only while that machine is on; moving it to the VPS is planned.

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
Then remove `REPOSILITE_OPTS` from the compose file and `docker compose up -d` (the bootstrap
`admin` token stops working: check it answers 401).

Repository settings are in the **Configuration** tab (shared configuration, not the local
`configuration.cdn`) → Maven → Repositories, and are stored only after **Submit**:
- `releases`: visibility **PUBLIC** (anonymous read), redeployment **off** (a released version can
  never change), no proxied repositories;
- `snapshots`, `private`: **PRIVATE** (or delete them).

Pitfalls (2026-11-12):
- Route permissions belong to the token **record**: after `token-revoke` + `token-generate` of the
  same name, run `route-add` again (a re-created `ci` had no route and could not publish).
- The secret is shown once; the bot token is `ci` (route `/releases rw` only), never the manager token.
- Checking without secrets: the SQLite file in the volume (`docker cp <container>:/app/data/reposilite.db`,
  open read-only) has tables `access_token`, `permission_route`, `permission_access_token` (names and
  routes, secrets are hashed) and `settings` (`remote-shared-configuration` JSON, `maven.repositories`).

Security checklist: redeployment off; only `releases` public; `ci` has only `/releases rw`; bootstrap
token removed; anonymous `PUT` → 401; optional Cloudflare Access on the whole host with a **Bypass**
application for `/releases` (Gradle reads and publishes only under `/releases/…`); keep the image
updated (https://github.com/dzikoysk/reposilite/releases).

## 4. GitHub
Repository → Settings → Secrets → Actions: `COMPOSECAST_MAVEN_USERNAME=ci`,
`COMPOSECAST_MAVEN_PASSWORD=<ci secret>`; variable `COMPOSECAST_MAVEN_URL=https://maven.<domain>/releases`
(current: `https://maven.jarka.club/releases`, set 2026-11-12).

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
