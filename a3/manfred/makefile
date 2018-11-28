JFLAGS = -g
JC = javac
.SUFFIXES: .java .class
.java.class:
	$(JC) $(JFLAGS) $*.java

CLASSES = \
        router.java \
        pkt_HELLO.java \
        pkt_LSPDU.java \
        link_cost.java \
        edge.java \
        pkt_INIT.java \
        circuit_DB.java \

default: classes

classes: $(CLASSES:.java=.class)

clean:
	$(RM) *.class
