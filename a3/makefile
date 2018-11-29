.SUFFIXES: .java .class
.java.class:
	javac -g $*.java

CLASSES = linkData.java finishedEdge.java LSPDU.java router.java 

default: $(CLASSES:.java=.class)

clean:
	rm *.class
