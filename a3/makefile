.SUFFIXES: .java .class
.java.class:
	javac -g $*.java

CLASSES = router.java pkt_LSPDU.java link_cost.java edge.java pkt_INIT.java circuit_DB.java

default: $(CLASSES:.java=.class)

clean:
	rm *.class
