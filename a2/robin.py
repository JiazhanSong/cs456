from packet import packet
from socket import *
from argparse import *
import threading
import time

def listen(f, t):
    global WINDOW_SIZE
    global successful_packets

    for i in range(f, t): 
        ## wait for ack of packet i
        print(i)

    successful_packets += WINDOW_SIZE

##############################

parser = ArgumentParser()
parser.add_argument("host_address")
parser.add_argument("r_port_num")
parser.add_argument("s_port_num")
parser.add_argument("filename")

## holds all the arguments
args = parser.parse_args()

## parse text file
with open (args.filename, "r") as myfile:
    data=myfile.read()[:-1]

data = [data[i:i+packet.MAX_DATA_LENGTH] for i in range(0, len(data), packet.MAX_DATA_LENGTH)]
data = [packet.create_packet(i, data[i]) for i in range(len(data))]

## open udp socket
udp_socket = socket(AF_INET, SOCK_DGRAM)

## GBN
WINDOW_SIZE = 10
successful_packets = 0
timeout = False

while successful_packets < len(data):
    f = successful_packets
    t = min(len(data), successful_packets + WINDOW_SIZE)

    ## Take next WINDOW_SIZE packets
    window = [data[i] for i in range(f, t)]

    ## start timer (new thread)
    listener = threading.Thread(target=listen, args=(f, t))
    listener.start()

    ## send packets
    for packet in window:
        print("send packet")

    ## wait for listen/timer
    listener.join()

## close udp socket
udp_socket.close()
