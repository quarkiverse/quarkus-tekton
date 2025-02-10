#!/usr/bin/env bash
set -eu
set -o pipefail

echo "Step 1 :: Configure SSH and rsync folders from tekton to the VM"
mkdir -p ~/.ssh
if [ -e "/ssh/error" ]; then
  #no server could be provisioned
  cat /ssh/error
  exit 1
elif [ -e "/ssh/otp" ]; then
  curl --cacert /ssh/otp-ca -XPOST -d @/ssh/otp "$(cat /ssh/otp-server)" >~/.ssh/id_rsa
  echo "" >> ~/.ssh/id_rsa
else
  cp /ssh/id_rsa ~/.ssh
fi
chmod 0400 ~/.ssh/id_rsa

SSH_HOST=$(cat /ssh/host)
BUILD_DIR=$(cat /ssh/user-dir)
export SSH_HOST
export BUILD_DIR
export SSH_ARGS=(-o StrictHostKeyChecking=no -o ServerAliveInterval=60 -o ServerAliveCountMax=10)

echo "Export different variables which are used within the script like args, etc"
BUILD_ARGS=()
while [[ $# -gt 0 ]]; do BUILD_ARGS+=("$1"); shift; done
export BUILD_ARGS
echo "Build args: $BUILD_ARGS"

ssh "${SSH_ARGS[@]}" "$SSH_HOST" mkdir -p "$BUILD_DIR/workspaces" "$BUILD_DIR/scripts" "$BUILD_DIR/volumes"

export PORT_FORWARD=""
export PODMAN_PORT_FORWARD=""

echo "Rsync folders from pod to VM ..."
rsync -ra "/var/workdir/"            "$SSH_HOST:$BUILD_DIR/volumes/workdir/"
rsync -ra "/shared/"                 "$SSH_HOST:$BUILD_DIR/volumes/shared/"
rsync -ra "/mnt/trusted-ca/"         "$SSH_HOST:$BUILD_DIR/volumes/trusted-ca/"
rsync -ra "/tekton/results/"         "$SSH_HOST:$BUILD_DIR/results/"

echo "Step 2 :: Create the bash script to be executed within the VM"
mkdir -p scripts

cat >scripts/script-setup.sh <<'REMOTESSHEOF'
#!/bin/sh

echo "Start podman.socket and show podman info"
systemctl --user start podman.socket
sleep 10s

echo "Podman version"
podman version

echo "Podman info"
podman info
REMOTESSHEOF
chmod +x scripts/script-setup.sh

cat >scripts/script-build.sh <<'REMOTESSHEOF'
#!/bin/sh

cd /var/workdir

echo "Build the builder image using pack"
for build_arg in "${BUILD_ARGS[@]}"; do
  PACK_ARGS+=" $build_arg"
done

echo "Pack extra args: $PACK_ARGS"

echo "Execute: pack builder create ..."
export DOCKER_HOST=unix:///workdir/podman.sock
pack config experimental true

UNSHARE_ARGS=()
PACK_BUILDER_FILE="source/builder.toml"

BUILDPACKS_IMAGES=$(tomljson ${PACK_BUILDER_FILE} | jq -r '.buildpacks[].uri?, .extensions[].uri?')

BASE_IMAGE=$(tomljson ${PACK_BUILDER_FILE} | jq -r '.stack."build-image"')
podman inspect ${BASE_IMAGE} | jq -r '.[].Digest' > /shared/BASE_IMAGES_DIGESTS

echo "Create locally a Dockerfile using the build image defined part of the builder.toml file to include the BASE IMAGE"
dockerfile_path=$(mktemp --suffix=-Dockerfile)
cat <<EOF > $dockerfile_path
FROM $BASE_IMAGE
EOF

dockerfile-json "$dockerfile_path" >/shared/parsed_dockerfile.json

BASE_IMAGES=$(
jq -r '.Stages[] | select(.From | .Stage or .Scratch | not) | .BaseName | select(test("^oci-archive:") | not)' /shared/parsed_dockerfile.json
)

if [ "${HERMETIC}" == "true" ]; then
  UNSHARE_ARGS+=("--net")

  for image in ${BASE_IMAGES}; do
    echo "Pull the base image: $image using unshare"
    unshare -Ufp --keep-caps -r --map-users 1,1,65536 --map-groups 1,1,65536 -- buildah pull $image
  done

  for image in ${BUILDPACKS_IMAGES}; do
    echo "Pull the buildpacks image: $image using unshare"
    unshare -Ufp --keep-caps -r --map-users 1,1,65536 --map-groups 1,1,65536 -- buildah pull $image
  done

  echo "Fetch lifecycle and patch the ${PACK_BUILDER_FILE} file ..."
  LIFECYCLE_VERSION=$(tomljson ${PACK_BUILDER_FILE} | jq -r '.lifecycle.version')
  unshare -Ufp --keep-caps -r --map-users 1,1,65536 --map-groups 1,1,65536 -- \
    curl -skL https://github.com/buildpacks/lifecycle/releases/download/v${LIFECYCLE_VERSION}/lifecycle-v${LIFECYCLE_VERSION}+linux.x86-64.tgz --output source/lifecycle-v${LIFECYCLE_VERSION}+linux.x86-64.tgz

  echo "Patch the builder toml file to use the local PATH of the lifecycle tar.gz file"
  tomljson ${PACK_BUILDER_FILE} > source/builder.json
  jq 'del(.lifecycle.version) | .lifecycle.uri = "lifecycle-v'"${LIFECYCLE_VERSION}"'+linux.x86-64.tgz"' source/builder.json > source/new-builder.json

  PACK_BUILDER_FILE="source/new-builder.toml"
  python -c "import json, tomli_w, sys; tomli_w.dump(json.load(open('source/new-builder.json')), open('${PACK_BUILDER_FILE}', 'wb'))"
fi

echo "pack builder create ${IMAGE} --config ${PACK_BUILDER_FILE} ${PACK_ARGS}"
unshare -Uf "${UNSHARE_ARGS[@]}" --keep-caps -r --map-users 1,1,65536 --map-groups 1,1,65536 -- \
  pack builder create ${IMAGE} --config ${PACK_BUILDER_FILE} ${PACK_ARGS}

REMOTESSHEOF
chmod +x scripts/script-build.sh

cat >scripts/script-post-build.sh <<'REMOTESSHEOF'
#!/bin/sh

echo "Push the image produced and generate its digest: $IMAGE"
podman push \
  --digestfile $BUILD_DIR/volumes/shared/IMAGE_DIGEST \
  "$IMAGE"

echo "Export the image as OCI"
podman push "${IMAGE}" "oci:$BUILD_DIR/volumes/shared/konflux-final-image:$IMAGE"

echo "Export: IMAGE_URL"
echo -n "$IMAGE" > $BUILD_DIR/volumes/shared/IMAGE_URL
REMOTESSHEOF
chmod +x scripts/script-post-build.sh

echo "Step 3 :: Execute the bash script on the VM"

rsync -ra scripts "$SSH_HOST:$BUILD_DIR"
rsync -ra "$HOME/.docker/" "$SSH_HOST:$BUILD_DIR/.docker/"

echo "Setup VM environment: podman, etc within the VM ..."
ssh "${SSH_ARGS[@]}" "$SSH_HOST" scripts/script-setup.sh

        # Remark: Adding security-opt to by pass: dial unix /workdir/podman.sock: connect: permission denied
        ssh "${SSH_ARGS[@]}" "$SSH_HOST" "$PORT_FORWARD" podman run "$PODMAN_PORT_FORWARD" \
          -e BUILDER_IMAGE="$BUILDER_IMAGE" \
          -e HERMETIC="$HERMETIC" \
          -e PLATFORM="$PLATFORM" \
          -e STORAGE_DRIVER="$STORAGE_DRIVER" \
          -e IMAGE="$IMAGE" \
          -e BUILD_ARGS="${BUILD_ARGS[*]}" \
          -e BUILD_DIR="$BUILD_DIR" \
          -v "$BUILD_DIR/volumes/workdir:/var/workdir:Z" \
          -v "$BUILD_DIR/volumes/shared:/shared:Z" \
          -v "$BUILD_DIR/.docker:/root/.docker:Z" \
          -v "$BUILD_DIR/volumes/trusted-ca:/mnt/trusted-ca:Z" \
          -v "$BUILD_DIR/scripts:/scripts:Z" \
          -v "/run/user/1001/podman/podman.sock:/workdir/podman.sock:Z" \
          --user=0 \
          --security-opt label=disable \
          --rm "$BUILDER_IMAGE" /scripts/script-build.sh "$@"

        echo "Execute post build steps within the VM ..."
        ssh "${SSH_ARGS[@]}" "$SSH_HOST" \
          BUILD_DIR="$BUILD_DIR" \
          IMAGE="$IMAGE" \
          scripts/script-post-build.sh

        echo "Rsync folders from VM to pod"
        rsync -ra "$SSH_HOST:$BUILD_DIR/volumes/workdir/" /var/workdir/
        rsync -ra "$SSH_HOST:$BUILD_DIR/volumes/shared/"  "/shared/"
        rsync -ra "$SSH_HOST:$BUILD_DIR/results/"         "/tekton/results/"
