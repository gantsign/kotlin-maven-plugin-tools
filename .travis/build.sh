#!/bin/bash

set -e

./mvnw install --batch-mode --show-version --settings .travis/settings.xml

if [ "$TRAVIS_PULL_REQUEST" == "false" ]; then

    # See http://www.debonair.io/post/maven-cd/ for instructions
    openssl aes-256-cbc -K $encrypted_e84eefe7574d_key -iv $encrypted_e84eefe7574d_iv \
      -in .travis/codesigning.asc.enc -out .travis/codesigning.asc -d
    gpg --fast-import .travis/codesigning.asc

    if [ "$TRAVIS_TAG" != "" ]; then
        ./mvnw deploy -P publish-artifacts --batch-mode --show-version \
            --settings .travis/settings.xml
    fi
fi
