publish-local:
	./gradlew publishToMavenLocal

# Publish to CodeArtifact. Requires:
#   CODEARTIFACT_URL=https://<domain>-<owner>.d.codeartifact.<region>.amazonaws.com/maven/<repo>/
#   CODEARTIFACT_AUTH_TOKEN=$(aws codeartifact get-authorization-token \
#     --domain <domain> --domain-owner <owner> --query authorizationToken --output text)
publish:
	./gradlew publish

test:
	./gradlew test
