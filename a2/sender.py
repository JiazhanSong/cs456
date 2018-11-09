from socket import *
from packet import packet
import select
import threading
import time
import sys

def ack():
  global N
  global senderSocket
  global base
  global nextseqnum
  global timer
  global ackfile

  while True:
    UDPdata, clientAddress = senderSocket.recvfrom( 2048 )
    p = packet.parse_udp_data(UDPdata)
    ackfile.write(str(p.seq_num) + "\n")

    lastBase = base
    baseModulo = base % packet.SEQ_NUM_MODULO
    currSeqnum = p.seq_num
    windowEnd = (baseModulo + N - 1) % packet.SEQ_NUM_MODULO

    if p.type == 2:
      senderSocket.close()
      break

    # check if within valid range
    if currSeqnum >= baseModulo and currSeqnum <= windowEnd:
      base = base + currSeqnum - baseModulo + 1
    elif baseModulo > windowEnd and (currSeqnum <= windowEnd or currSeqnum >= baseModulo):
      if baseModulo <= currSeqnum:
        base = base + currSeqnum + 1 - baseModulo
      else:
        base = base + currSeqnum + 1 + (packet.SEQ_NUM_MODULO - baseModulo)
    
    if base > lastBase:
      if base != nextseqnum:
        timer = time.time()
      else:
        timer = None


# command line
hostAddress = sys.argv[1]
sendDataPort = int(sys.argv[2])
receiveAckPort = int(sys.argv[3])
filename = sys.argv[4]

# read file data
with open(filename) as f:
  data = f.read()

packets = []
for p in range(0, len(data), packet.MAX_DATA_LENGTH):
  packets.append( packet.create_packet( int(p/packet.MAX_DATA_LENGTH), data[ p:(p + packet.MAX_DATA_LENGTH) ] ))


N = 10              # window size
nextseqnum = 0
base = 0
totalPackets = len(packets)
timer = None

# UPD socket
senderSocket = socket(AF_INET, SOCK_DGRAM)
senderSocket.bind((hostAddress, receiveAckPort))


seqfile = open("seqnum.log", "w")
ackfile = open("ack.log", "w")

acknowledger = threading.Thread(target=ack)
acknowledger.start()

while not (base == totalPackets):
  if nextseqnum < totalPackets and nextseqnum < base + N:
    senderSocket.sendto( packets[ nextseqnum ].get_udp_data() , (hostAddress, sendDataPort))
    seqfile.write(str(nextseqnum) + "\n")

    nextseqnum = nextseqnum + 1
    print(nextseqnum % packet.SEQ_NUM_MODULO)
    if base == nextseqnum:
      timer = time.time()

  elif timer != None and (time.time() - timer > 0.1):
    baseModulo = base % packet.SEQ_NUM_MODULO

    # resend packets
    for p in range(base, nextseqnum):
      senderSocket.sendto( packets[p].get_udp_data() , (hostAddress, sendDataPort))
      seqfile.write(str(p) + "\n")

    timer = time.time()




print("PROGRAM ENDED------")
print("total packets:", totalPackets)

# send eot
senderSocket.sendto( packet.create_eot(-1).get_udp_data() , (hostAddress, sendDataPort))
acknowledger.join()

seqfile.close()
ackfile.close()
