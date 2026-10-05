# Deploy on one Linux machine

*Last checked against the code: 5 October 2026.*

A checklist for running MemberTracker on one Linux machine you control, such as a home or office server. Follow it from top to bottom. Every step has the exact command and a "You should see" line: if you do not see it, stop and look at [When something is wrong](#when-something-is-wrong). The reference tables (every variable, the Caddyfile, the first-start rules) stay in [development.md](development.md#deploy).

Commands use Debian or Ubuntu. Package names differ between releases, so where a name is not found, search for it (`apt search jre`).

```
browser --> Caddy (ports 80 and 443) --> MemberTracker (127.0.0.1:8080) --> MySQL in Docker (127.0.0.1:3306)
```

Only Caddy listens on the network. The database files are in `/srv/membertracker/mysql`, the backups in `/srv/membertracker/backups`, the program and its settings in `/opt/membertracker`.

**[server]** means run it on the Linux machine, **[computer]** on the machine where you build. Replace `admin@SERVER` with your login and the machine's name or address.

## 1. Decide how people will reach it

Pick one. Later steps say "Option A", "B" or "C".

| | Option A: same network only | Option B: a real domain | Option C: plain http |
|---|---|---|---|
| Address people open | `https://members.lan` | `https://members.yourchurch.org` | `http://SERVER-IP:8080` |
| What you need | A name that every device resolves to the server (a router DNS entry, or one line in each device's hosts file). Caddy signs its own certificate (`tls internal`) and each device trusts Caddy's root certificate once | A domain, a DNS A record pointing at your public IP, and ports 80 and 443 forwarded from the router to the server. Let's Encrypt certificate, renewed by Caddy | Nothing else. No Caddy: the jar serves the pages and the API itself, so the address works without a proxy |
| Settings | `COOKIE_SECURE=true` (default) | `COOKIE_SECURE=true` (default) | `COOKIE_SECURE=false` and `SERVER_ADDRESS=0.0.0.0` |

**Option C warning:** passwords and session cookies cross the network unencrypted, so anyone on that network can read them. Use it only on a network you fully trust, never on the internet.

## 2. Install the software [server]

Java 17, Docker with the compose plugin, and Caddy (Options A and B only).

```bash
sudo apt update
sudo apt install -y openjdk-17-jre-headless
java -version
```

You should see `openjdk version "17...`. If the package is not found, install the newest `openjdk-NN-jre-headless` that is 17 or higher; the project is built and tested with 17.

```bash
sudo apt install -y docker.io docker-compose-v2
docker --version
docker compose version
```

You should see a Docker version and `Docker Compose version v2...` (`up --wait` needs 2.1 or later). If `docker-compose-v2` is not found, install Docker from its own repository: <https://docs.docker.com/engine/install/> (the package is `docker-compose-plugin`).

```bash
sudo apt install -y caddy
caddy version
systemctl is-active caddy
```

You should see a version and `active`. If the package is not found, follow <https://caddyserver.com/docs/install#debian-ubuntu-raspbian>. Option C does not need Caddy.

## 3. Build the jar and copy the files [computer]

In the repository, with Java 17 (the first build downloads Node and the npm packages, so it needs the internet):

```bash
./gradlew bootJar -x test
ls -lh target/membertracker.jar
```

You should see `BUILD SUCCESSFUL` and a jar of about 65 to 70 MB. (`-x test` skips the tests: run `./gradlew test` before a release.)

```bash
ssh admin@SERVER 'mkdir -p ~/membertracker-release'
scp target/membertracker.jar docker-compose.yml .env.example admin@SERVER:membertracker-release/
scp -r deploy admin@SERVER:membertracker-release/
```

Then on the server:

```bash
sudo mkdir -p /opt/membertracker
sudo cp -r ~/membertracker-release/. /opt/membertracker/
sudo chmod +x /opt/membertracker/deploy/backup-mysql.sh
ls -A /opt/membertracker
```

You should see `.env.example  deploy  docker-compose.yml  membertracker.jar`.

## 4. Create the service user and the folders [server]

```bash
sudo useradd --system --home-dir /opt/membertracker --shell /usr/sbin/nologin membertracker
sudo mkdir -p /srv/membertracker/mysql /srv/membertracker/backups
sudo chmod 700 /srv/membertracker/backups
id membertracker
```

You should see `uid=... (membertracker)`. `/opt/membertracker` stays owned by root: the service only reads its jar and writes nothing there.

## 5. Database [server]

Make the settings file for MySQL. The first command puts a random root password and the data folder into `.env` (nobody has to type the password; read it back once and keep it in a password manager):

```bash
cd /opt/membertracker
sudo cp .env.example .env
sudo chmod 600 .env
sudo sed -i -e "s|^MYSQL_ROOT_PASSWORD=.*|MYSQL_ROOT_PASSWORD=$(openssl rand -hex 24)|" -e 's|^MYSQL_DATA_DIR=.*|MYSQL_DATA_DIR=/srv/membertracker/mysql|' .env
sudo grep -E '^MYSQL_(ROOT_PASSWORD|DATA_DIR)' .env
```

You should see a `MYSQL_ROOT_PASSWORD` of 48 letters and digits (not `password`) and `MYSQL_DATA_DIR=/srv/membertracker/mysql`. Leave `MYSQL_PORT=3306`. The password applies only when the data folder is empty, so do not change it in `.env` later: it would not change the database.

Start MySQL (the first start downloads the image and takes a minute or two):

```bash
sudo docker compose up -d --wait
sudo docker compose ps
```

You should see `membertracker-mysql` with `Up ... (healthy)`, listening on `127.0.0.1:3306->3306/tcp`.

Create the login the app uses (never root). Stay in this terminal until step 6: it prints the password you need there.

```bash
DB_PASSWORD=$(openssl rand -hex 24)
sudo docker exec -i membertracker-mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot' <<SQL
CREATE USER 'membertracker'@'%' IDENTIFIED BY '$DB_PASSWORD';
GRANT ALL PRIVILEGES ON felege_selam.* TO 'membertracker'@'%';
SQL
echo "DB_PASSWORD for step 6: $DB_PASSWORD"
sudo docker exec membertracker-mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -e "SHOW GRANTS FOR membertracker@\"%\""'
```

You should see your 48-character password and a line `GRANT ALL PRIVILEGES ON `felege_selam`.* TO `membertracker`@`%``. (The container already knows the root password, so these commands do not ask for it. `ALL PRIVILEGES` on `felege_selam` only is what Liquibase needs to create and change the tables.)

**Why `'%'` and not `'localhost'`:** the app runs on the host and reaches MySQL through the port Docker publishes. Docker forwards that connection into the container, so MySQL sees it coming from the Docker network's gateway address (checked on Docker Desktop: `192.168.117.1`; on Linux it is usually `172.x.0.1`), not from `localhost`. A login limited to `'localhost'` is refused with `Access denied`. `'%'` is safe here because the port is published on `127.0.0.1` only, so only programs on this machine can reach it, and the login has rights on `felege_selam` only.

## 6. The app's settings file [server]

```bash
sudo cp /opt/membertracker/deploy/membertracker.env.example /opt/membertracker/membertracker.env
sudo chown membertracker:membertracker /opt/membertracker/membertracker.env
sudo chmod 600 /opt/membertracker/membertracker.env
openssl rand -base64 48
sudo nano /opt/membertracker/membertracker.env
```

The `openssl` line prints the `JWT_SECRET`. In the file set:

| Variable | Value |
|----------|-------|
| `DB_PASSWORD` | the password printed in step 5 |
| `JWT_SECRET` | the `openssl rand -base64 48` output |
| `BOOTSTRAP_ADMIN_EMAIL` | the email of the first administrator |
| `BOOTSTRAP_ADMIN_PASSWORD` | a strong password: 8 to 72 characters with an uppercase letter, a lowercase letter, a digit and a special character. Used for the first start only (step 8) |
| `COOKIE_SECURE` | `true` for A and B. For C: `false`, and remove the `#` before `SERVER_ADDRESS=0.0.0.0` |
| `MAIL_*` (optional) | Email to members; nothing is sent until these are set. See [email.md](email.md#configuration) |

Leave `DB_USERNAME=membertracker`. Check:

```bash
sudo ls -l /opt/membertracker/membertracker.env
sudo grep -n '^[A-Z_]*=CHANGE_ME' /opt/membertracker/membertracker.env
```

You should see `-rw------- 1 membertracker membertracker` and no output from the `grep` (a line printed there is a value you still have to set).

## 7. Install the service and start it [server]

```bash
sudo cp /opt/membertracker/deploy/membertracker.service /etc/systemd/system/membertracker.service
sudo systemctl daemon-reload
sudo systemctl enable --now membertracker
journalctl -u membertracker -f
```

Wait for these lines (Liquibase creating the tables takes a few seconds first), then press Ctrl+C:

```
... Tomcat started on port 8080 (http) with context path '/'
... Started Application in 3.3 seconds (process running for 3.5)
... : Created the first administrator owner@example.org
```

Then check that the app answers on the loopback address:

```bash
systemctl is-active membertracker
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8080/api/users/me
```

You should see `active` and `401` (the app is up and asks for a sign-in).

## 8. Remove the first-start password [server]

The administrator now exists with the password you chose; the variable is no longer needed, and a password in a file is a risk you do not need.

```bash
sudo sed -i '/^BOOTSTRAP_ADMIN_PASSWORD=/d' /opt/membertracker/membertracker.env
sudo grep -c '^BOOTSTRAP_ADMIN_PASSWORD' /opt/membertracker/membertracker.env
sudo systemctl restart membertracker
journalctl -u membertracker -n 15 --no-pager
```

You should see `0`, and in the log `Started Application` but no `Created the first administrator` line. (Leaving `BOOTSTRAP_ADMIN_EMAIL` is harmless: it is used only while the users table is empty.) There is no password reset yet, so keep the password somewhere safe.

## 9. Caddy: the address people open [server] (Options A and B)

```bash
sudo cp /opt/membertracker/deploy/Caddyfile /etc/caddy/Caddyfile
```

Set the site name. Option A:

```bash
sudo sed -i -e 's/^members\.example\.org {/members.lan {/' -e 's/# tls internal/tls internal/' /etc/caddy/Caddyfile
```

Option B (use your domain):

```bash
sudo sed -i 's/^members\.example\.org {/members.yourchurch.org {/' /etc/caddy/Caddyfile
```

Check the file and load it:

```bash
sudo caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile
sudo systemctl reload caddy
systemctl is-active caddy
```

You should see `Valid configuration` and `active`. Check the proxy. `--resolve` sends the request to this machine whatever the name resolves to; `-k` skips the certificate check (Option A, which you fix below; for Option B drop `-k` and use your domain):

```bash
curl -sk --resolve members.lan:443:127.0.0.1 -o /dev/null -w '%{http_code}\n' https://members.lan/api/users/me
curl -sI --resolve members.lan:80:127.0.0.1 http://members.lan | head -n 1
```

You should see `401` (from the app, through Caddy) and a `308` redirect to https. For Option B, the first request needs a minute to get the certificate: `journalctl -u caddy -n 30 --no-pager` shows `certificate obtained successfully`.

**Option A only: make the name resolve and trust the certificate, once per device.**

1. Give the server a fixed address in the router (`hostname -I` shows the current one) and make `members.lan` point to it: a DNS or "host" entry in the router, or on each device the hosts-file line `192.168.1.20 members.lan` (use your address). Check from a device: `ping members.lan` shows that address.
2. Copy Caddy's root certificate off the server. It exists once Caddy has loaded the config:

   ```bash
   sudo cp /var/lib/caddy/.local/share/caddy/pki/authorities/local/root.crt ~/membertracker-root.crt
   sudo chown "$USER" ~/membertracker-root.crt
   ```

   Then copy `membertracker-root.crt` to each device (`scp`, a USB stick, a chat to yourself).
3. On each device, install it as a trusted certificate authority (menu names vary): Windows: double-click, Install Certificate, Local Machine, "Trusted Root Certification Authorities". macOS: Keychain Access, import into System, set "Always Trust". iPhone and iPad: open the file, install the profile in Settings, then Settings, General, About, Certificate Trust Settings, switch it on. Android: Settings, Security, Install a certificate, CA certificate. Firefox keeps its own list: Settings, Privacy and Security, Certificates, Import.

You should see `https://members.lan` open in the browser with a padlock and no warning (once the certificate is trusted, `curl https://members.lan/api/users/me` also works without `-k`). Keep the root certificate private to your own devices.

Option C: skip this step. People open `http://SERVER-IP:8080`.

## 10. Firewall [server]

Allow SSH first, or you lock yourself out (if SSH uses another port, allow that port instead of 22):

```bash
sudo apt install -y ufw
sudo ufw allow 22/tcp
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw enable
sudo ufw status
```

You should see `Status: active` and rules for 22, 80 and 443. Option B needs 80 open for the certificate. Option C: skip 80 and 443 and allow the app to your own network only: `sudo ufw allow from 192.168.1.0/24 to any port 8080 proto tcp` (use your network).

Docker publishes ports through its own firewall rules that ufw does not filter. That is why `docker-compose.yml` publishes MySQL on `127.0.0.1` only, and the app listens on `127.0.0.1` by default. Check what listens where:

```bash
sudo ss -ltnp
```

You should see MySQL (`docker-proxy`) on `127.0.0.1:3306`, `java` on `127.0.0.1:8080`, and Caddy on `*:80` and `*:443`, plus `127.0.0.1:2019` (Caddy's admin port, loopback only) and sshd. There must be no `0.0.0.0:3306` and no `*:8080`. (Option C: `java` on `*:8080` is expected, and there is no Caddy.) From another computer, `nc -zv SERVER-IP 3306` and `nc -zv SERVER-IP 8080` must fail.

## 11. Back up [server]

Run it once by hand:

```bash
sudo /opt/membertracker/deploy/backup-mysql.sh
ls -l /srv/membertracker/backups
```

You should see `backup-mysql: wrote /srv/membertracker/backups/felege_selam-YYYYMMDD-HHMMSS.sql.gz (... bytes), keeping the newest 14`. The script reads the root password from `/opt/membertracker/.env`, dumps `felege_selam` with `mysqldump --single-transaction --routines`, checks the gzip, and deletes all but the 14 newest. If anything fails it prints why, exits non-zero and keeps the older backups.

Nightly at 02:00, in root's crontab:

```bash
sudo crontab -e
```

Add this line, save, and check:

```
0 2 * * * /opt/membertracker/deploy/backup-mysql.sh >> /var/log/membertracker-backup.log 2>&1
```

```bash
sudo crontab -l
```

You should see the line. The next morning `ls -l /srv/membertracker/backups` shows a new file and `tail -n 3 /var/log/membertracker-backup.log` its `wrote` line.

**Copy the backups off the machine** (another computer or an external drive) every week or so. A backup on the same disk is lost with the disk, and it holds names, emails, phone numbers and password hashes, so keep the copy private. The folder is readable by root only, so copy it in three steps:

```bash
# [server]
sudo cp -r /srv/membertracker/backups ~/backups-copy && sudo chown -R "$USER" ~/backups-copy
# [computer]
scp -r admin@SERVER:backups-copy ~/membertracker-backups
# [server]
rm -r ~/backups-copy
```

**Restore test, once** (do it before you need it). It loads the newest backup into a scratch database and counts the members; the live data is not touched:

```bash
sudo -v   # asks for your password now, so the commands below do not stop to ask
F=$(sudo sh -c 'ls -1 /srv/membertracker/backups/*.sql.gz' | tail -n 1)
sudo docker exec membertracker-mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -e "CREATE DATABASE restore_test"'
sudo gzip -dc "$F" | sudo docker exec -i membertracker-mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot restore_test'
sudo docker exec membertracker-mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -e "SELECT COUNT(*) AS members FROM restore_test.member; DROP DATABASE restore_test"'
```

You should see `members` with the number of members in the app at the time of the backup.

## 12. Sign in and check

Open the address from step 1 and sign in with `BOOTSTRAP_ADMIN_EMAIL` and the password you chose.

1. You should see the **Overview** page load (all zeros on a new database).
2. **Members**, "Add member", enter a name, "Add member": the member appears in the list.
3. **Payments**, "Record payment", pick that member, enter an amount, "Record payment": a "Payment recorded" message appears and the Overview shows the payment.

The pages come from the jar itself, signed in or not: reloading on a page such as `/members` shows it again, and a file that does not exist (`/assets/missing.js`) answers 404. If the page loads but sign-in does nothing, see the cookie row in the table below.

## 13. Check that it starts by itself after a reboot [server]

```bash
sudo docker inspect -f '{{.HostConfig.RestartPolicy.Name}}' membertracker-mysql
systemctl is-enabled docker membertracker caddy
sudo reboot
```

You should see `unless-stopped` and `enabled` three times (Option C has no `caddy`). After a minute or two, log in again:

```bash
sudo docker ps --format '{{.Names}} {{.Status}}'
systemctl is-active membertracker caddy
```

You should see `membertracker-mysql Up ... (healthy)` and `active` for both. The app may start before MySQL is ready: its log then shows a `Communications link failure` once or twice, and systemd restarts it every 10 seconds until MySQL answers. That is expected.

## Updating

1. **[server]** Back up first, because a new version can change the database: `sudo /opt/membertracker/deploy/backup-mysql.sh`. Note the file name it prints.
2. **[computer]** Build and copy: `./gradlew bootJar -x test`, then `scp target/membertracker.jar admin@SERVER:membertracker-release/`. If `deploy/` changed, copy it again too (`scp -r deploy admin@SERVER:membertracker-release/`) and copy the changed file to its place.
3. **[server]** Keep the old jar, install the new one, restart, and read the log:

   ```bash
   sudo cp /opt/membertracker/membertracker.jar /opt/membertracker/membertracker.jar.previous
   sudo cp ~/membertracker-release/membertracker.jar /opt/membertracker/membertracker.jar
   sudo systemctl restart membertracker
   journalctl -u membertracker -n 30 --no-pager
   ```

   You should see `Started Application` and no `ERROR`. A release that needs a new variable stops with a message that names it: add it to `membertracker.env` (see `deploy/membertracker.env.example`) and restart.

**Rollback** (the new version misbehaves): restore the database from the dump of step 1 and put the old jar back. Everything entered after that dump is lost.

```bash
sudo -v   # asks for your password now, so the commands below do not stop to ask
sudo systemctl stop membertracker
sudo docker exec membertracker-mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -e "DROP DATABASE felege_selam; CREATE DATABASE felege_selam"'
sudo gzip -dc /srv/membertracker/backups/felege_selam-YYYYMMDD-HHMMSS.sql.gz | sudo docker exec -i membertracker-mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot felege_selam'
sudo cp /opt/membertracker/membertracker.jar.previous /opt/membertracker/membertracker.jar
sudo systemctl start membertracker
journalctl -u membertracker -n 15 --no-pager
```

Use the file name from step 1. You should see `Started Application`. The `membertracker` login keeps its rights, because dropping a database does not remove the grants.

## When something is wrong

| Symptom | Cause and fix |
|---------|---------------|
| The service does not start | Read the reason: `journalctl -u membertracker -n 60 --no-pager`. The rows below are the usual messages |
| `Access denied for user '${DB_USERNAME}'@...` | `DB_USERNAME` is missing or misspelled in `membertracker.env`: the app uses the text `${DB_USERNAME}` as the user name. Same for a missing `DB_PASSWORD` |
| `Access denied for user 'membertracker'@'172...'` (or `192.168...`) | Wrong `DB_PASSWORD`, or the login was created for `'localhost'` instead of `'%'` (step 5) |
| `auth.jwt-secret (JWT_SECRET) must be at least 32 characters` | `JWT_SECRET` is missing, still `CHANGE_ME`, or too short (a missing one shows `Value: "${JWT_SECRET}"`). Set it from `openssl rand -base64 48` |
| `BOOTSTRAP_ADMIN_PASSWORD is not set` or `... is not strong enough` | Set both `BOOTSTRAP_ADMIN_EMAIL` and a strong `BOOTSTRAP_ADMIN_PASSWORD` (rule in step 6), or remove both lines if the database already has users |
| `Communications link failure` ... `Connection refused` | MySQL is not up yet or not on this port. `cd /opt/membertracker && sudo docker compose ps`; the app retries by itself every 10 seconds |
| Caddy shows 502 Bad Gateway | The app is down or not on `127.0.0.1:8080`. `systemctl status membertracker`, then `curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8080/api/users/me` must print `401`. `SERVER_ADDRESS` must be unset or `127.0.0.1` when Caddy is in front |
| Option B: no certificate | DNS must point at your public IP and ports 80 and 443 must reach the server: `journalctl -u caddy -n 50 --no-pager` says what Let's Encrypt refused |
| Option A: browser warns about the certificate | The device does not trust Caddy's root certificate yet (step 9), or it opened the address, not the name, that is in the Caddyfile |
| Sign-in does nothing over http | The app sends Secure cookies, which browsers drop on plain http. Option C needs `COOKIE_SECURE=false` in `membertracker.env` and `sudo systemctl restart membertracker`. With Caddy and https keep it `true` |
| Dates are off, or the monthly reminders run at the wrong hour | The jobs (6:00 and 9:00 on the 1st) use the machine's time zone. `timedatectl` shows it and whether the clock is synchronised: `sudo timedatectl set-timezone <Zone>` (list: `timedatectl list-timezones`), `sudo timedatectl set-ntp true`, then restart the app. MySQL runs in UTC on purpose |
| Port already in use | `sudo ss -ltnp \| grep -E ':(80\|443\|3306\|8080)\b'` shows who has it. For MySQL set another `MYSQL_PORT` in `/opt/membertracker/.env` and `DB_URL` in `membertracker.env` (`jdbc:mysql://localhost:3307/felege_selam?serverTimezone=UTC`); for the app add `SERVER_PORT=8081` to `membertracker.env` and change the port in the Caddyfile |
| MySQL is not `healthy` | `sudo docker compose logs mysql` (in `/opt/membertracker`). The first start can take a minute. Usual causes: no disk space (`df -h`), a wrong `MYSQL_DATA_DIR`, or a new password in `.env` over an old data folder (the old password stays) |
