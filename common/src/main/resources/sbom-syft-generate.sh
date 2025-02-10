#!/bin/bash
set -e
if [ "${IMAGE_APPEND_PLATFORM}" == "true" ]; then
  IMAGE="${IMAGE}-${PLATFORM//[^a-zA-Z0-9]/-}"
  export IMAGE
fi

case $SBOM_TYPE in
  cyclonedx)
    syft_sbom_type=cyclonedx-json@1.5 ;;
  spdx)
    syft_sbom_type=spdx-json@2.3 ;;
  *)
    echo "Invalid SBOM type: $SBOM_TYPE. Valid: cyclonedx, spdx" >&2
    exit 1
    ;;
esac

echo "Running syft on the source directory"
syft dir:"/var/workdir/$SOURCE_CODE_DIR/$CONTEXT" --output "$syft_sbom_type=/var/workdir/sbom-source.json"

echo "Running syft on the image filesystem"
syft scan oci-dir:/shared/konflux-final-image -o "$syft_sbom_type" > /var/workdir/sbom-image.json
