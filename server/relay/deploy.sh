#!/usr/bin/env bash
# Creates (first time) or updates Intack's relay server on AWS Lightsail: instance `intack-relay`
# (Ubuntu, $5 plan, Sydney) with a static IP, ports 80/443 open, running the relay and Caddy from
# this folder in Docker. Re-run after changing config.toml, docker-compose.yml or Caddyfile.
#
#   bash server/relay/deploy.sh        # AWS profile "intack"; SSH key saved to ~/secrets/intack-lightsail.pem
set -euo pipefail
PROFILE=${PROFILE:-intack}
NAME=intack-relay
IP_NAME=$NAME-ip
ZONE=ap-southeast-2a
KEY=$HOME/secrets/intack-lightsail.pem
export AWS_PAGER="" PATH="$PATH:/c/Program Files/Amazon/AWSCLIV2"
here=$(cd "$(dirname "$0")" && pwd)
aws() { command aws.exe "$@" --profile "$PROFILE"; }
win() { cygpath -w "$1" 2>/dev/null || echo "$1"; }

if ! aws lightsail get-instance --instance-name "$NAME" >/dev/null 2>&1; then
  aws lightsail create-instances --instance-names "$NAME" --availability-zone "$ZONE" \
    --blueprint-id ubuntu_24_04 --bundle-id nano_3_2 --user-data "file://$(win "$here/setup.sh")" >/dev/null
  echo "Creating $NAME..."
  until [ "$(aws lightsail get-instance-state --instance-name "$NAME" --query state.name --output text)" = running ]; do sleep 5; done
fi

if ! aws lightsail get-static-ip --static-ip-name "$IP_NAME" >/dev/null 2>&1; then
  aws lightsail allocate-static-ip --static-ip-name "$IP_NAME" >/dev/null
fi
if [ "$(aws lightsail get-static-ip --static-ip-name "$IP_NAME" --query staticIp.isAttached --output text)" != True ]; then
  aws lightsail attach-static-ip --static-ip-name "$IP_NAME" --instance-name "$NAME" >/dev/null
fi
ip=$(aws lightsail get-static-ip --static-ip-name "$IP_NAME" --query staticIp.ipAddress --output text)

# Web ports for everyone; SSH stays open for this script (key only, no passwords).
aws lightsail put-instance-public-ports --instance-name "$NAME" --port-infos \
  fromPort=22,toPort=22,protocol=tcp fromPort=80,toPort=80,protocol=tcp fromPort=443,toPort=443,protocol=tcp >/dev/null

if [ ! -f "$KEY" ]; then
  mkdir -p "$(dirname "$KEY")"
  aws lightsail download-default-key-pair --query privateKeyBase64 --output text > "$KEY"
  chmod 600 "$KEY"
fi
ssh_() { ssh -i "$KEY" -o StrictHostKeyChecking=accept-new -o ConnectTimeout=10 "ubuntu@$ip" "$@"; }

echo "Waiting for $ip to finish its first-boot setup..."
until ssh_ test -f /opt/relay/.ready 2>/dev/null; do sleep 10; done
scp -i "$KEY" -q "$here/config.toml" "$here/docker-compose.yml" "$here/Caddyfile" "ubuntu@$ip:/tmp/"
ssh_ 'sudo mv /tmp/config.toml /tmp/docker-compose.yml /tmp/Caddyfile /opt/relay/ && cd /opt/relay && sudo docker compose pull -q && sudo docker compose up -d --remove-orphans && sudo docker compose restart relay caddy'
echo "Relay server at $ip: point relay.intack.app's DNS (A record) here."
