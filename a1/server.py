from socket import * 
import sys

req_code = sys.argv[1]

serverSocket = socket(AF_INET, SOCK_DGRAM) 
serverSocket.bind(('',0))
print("The server is ready to receive")
print("Server n_port number:", serverSocket.getsockname()[1])
print("\n")

while True:
  # receive req_code from UDP
  while True:
    message, clientAddress = serverSocket.recvfrom(2048)    
    modifiedMessage = message.decode()
    print("req_code received is: ", modifiedMessage)
    # verify req_code
    if modifiedMessage == req_code: 
      break

  TCP_Socket = socket(AF_INET, SOCK_STREAM) 
  TCP_Socket.bind(('',0))

  r_portString = str(TCP_Socket.getsockname()[1])

  # Send r_port with UDP connection
  serverSocket.sendto(r_portString.encode(), clientAddress)
  print("r_port sent to client through UDP")

  confirm_port, clientAddress = serverSocket.recvfrom(2048)
  if confirm_port.decode() == r_portString:
    serverSocket.sendto("success".encode(), clientAddress)
    print("r_port confirmation received from client")
  else:
    continue

  # TCP connection
  TCP_Socket.listen(1)
  print("The server is ready to receive message to reverse")

  connectionSocket, addr = TCP_Socket.accept()    
  sentence = connectionSocket.recv(1024).decode()    
  reversedSentence = sentence[::-1]
  connectionSocket.send(reversedSentence.encode())
  connectionSocket.close()

  print("Reversed string has been sent!\n")
  print("---WAITING ON NEW CONNECTION---")

