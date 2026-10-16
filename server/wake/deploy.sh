#!/usr/bin/env bash
# Deploys the wake server to AWS Lambda as `intack-wake`, with a public Function URL.
# Creates the function, its role and URL the first time; afterwards updates code and settings.
#
#   bash server/wake/deploy.sh            # key ~/secrets/intack-fcm.json, AWS profile "intack"
#   KEY=path/to/key.json PROFILE=other bash server/wake/deploy.sh
#
# Needs the AWS CLI signed in (`aws sso login --profile intack`). Prints the Function URL, which goes
# in the app build as the gradle property intack.wakeUrl.
set -euo pipefail
KEY=${KEY:-$HOME/secrets/intack-fcm.json}
PROFILE=${PROFILE:-intack}
NAME=${NAME:-intack-wake}
ROLE=$NAME-role
export AWS_PAGER="" PATH="$PATH:/c/Program Files/Amazon/AWSCLIV2"
here=$(cd "$(dirname "$0")" && pwd)
[ -f "$KEY" ] || { echo "Firebase service account key not found at $KEY" >&2; exit 1; }
aws() { command aws.exe "$@" --profile "$PROFILE"; }
# A path the Windows aws.exe understands.
win() { cygpath -w "$1" 2>/dev/null || echo "$1"; }

tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
# The key goes into the function's environment through a file, never on a command line.
node -e 'const fs=require("fs");fs.writeFileSync(process.argv[2],JSON.stringify({Variables:{FCM_SERVICE_ACCOUNT:fs.readFileSync(process.argv[1],"utf8")}}))' "$(win "$KEY")" "$(win "$tmp/env.json")"
(cd "$here" && /c/Windows/System32/tar.exe -a -cf "$(win "$tmp/wake.zip")" index.mjs)

# Role: only lets the function write its own logs.
if ! role_arn=$(aws iam get-role --role-name "$ROLE" --query Role.Arn --output text 2>/dev/null); then
  echo '{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":{"Service":"lambda.amazonaws.com"},"Action":"sts:AssumeRole"}]}' > "$tmp/trust.json"
  role_arn=$(aws iam create-role --role-name "$ROLE" --assume-role-policy-document "file://$(win "$tmp/trust.json")" --query Role.Arn --output text)
  aws iam attach-role-policy --role-name "$ROLE" --policy-arn arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole
  echo "Created role $ROLE; waiting for it to be usable..."
  sleep 10
fi

if aws lambda get-function --function-name "$NAME" >/dev/null 2>&1; then
  aws lambda update-function-code --function-name "$NAME" --zip-file "fileb://$(win "$tmp/wake.zip")" >/dev/null
  aws lambda wait function-updated-v2 --function-name "$NAME"
  aws lambda update-function-configuration --function-name "$NAME" --environment "file://$(win "$tmp/env.json")" --timeout 10 --memory-size 128 >/dev/null
  aws lambda wait function-updated-v2 --function-name "$NAME"
  echo "Updated $NAME."
else
  aws lambda create-function --function-name "$NAME" --runtime nodejs22.x --handler index.handler --role "$role_arn" \
    --zip-file "fileb://$(win "$tmp/wake.zip")" --timeout 10 --memory-size 128 --environment "file://$(win "$tmp/env.json")" >/dev/null
  aws lambda wait function-active-v2 --function-name "$NAME"
  echo "Created $NAME."
fi

# Public URL: anyone may call it, but it only accepts a topic and only sends content-free pushes.
if ! url=$(aws lambda get-function-url-config --function-name "$NAME" --query FunctionUrl --output text 2>/dev/null); then
  url=$(aws lambda create-function-url-config --function-name "$NAME" --auth-type NONE --query FunctionUrl --output text)
  aws lambda add-permission --function-name "$NAME" --statement-id public-url --action lambda:InvokeFunctionUrl \
    --principal '*' --function-url-auth-type NONE >/dev/null
  aws lambda add-permission --function-name "$NAME" --statement-id public-url-invoke --action lambda:InvokeFunction \
    --principal '*' --invoked-via-function-url >/dev/null
fi
echo "Function URL: $url"
