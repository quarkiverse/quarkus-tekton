#!/bin/bash
set -e
if [ "${IMAGE_APPEND_PLATFORM}" == "true" ]; then
  IMAGE="${IMAGE}-${PLATFORM//[^a-zA-Z0-9]/-}"
  export IMAGE
fi

sboms_to_merge=(syft:sbom-source.json syft:sbom-image.json)

if [ -f "sbom-cachi2.json" ]; then
  sboms_to_merge+=(cachi2:sbom-cachi2.json)
fi

echo "Merging sboms: (${sboms_to_merge[*]}) => sbom.json"
python3 /scripts/merge_sboms.py "${sboms_to_merge[@]}" > sbom.json

echo "Adding base images data to sbom.json"
python3 /scripts/base_images_sbom_script.py \
  --sbom=sbom.json \
  --parsed-dockerfile=/shared/parsed_dockerfile.json \
  --base-images-digests=/shared/BASE_IMAGES_DIGESTS
