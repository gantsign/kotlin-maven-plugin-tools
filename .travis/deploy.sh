#!/bin/bash

set -e
set +x

# See http://www.debonair.io/post/maven-cd/ for instructions
openssl aes-256-cbc \
    -K "$encrypted_e84eefe7574d_key" \
    -iv "$encrypted_e84eefe7574d_iv" \
    -in .travis/codesigning.asc.enc \
    -out .travis/codesigning.asc -d
gpg --fast-import .travis/codesigning.asc

./mvnw deploy \
    --settings .travis/settings.xml \
    -P publish-artifacts \
    -DskipTests \
    -Dinvoker.skip=true \
    --batch-mode \
    --show-version
