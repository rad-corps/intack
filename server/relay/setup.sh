#!/bin/bash
# First-boot script for the relay server (Lightsail launch script): swap, Docker, then the relay.
# The relay's files (config.toml, docker-compose.yml, Caddyfile) are copied to /opt/relay by deploy.sh.
set -eux
if [ ! -f /swapfile ]; then
  fallocate -l 1G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile
  echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi
export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y docker.io docker-compose-v2 unattended-upgrades
systemctl enable --now docker
mkdir -p /opt/relay
touch /opt/relay/.ready
