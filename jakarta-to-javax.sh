#!/usr/bin/env bash

## adjust pom dependencies

sed -i 's/<version>\(.*\)-SNAPSHOT<\/version>/<version>\1-javax-SNAPSHOT<\/version>/' pom.xml
sed -i 's/<version>\(.*\)-SNAPSHOT<\/version>/<version>\1-javax-SNAPSHOT<\/version>/' inject/pom.xml
sed -i 's/<version>\(.*\)-SNAPSHOT<\/version>/<version>\1-javax-SNAPSHOT<\/version>/' inject-generator/pom.xml
sed -i 's/<version>\(.*\)-SNAPSHOT<\/version>/<version>\1-javax-SNAPSHOT<\/version>/' inject-test/pom.xml
sed -i 's/<version>\(.*\)-SNAPSHOT<\/version>/<version>\1-javax-SNAPSHOT<\/version>/' blackbox-aspect/pom.xml
sed -i 's/<version>\(.*\)-SNAPSHOT<\/version>/<version>\1-javax-SNAPSHOT<\/version>/' blackbox-other/pom.xml
sed -i 's/<version>\(.*\)-SNAPSHOT<\/version>/<version>\1-javax-SNAPSHOT<\/version>/' blackbox-test-inject/pom.xml

sed -i'' -e 's|<version>2\.0\.1</version> <!-- jakarta -->|<version>1\.0\.5</version> <!-- javax -->|g' inject/pom.xml

## adjust module-info
sed -i'' -e 's| jakarta\.inject| java\.inject|g' inject/src/main/java/module-info.java

## adjust code
#find . -type f -name '*.java' -exec sed -i'' -e 's| jakarta\.inject\.| javax\.inject\.|g' {} +
find . -type f -not -name 'IncludeAnnotations.java' -name '*.java' -exec sed -i'' -e 's|jakarta\.inject\.|javax\.inject\.|g' {} +
