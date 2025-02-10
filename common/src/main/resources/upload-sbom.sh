#!/bin/bash
set -e
if [ "${IMAGE_APPEND_PLATFORM}" == "true" ]; then
  IMAGE="${IMAGE}-${PLATFORM//[^a-zA-Z0-9]/-}"
  export IMAGE
fi
ca_bundle=/mnt/trusted-ca/ca-bundle.crt
if [ -f "$ca_bundle" ]; then
  echo "INFO: Using mounted CA bundle: $ca_bundle"
  cp -vf $ca_bundle /etc/pki/ca-trust/source/anchors
  update-ca-trust
fi

cosign attach sbom --sbom sbom.json --type "$SBOM_TYPE" "$(cat "$(results.IMAGE_REF.path)")"
