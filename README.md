# XdpServices
ODP indexing and search services supporting the XD for WebProtégé extension (https://github.com/hammar/webprotege).

## Requirements

* SemanticVectors 5.9 or greater in local Maven repository (at the time of writing has not been released on maven central, see https://github.com/semanticvectors/semanticvectors)
* Gradle

## Install instructions

* Install SemanticVectors 5.9 or greater (`git clone` followed by `mvn install`)
* Build to jar: `gradle jar`
* Build redistributable/runnable jar: `gradle bootRepackage`
