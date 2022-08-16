#!/bin/bash

set -e
set +x

./mvnw deploy \
    -P publish-artifacts,sign-artifacts \
    "-Drevision=${GIT_TAG:-development-SNAPSHOT}" \
    -DskipTests \
    -Dinvoker.skip=true \
    --batch-mode \
    --show-version
