from socket import *
from packet import packet
import select
import threading
import time
import sys

def getUDP():
  global senderSocket
  global currPacket
  UDPdata, clientAddress = senderSocket.recvfrom( 2048 )
  currPacket = packet.parse_udp_data(UDPdata)


def ack():
  global N
  global senderSocket
  global base
  global nextseqnum
  global timer

  while True:
    UDPdata, clientAddress = senderSocket.recvfrom( 2048 )
    p = packet.parse_udp_data(UDPdata)
    print("received packet: ", p.seq_num)
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
    elif baseModulo > windowEnd and (currSeqnum <= windowEnd or baseModulo <= currSeqnum):
      if baseModulo <= currSeqnum:
        base = base + currSeqnum + 1 - baseModulo
      else:
        base = base + currSeqnum + 1 + (packet.SEQ_NUM_MODULO - baseModulo)
    
    if base > lastBase:
      if base != currSeqnum:
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
packetsSent = 0
nextseqnum = 0
base = 0
totalPackets = len(packets)
timer = time.time()
currPacket = None

# UPD socket
senderSocket = socket(AF_INET, SOCK_DGRAM)
senderSocket.bind((hostAddress, receiveAckPort))


#seqfile = open("seqnum.log", "w")
#ackfile = open("ack.log", "w")

acknowledger = threading.Thread(target=ack)
acknowledger.start()

while not (base == nextseqnum and nextseqnum == totalPackets):
  # set endpoint to loop until min(packetsSent + N, totalPackets), start new thread for current window
  if nextseqnum < totalPackets and nextseqnum < base + N:
    senderSocket.sendto( packets[ nextseqnum % packet.SEQ_NUM_MODULO ].get_udp_data() , (hostAddress, sendDataPort))

    if base == nextseqnum:
      timer = time.time()
    nextseqnum = nextseqnum + 1

  elif timer != None and (timer - time.time() > 0.1):
    timer = time.time()
    baseModulo = base % packet.SEQ_NUM_MODULO

    # resend packets
    if baseModulo < (nextseqnum % packet.SEQ_NUM_MODULO):
      for p in range(baseModulo, nextseqnum % packet.SEQ_NUM_MODULO):
        senderSocket.sendto( packets[p].get_udp_data() , (hostAddress, sendDataPort))
    else:
      for p in range(baseModulo, packet.SEQ_NUM_MODULO):
        senderSocket.sendto( packets[p].get_udp_data() , (hostAddress, sendDataPort))
      for p in range(0, nextseqnum % packet.SEQ_NUM_MODULO):
        senderSocket.sendto( packets[p].get_udp_data() , (hostAddress, sendDataPort))


print("PROGRAM ENDED------------packetsSent:",packetsSent)

# send eot
senderSocket.sendto( packet.create_eot(-1).get_udp_data() , (hostAddress, sendDataPort))

