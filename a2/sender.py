from socket import *
from packet import packet
import threading
import time
import sys

def ack(startpoint, endpoint):
  global N
  global packets
  global packetsSent

  # start timer
  startTime = time.time()

  for p in range(startpoint, endpoint):
    # packets sent and acked, WAIT ON UDP
    packetsSent = packetsSent + 1

    # if taken longer than 150 ms return
    if time.time() - startTime > 0.15:
      return

  # return if all packets acked
  return

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
  packets.append( packet.create_packet( p, data[ p:(p + packet.MAX_DATA_LENGTH) ] ))


N = 10              # window size
packetsSent = 0      
totalPackets = len(packets)

# UPD socket
senderSocket = socket(AF_INET, SOCK_DGRAM) 

while packetsSent < totalPackets:
  # set endpoint to loop until min(packetsSent + N, totalPackets), start new thread for current window
  startpoint = packetsSent
  endpoint = min(packetsSent + N, totalPackets)

  acknowledger = threading.Thread(target=ack, args=(startpoint, endpoint))
  acknowledger.start()

  ## send packets
  print("start and end:",startpoint, endpoint)
  for p in range(startpoint, endpoint):
    senderSocket.sendto( packets[p].get_udp_data() , (hostAddress, receiveAckPort))
  # wait on acknowledger
  acknowledger.join()

senderSocket.sendto( packet.create_eot(-1).get_udp_data() , (hostAddress, receiveAckPort))

senderSocket.close()
