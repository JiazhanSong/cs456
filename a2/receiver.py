from socket import *
from packet import packet
import threading
import sys

# command line
hostAddress = sys.argv[1]
sendAckPort = int(sys.argv[2])
receiveDataPort = int(sys.argv[3])
filename = sys.argv[4]

serverSocket = socket(AF_INET, SOCK_DGRAM) 

expectingPacket = 0

while True:
  UDPdata, clientAddress = serverSocket.recvfrom( receiveDataPort )
  p = parse_udp_data(UDPdata)

  returnPacket = None
  # if data
  if p.type == 1:
    if p.seq_num == expectingPacket % packet.SEQ_NUM_MODULO:
      returnPacket = packet.create_ack(p.seq_num)
      expectingPacket = expectingPacket + 1
    else:
      returnPacket = packet.create_ack(expectingPacket - 1)
  # if eot
  elif p.type == 2:
    returnPacket = packet.create_eot(p.seq_num)
  
  # send packet
  serverSocket.sendto( returnPacket.get_udp_data() , (hostAddress, sendAckPort))