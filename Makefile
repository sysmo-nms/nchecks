run:
	./gradlew installDist
	./build/install/nchecks/bin/nchecks --no-snmp CheckHTTP uri=http://www.google.fr
