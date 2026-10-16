# Intack relay

Our own Nostr relay at `wss://relay.intack.app`, first in the app's relay list (RelayPool.kt). The
free public relays stay in the list, so the app keeps working if this one is down.

- **What it keeps:** only Intack's event kinds (4333 stored edits, 24333 live notes; see Nostr.kt).
  Everything else is refused. Contents are encrypted on the phones with each space's key, so the relay
  sees only scrambled data, a random author key per space, the time, and the sender's IP address.
  Stored events carry a NIP-40 expiration (14 days) and the relay drops them after that.
- **Where:** AWS Lightsail in account `intack`, Sydney. Instance `intack-relay`: Ubuntu 24.04, $5
  plan (512 MB plus 1 GB swap), static IP `intack-relay-ip` (32.237.31.167). Route 53 in Adam's main
  account has an A record for `relay.intack.app` pointing there.
- **Software:** [nostr-rs-relay](https://git.sr.ht/~gheartsfield/nostr-rs-relay) 0.10.0 (SQLite) behind
  Caddy, which gets and renews the TLS certificate. Both run in Docker from `/opt/relay`.

## Deploy or change settings

```
bash server/relay/deploy.sh
```

First run: creates the instance (running `setup.sh` on first boot: swap, Docker), the static IP and
the open ports (22, 80, 443), and saves the SSH key to `~/secrets/intack-lightsail.pem`. Every run copies
`config.toml`, `docker-compose.yml` and `Caddyfile` to the server and restarts the containers. Needs
`aws sso login --profile intack`.

## Looking at it

```
ssh -i ~/secrets/intack-lightsail.pem ubuntu@32.237.31.167
cd /opt/relay && sudo docker compose logs --tail 50
sudo docker exec relay-relay-1 sqlite3 /usr/src/app/db/nostr.db "select kind, count(*) from event group by kind;"
```
