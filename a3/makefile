.SUFFIXES: .java .class
.java.class:
	javac -g $*.java

CLASSES = router.java LSPDU.java linkData.java finishedEdge.java

default: $(CLASSES:.java=.class)

clean:
	rm *.class
