publish-local:
	./gradlew publishToMavenLocal

# Publish to CodeArtifact. Requires:
#   ARTIFACTORY_URL=https://<domain>-<owner>.d.codeartifact.<region>.amazonaws.com/maven/<repo>/
#   ARTIFACTORY_PASSWORD=$(aws codeartifact get-authorization-token \
#     --domain <domain> --domain-owner <owner> --query authorizationToken --output text)
publish:
	./gradlew publish

test:
	./gradlew test
