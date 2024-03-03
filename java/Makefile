#JAVA_PATH=/Library/Java/JavaVirtualMachines/adoptopenjdk-8.jdk/Contents/Home
#JAVA_PATH=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home
JAVA_PATH=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home
#JAVA_PATH=/Library/Java/JavaVirtualMachines/jdk-20.jdk/Contents/Home
#JAVA_PATH=/usr

JAVAC_FLAGS = -g -Xlint:deprecation #,vars #-Xlint:deprecation 

JAVA_FILES= ./src/symapCE/*.java ./src/database/*.java ./src/backend/*.java \
			./src/symap/*.java ./src/dotplot/*.java ./src/blockview/*.java \
			./src/circview/*.java  ./src/symapQuery/*.java

all: clean build standalone_jars 

build: 
	$(JAVA_PATH)/bin/javac $(JAVAC_FLAGS) -sourcepath ./src -d ./classes $(JAVA_FILES) 

standalone_jars:
	$(JAVA_PATH)/bin/jar -cf jar/symap.jar -C ./classes . -C ./src html -C ./src properties -C ./src images -C ./classes_ext . 
 
clean:
	@if [ ! -d "./classes" ];then mkdir classes;fi
	@if [ ! -d "./jar" ];then mkdir jar;fi
	@rm -fr classes/*  jar/symap*.jar 
	
