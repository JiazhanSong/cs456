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


def ack(startpoint, endpoint):
  global N
  global packets
  global packetsSent
  global senderSocket
  global currPacket

  # start timer
  startTime = time.time()

  temporarySent = 0
  offset = 0
  startPacket = startpoint % packet.SEQ_NUM_MODULO - 1
  endPacket = endpoint % packet.SEQ_NUM_MODULO

  while packetsSent + temporarySent < endpoint:
    # packets sent and acked, WAIT ON UDP
    getudp = threading.Thread(target=getUDP, args=())
    getudp.start()
    getudp.join(0.15)
    if time.time() - startTime > 0.15:
      packetsSent = packetsSent + temporarySent
      print("TIMEOUT TIMEOUT packets acked, startPacket: ", packetsSent, startPacket)
      return

    p = currPacket

    # if receving old packet
    if startPacket > endPacket and not p.seq_num < endPacket:
      continue
    elif p.seq_num > startPacket and p.seq_num > endPacket:
      continue
    elif p.seq_num < startPacket and p.seq_num < endPacket:
      continue
    

    if p.seq_num > startPacket:
      offset = p.seq_num - startPacket
    else:
      offset = (N - startPacket) + p.seq_num

    if offset > temporarySent:
      print("new offset: ", offset)
      temporarySent = offset

  # return if all packets acked
  packetsSent = packetsSent + temporarySent
  print("seqnum, packets acked, startPacket: ", p.seq_num, packetsSent, startPacket)
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
  packets.append( packet.create_packet( int(p/packet.MAX_DATA_LENGTH), data[ p:(p + packet.MAX_DATA_LENGTH) ] ))


N = 10              # window size
packetsSent = 0      
totalPackets = len(packets)
currPacket = None

# UPD socket
senderSocket = socket(AF_INET, SOCK_DGRAM)
senderSocket.bind((hostAddress, receiveAckPort))

while packetsSent < totalPackets:
  # set endpoint to loop until min(packetsSent + N, totalPackets), start new thread for current window
  startpoint = packetsSent
  endpoint = min(packetsSent + N, totalPackets)

  acknowledger = threading.Thread(target=ack, args=(startpoint, endpoint))
  acknowledger.start()

  ## send packets
  print("start and end:",startpoint, endpoint)
  for p in range(startpoint, endpoint):
    print( "sending: ",packets[p].seq_num )
    senderSocket.sendto( packets[p].get_udp_data() , (hostAddress, sendDataPort))
  # wait on acknowledger
  acknowledger.join()

senderSocket.sendto( packet.create_eot(-1).get_udp_data() , (hostAddress, sendDataPort))

senderSocket.close()
