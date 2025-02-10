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

echo "Pull the image from the OCI storage."
buildah --storage-driver "$STORAGE_DRIVER" pull "$IMAGE"

echo "Copy within the container of the image the sbom files"

container=$(buildah --storage-driver "$STORAGE_DRIVER" from --pull-never "$IMAGE")
buildah --storage-driver "$STORAGE_DRIVER" copy "$container" sbom.json /root/buildinfo/content_manifests/

BUILDAH_ARGS=()
if [ "${SQUASH}" == "true" ]; then
  BUILDAH_ARGS+=("--squash")
fi

buildah --storage-driver "$STORAGE_DRIVER" commit "${BUILDAH_ARGS[@]}" "$container" "$IMAGE"

echo "Pushing to ${IMAGE%:*}:${TASKRUN_NAME}"

retries=5
if ! buildah push \
  --retry "$retries" \
  --storage-driver "$STORAGE_DRIVER" \
  --tls-verify="$TLSVERIFY" \
  "$IMAGE" \
  "docker://${IMAGE%:*}:$(context.taskRun.name)"; then
  echo "Failed to push sbom image to ${IMAGE%:*}:$(context.taskRun.name) after ${retries} tries"
  exit 1
fi


echo "Pushing to ${IMAGE}"

if ! buildah push \
  --retry "$retries" \
  --storage-driver "$STORAGE_DRIVER" \
  --tls-verify="$TLSVERIFY" \
  --digestfile "/var/workdir/image-digest" "$IMAGE" \
  "docker://$IMAGE"; then
  echo "Failed to push sbom image to $IMAGE after ${retries} tries"
  exit 1
fi


echo "Save the different results"

tee "$(results.IMAGE_DIGEST.path)" < "/var/workdir/image-digest"
echo -n "$IMAGE" | tee "$(results.IMAGE_URL.path)"
{
  echo -n "${IMAGE}@"
  cat "/var/workdir/image-digest"
} >"$(results.IMAGE_REF.path)"

        # Remove tag from IMAGE while allowing registry to contain a port number.
        sbom_repo="${IMAGE%:*}"
        sbom_digest="$(sha256sum sbom.json | cut -d' ' -f1)"
        # The SBOM_BLOB_URL is created by `cosign attach sbom`.
        echo -n "${sbom_repo}@sha256:${sbom_digest}" | tee "$(results.SBOM_BLOB_URL.path)"

        tee "$(results.BASE_IMAGES_DIGESTS.path)" < /shared/BASE_IMAGES_DIGESTS
