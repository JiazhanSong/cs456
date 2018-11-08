Run emulator, receiver, then sender in that order

Usage:

# RUNNING RECEIVER
python3 receiver.py <hostname for the network emulator>, <UDP port number used by
the link emulator to receive ACKs from the receiver>, <UDP port number used by the 
receiver to receive data from the emulator>, and <name of the file into which the
received data is written>

# RUNNING SENDER
python3 sender.py <host address of the network emulator>, <UDP port number used by
the emulator to receive data from the sender>, <UDP port number used by the sender to
receive ACKs from the emulator>, and <name of the file to be transferred>



Tested on school computers, jzsong@ubuntu1604-006 jzsong@ubuntu1604-004 jzsong@ubuntu1604-002
Using python3

#NOTE, code does not work with unreliable connection likely because of a numerical error of some sort
#      but works with stable connection