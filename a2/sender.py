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
  global ackfile

  # start timer
  startTime = time.time()

  temporarySent = 0
  offset = 0
  startPacket = (startpoint - 1) % packet.SEQ_NUM_MODULO
  endPacket = endpoint % packet.SEQ_NUM_MODULO
  print(1)
  while packetsSent + temporarySent < endpoint:
    # packets sent and acked, WAIT ON UDP
    getudp = threading.Thread(target=getUDP, args=())
    getudp.start()
    getudp.join(0.15)
    print(2)
    if time.time() - startTime > 0.15:
      packetsSent = packetsSent + temporarySent
      return
    print(3)
    p = currPacket
    ackfile.write(str(p.seq_num) + "\n")

    # if receving old packet, ignore
    if startPacket > endPacket:
      if not (p.seq_num < endPacket or p.seq_num > startPacket) :
        continue
    else:
      if p.seq_num > startPacket and p.seq_num > endPacket:
        continue
      elif p.seq_num < startPacket and p.seq_num < endPacket:
        continue
    
    print("seqnum, packets acked, startPacket, offset: ", p.seq_num, packetsSent, startPacket, temporarySent)
    if p.seq_num > startPacket:
      offset = p.seq_num - startPacket
    else:
      offset = (packet.SEQ_NUM_MODULO - 1 - startPacket) + p.seq_num + 1

    if offset > temporarySent:
      temporarySent = offset

  # return if all packets acked
  packetsSent = packetsSent + temporarySent
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


seqfile = open("seqnum.log", "w")
ackfile = open("ack.log", "w")

while packetsSent < totalPackets:
  # set endpoint to loop until min(packetsSent + N, totalPackets), start new thread for current window
  startpoint = packetsSent
  endpoint = min(packetsSent + N, totalPackets)
  print("SEND PACKETS: ", startpoint, endpoint)

  acknowledger = threading.Thread(target=ack, args=(startpoint, endpoint))
  acknowledger.start()

  ## send packets
  for p in range(startpoint, endpoint):
    seqfile.write(str(p) + "\n")
    senderSocket.sendto( packets[p].get_udp_data() , (hostAddress, sendDataPort))
  # wait on acknowledger
  acknowledger.join()

senderSocket.sendto( packet.create_eot(-1).get_udp_data() , (hostAddress, sendDataPort))

senderSocket.close()
