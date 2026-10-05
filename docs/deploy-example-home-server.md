# Worked example: a shared home server with Nginx Proxy Manager

How MemberTracker was put on a home server that already ran other apps in Docker behind Nginx Proxy Manager (NPM), reached from the internet through a router port forward. It is a record of what was done and what went wrong, written so the next deployment (or a rebuild after a disk failure) takes an hour, not an evening. The general steps are in [deploy-linux.md](deploy-linux.md); this file is the same route on one real machine. Names below are placeholders: `server` is the machine, `members.example.org` the address people open, `8191` the port the router forwards.

The machine: Ubuntu 24.04 on an older 4-core Intel with 7 GB of memory, Docker and Compose from Ubuntu's packages (no buildx), NPM in a container owning ports 80, 81 and 443 on the network `docker_proxynet`, a host MySQL/MariaDB/PostgreSQL already running, and ufw active. Other apps lived there and had to stay untouched.

## What was decided

| Decision | Choice | Why |
|----------|--------|-----|
| How to run it | Docker: `docker-compose.server.yml` (MySQL 8.4 and the app) | The server already ran Docker; no Java or systemd needed |
| Database | The stack's own MySQL container, no published port | The host's MySQL/MariaDB are used by other apps and port 3306 was taken; MariaDB is not what this app is tested on |
| Where things live | `~/membertracker` (repo, `.env`, `data/mysql`, `backups`) | The admin account had no passwordless `sudo`, and `/opt` and `/srv` belong to root. Everything under the home folder needs no `sudo`. Use `/opt/membertracker` and `/srv/membertracker/mysql` when you have it |
| Front door | The existing NPM, app on its network, no host port needed | One entry point for all apps; the certificate (a wildcard) already existed |
| Address | `https://members.example.org:8191`, DNS needs no new record when the dynamic-DNS name is a wildcard | Same router forward as the other apps |
| Getting the code | `git clone` over https (the repo is public); the image is built on the server | No deploy key, no image transfer. First build took about 15 minutes on that CPU, later ones about 3 |
| Access for the work | SSH from one computer, LAN only, key only; revoked afterwards | The server's web terminal is too limiting for a deployment |

## The steps, in order

1. **Look first, change nothing.** From the server's terminal: `docker ps`, `docker network ls`, `ss -ltnp`, `sudo ufw status`, free memory and disk, and which databases run. This decided everything above.
2. **Open SSH to the LAN only**, if you want to work from your own computer: `sudo ufw allow from 192.168.0.0/16 to any port 22 proto tcp comment 'ssh-lan-temp'` and add your public key to `~/.ssh/authorized_keys`. On the first connection compare the host key fingerprint with `ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub` on the server before trusting it.
3. **Clone and configure.** `git clone https://github.com/amenski/church-membership.git ~/membertracker`. Write `.env` with `umask 077`, the three secrets from `openssl rand` on the server itself (they never travel or get printed), the first administrator's email and a strong password, `COOKIE_SECURE=true`, a free `APP_PORT`, `TZ`, and `MYSQL_DATA_DIR`. Every variable is explained in `deploy/server.env.example`.
4. **Join the proxy network without editing tracked files.** Create `docker-compose.proxy.yml` (see [D5 of deploy-linux.md](deploy-linux.md#d5-the-front-door-server), point 5) and list it in `.git/info/exclude`, so `git pull` never conflicts. Because the box is shared it also caps MySQL (`mem_limit: 512m`, `--performance-schema=OFF`, `--innodb-buffer-pool-size=128M`).
5. **Check, then build and start.** `docker compose -f docker-compose.server.yml -f docker-compose.proxy.yml config -q`, then `nohup nice -n 10 docker compose -f docker-compose.server.yml -f docker-compose.proxy.yml up -d --build --wait > deploy.log 2>&1 &` (`nice` and `nohup`: the build is heavy and the connection can drop). Expect both containers `healthy`; the app log says `Created the first administrator`.
6. **Check from inside the proxy network** before touching the proxy: `docker exec npm curl -s -o /dev/null -w '%{http_code}\n' http://membertracker-app:8080/` prints 200.
7. **Add the proxy host.** The NPM web login was not available, so a hand-written config was used: [D5 of deploy-linux.md](deploy-linux.md#d5-the-front-door-server), points 6 and 7, has the block. Always `nginx -t` before `nginx -s reload`, and keep the previous file (`http.conf.bak`).
8. **Sign in as the administrator**, change the password, then delete the `BOOTSTRAP_ADMIN_PASSWORD` line from `.env` and run `up -d` again.
9. **Back up.** Run `deploy/backup-mysql.sh` once by hand (with `ENV_FILE` and `BACKUP_DIR` set to the home-folder paths), check the dump has its tables (`zcat file | grep -c '^CREATE TABLE'`), then add the cron line to the user's crontab (`15 2 * * *`), keeping the existing lines.
10. **Undo what was temporary.** Delete the SSH rule (`sudo ufw delete allow from 192.168.0.0/16 to any port 22 proto tcp`) and the key line.

## Updating (what was done for the first fix)

```bash
cd ~/membertracker
git pull --ff-only
docker tag membertracker:local membertracker:previous      # rollback point
nohup nice -n 10 docker compose -f docker-compose.server.yml -f docker-compose.proxy.yml up -d --build --wait > deploy.log 2>&1 &
```

Rollback: tag `membertracker:previous` back to `membertracker:local` and run `up -d --no-build --wait` (see [D10](deploy-linux.md#d10-updating-and-rolling-back)).

## What went wrong, and the fix

| Problem | Cause | Fix |
|---------|-------|-----|
| The first SSH attempt went to the wrong machine | A pasted command named another server | Read-only checks only, nothing changed; confirm the target before connecting |
| SSH timed out from the computer though `sshd` ran | ufw had no rule for port 22 | The LAN-only rule above |
| `docker: unknown command: docker buildx` | Ubuntu's `docker.io` has no buildx | Nothing: compose uses the classic builder and the Dockerfile has no BuildKit-only features |
| `.env` / config file written empty | `docker exec` without `-i` gets no standard input | `docker exec -i` |
| The page was blank, `/assets/...` 403 in the browser, 200 with curl | The proxy hid the non-443 port; the app's Origin check failed. Putting the port in `Host` was not enough | `X-Forwarded-Host $http_host` and `X-Forwarded-Proto $scheme` |
| After sign-in the login page stayed until a refresh | The app navigated to `/`, which redirects to `/login`: a duplicate navigation, dropped | The app now goes to the user's home page (fixed in the app, test added) |
| NPM web login forgotten | – | Hand-written config instead; the web login can be reset later |

## Left for the hardening pass

Found while looking at the machine, not changed (other apps depend on them): the host's database listening on all interfaces, an admin tool on plain http, pending package updates and a reboot, SSH password login enabled, and for MemberTracker itself no per-address rate limit on sign-in and no two-factor sign-in. Backups still sit on the same disk: copy them off the machine and try one restore.
