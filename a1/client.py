from socket import *
import sys
# server.py <server_address>, <n_port>, <req_code>, and <msg>
server_address = sys.argv[1]
n_port = int(sys.argv[2])
req_code = sys.argv[3]
msg = sys.argv[4]

clientSocket = socket(AF_INET, SOCK_DGRAM) 

# send req_code via UDP to verify
clientSocket.sendto(req_code.encode(),(server_address, n_port))

# receive r_port and send back to confirm
r_port, serverAddress = clientSocket.recvfrom(2048)
clientSocket.sendto(r_port,(server_address, n_port))

# confirm acknowledgement from server
result, serverAddress = clientSocket.recvfrom(2048)
print()
if (result.decode() != "success"):
  print("failed to receive r_port confirmation from server")
  exit()

clientSocket.close()
# r_port confirmation received!

# TCP transaction
TCP_Socket = socket(AF_INET, SOCK_STREAM) 
TCP_Socket.connect((server_address, int(r_port.decode()))) 

TCP_Socket.send(msg.encode())

modifiedSentence = TCP_Socket.recv(1024) 
print("From Server:", modifiedSentence.decode()) 

TCP_Socket.close()
