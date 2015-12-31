.PHONY: all clean javadoc

GRADLE = ./gradlew

all:
	$(GRADLE) classes

clean:
	$(GRADLE) clean

doc:
	$(GRADLE) javadoc
