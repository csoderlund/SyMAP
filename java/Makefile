#JAVA_PATH=/Library/Java/JavaVirtualMachines/jdk-14.0.1.jdk/Contents/Home
#JAVA_PATH=/Library/Java/JavaVirtualMachines/adoptopenjdk-15.jdk/Contents/Home
#JAVA_PATH=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home
JAVA_PATH=/Library/Java/JavaVirtualMachines/adoptopenjdk-8.jdk/Contents/Home
#JAVA_PATH=/usr

JAVAC_FLAGS = -g -Xlint:deprecation #,vars #-Xlint:deprecation

JAVA_FILES=./src/symap/*.java  ./src/symapCE/*.java ./src/dotplot/*.java \
           	./src/symapCE/*.java  ./src/backend/*.java ./src/symapMultiAlign/*.java \
			./src/symapQuery/*.java   ./src/blockview/*.java \
			./src/symap/projectmanager/common/*.java ./src/circview/*.java

all: clean build standalone_jars 

build: 
	$(JAVA_PATH)/bin/javac $(JAVAC_FLAGS) -classpath ./classes_ext -sourcepath ./src -d ./classes $(JAVA_FILES) 

standalone_jars:
	$(JAVA_PATH)/bin/jar -cf jar/symap.jar -C ./classes . -C ./src html -C ./src properties -C ./src images -C ./classes_ext . 
 
clean:
	@if [ ! -d "./classes" ];then mkdir classes;fi
	@if [ ! -d "./jar" ];then mkdir jar;fi
	@rm -fr classes/*  jar/symap*.jar 
	
