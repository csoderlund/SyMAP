JAVA_PATH=/Library/Java/JavaVirtualMachines/adoptopenjdk-8.jdk/Contents/Home
#JAVA_PATH=/usr

JAVAC_FLAGS = -g #,vars #-Xlint:deprecation

JAVA_FILES=./src/symap/*.java  ./src/symapCE/*.java ./src/dotplot/*.java \
           ./src/symapCE/*.java  ./src/backend/*.java ./src/symapMultiAlign/*.java \
           ./src/symapQuery/*.java  ./src/symap3D/*.java ./src/blockview/*.java \
./src/symap/projectmanager/common/*.java ./src/circview/*.java

all: clean build standalone_jars 

build: 
	$(JAVA_PATH)/bin/javac $(JAVAC_FLAGS) -classpath ./classes_ext/both:./classes_ext/standalone:./classes_ext/3d:./classes_ext/freehep \
                         -sourcepath ./src -d ./classes $(JAVA_FILES) 

standalone_jars:
	$(JAVA_PATH)/bin/jar -cf jar/symap.jar -C ./classes . -C ./src html -C ./src properties -C ./src images -C ./classes_symap . 
	#$(JAVA_PATH)/bin/jar -cf jar/symap3D.jar -C ./classes . -C ./src html -C ./src properties -C ./src images -C ./classes_symap3D . 
	 
class_dirs:
	rm -rf classes_symap/* classes_symap3D/* 
	mkdir -p classes
	mkdir -p jar
	# standalone, no 3D
	mkdir -p classes_symap
	cp -R classes_ext/both/* classes_symap
	cp -R classes_ext/standalone/* classes_symap
	cp -R classes_ext/freehep/* classes_symap
# 3D : contains all of standalone as well
	#mkdir -p classes_symap3D
	#cp -R classes_symap/* classes_symap3D
	#cp -R classes_ext/3d/* classes_symap3D
		
clean_classes:
	rm -fr classes/* 
clean:
	rm -fr classes/*  jar/symap*.jar 
