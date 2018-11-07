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

f = open(filename, "w+")

while True:
  UDPdata, clientAddress = serverSocket.recvfrom( receiveDataPort )
  p = parse_udp_data(UDPdata)

  returnPacket = None
  # if data
  if p.type == 1:
    if p.seq_num == expectingPacket % packet.SEQ_NUM_MODULO:
      f.write(p.data) # read and write to file
      
      returnPacket = packet.create_ack(p.seq_num) #send response
      expectingPacket = expectingPacket + 1
    else:
      returnPacket = packet.create_ack(expectingPacket - 1)
    # send
    serverSocket.sendto( returnPacket.get_udp_data() , (hostAddress, sendAckPort))

  # if eot
  elif p.type == 2:
    returnPacket = packet.create_eot(-1)
    # send
    serverSocket.sendto( returnPacket.get_udp_data() , (hostAddress, sendAckPort))
    break
  
f.close()
serverSocket.close()