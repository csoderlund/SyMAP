JAVA_PATH=/Library/Java/JavaVirtualMachines/jdk1.7.0_67.jdk/Contents/Home
#JAVA_PATH=/usr/java/latest

JAVAC_FLAGS = -g #,vars #-Xlint:deprecation

# Note, other files are picked up through dependencies, but if a file is added
# that isn't a dependency of one of these, then it won't be built
JAVA_FILES=./src/symap/*.java  ./src/symapCE/*.java ./src/dotplot/*.java \
           ./src/symapCE/*.java  ./src/backend/*.java ./src/symapMultiAlign/*.java \
           ./src/symapQuery/*.java  ./src/symap3D/*.java ./src/blockview/*.java \
./src/symap/projectmanager/common/*.java ./src/circview/*.java

all: clean build jars clean_classes

build: 
	$(JAVA_PATH)/bin/javac $(JAVAC_FLAGS) -classpath ./classes_ext/both:./classes_ext/standalone:./classes_ext/applet:./classes_ext/3d:./classes_ext/freehep \
                         -sourcepath ./src -d ./classes $(JAVA_FILES) 

jars: standalone_jars

standalone_jars:
	rm -f jar/symap.jar jar/symap3D.jar
	$(JAVA_PATH)/bin/jar -cf jar/symap.jar -C ./classes . -C ./src html -C ./src properties -C ./src images -C ./classes_symap . 
	$(JAVA_PATH)/bin/jar -cf jar/symap3D.jar -C ./classes . -C ./src html -C ./src properties -C ./src images -C ./classes_symap3D . 

applet:
	$(JAVA_PATH)/bin/jar -cfm jar/symapApplet.jar manifest.add -C ./classes . -C ./src html -C ./src properties -C ./src images -C ./classes_symapApplet . 
	$(JAVA_PATH)/bin/jar -cfm jar/symapApplet3D.jar manifest.add -C ./classes . -C ./src html -C ./src properties -C ./src images -C ./classes_symap3DApplet . 
	

class_dirs:
	rm -rf classes_symap/* classes_symap3D/* 
	rm -rf classes_symapApplet/* classes_symap3DApplet/*
	mkdir classes
	mkdir jar
	# standalone, no 3D
	mkdir -p classes_symap
	cp -R classes_ext/both/* classes_symap
	cp -R classes_ext/standalone/* classes_symap
	cp -R classes_ext/freehep/* classes_symap
	# 3D : contains all of standalone as well
	mkdir -p classes_symap3D
	cp -R classes_symap/* classes_symap3D
	cp -R classes_ext/3d/* classes_symap3D
	# applet
	mkdir -p classes_symapApplet
	cp -R classes_ext/both/* classes_symapApplet
	cp -R classes_ext/freehep/* classes_symapApplet
	cp -R classes_ext/applet/* classes_symapApplet
	
	
clean_classes:
	rm -fr classes/* 
clean:
	rm -fr classes/*  jar/symap*.jar 
