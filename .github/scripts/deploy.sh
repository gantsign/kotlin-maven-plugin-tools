#!/bin/bash

set -e
set +x

./mvnw deploy \
    -P publish-artifacts,sign-artifacts \
    -DskipTests \
    -Dinvoker.skip=true \
    --batch-mode \
    --show-version
