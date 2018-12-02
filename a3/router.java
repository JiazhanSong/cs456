import java.net.*;
import java.io.*;
import java.util.*;
import java.nio.file.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class router {
    // helper
    public static int getLinkID(linkData link, Map<linkData, Integer> hashmap) {
        for (linkData elem: hashmap.keySet()) {
            if (elem.link == link.link && elem.cost == link.cost) {
                return hashmap.get(elem);
            }
        }
        return 0;
    }

    public static void main(String [] args) throws Exception{    
        // members
        int NBR_ROUTER = 5; 
        linkData [] localLinks = new linkData[NBR_ROUTER];  
        int linkNum;

        // pending hellos, arraylist cannot use primitive int so use Integer
        ArrayList<Integer> pendingHellos = new ArrayList<Integer>();
        // send lspdus
        ArrayList<LSPDU > sentLS_PDU = new ArrayList<LSPDU>();

        // database
        Map<linkData, finishedEdge> finishedEdges = new HashMap<linkData, finishedEdge>();
        Map<linkData, Integer> incompleteEdges= new HashMap<linkData, Integer>();

        // command line args
        int ID = Integer.valueOf(args[0]);
        String stringID = Integer.toString(ID);
        int nsePort = Integer.valueOf(args[2]);
        // socket
        DatagramSocket UDP_Socket = new DatagramSocket(Integer.valueOf(args[3]));
        // Determines the IP address of a host, given the host's name.
        InetAddress address = InetAddress.getByName(args[1]);

        // clean up log file if exists already
        Path currentRelativePath = Paths.get(""); // from stack overflow to get current working directory
        String s = currentRelativePath.toAbsolutePath().toString();
        File outFile = new File(s + "/" + "router" + stringID + ".log");
        outFile.delete(); // delete if exists

        // send init packet to network state emulator containing router id
        BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter("router" + stringID + ".log", true));
        bufferedWriter.write("Router " + stringID + " sending INIT to network state emulator\n");

        ByteBuffer initBuffer = ByteBuffer.allocate(4);     
        initBuffer.order(ByteOrder.LITTLE_ENDIAN);
        initBuffer.putInt(ID);

        DatagramPacket init_packet = new DatagramPacket(initBuffer.array(), initBuffer.array().length, address, nsePort);
        UDP_Socket.send(init_packet);

        // receive circuit data, containing all local edges
        byte[] dataArray = new byte[562];
        DatagramPacket circuitPacket = new DatagramPacket(dataArray, dataArray.length);
        UDP_Socket.receive(circuitPacket);

        ByteBuffer circuitBuffer = ByteBuffer.wrap(dataArray);
        circuitBuffer.order(ByteOrder.LITTLE_ENDIAN);
        linkNum = circuitBuffer.getInt();
        int temp_link;
        int temp_cost;
        for (int i = 0; i<linkNum; i++){
            temp_link = circuitBuffer.getInt();
            temp_cost = circuitBuffer.getInt();
            localLinks[i] = new linkData(temp_link, temp_cost);
        }

        bufferedWriter.write("R" + stringID + " receives Circuit Database: nbr_link " + Integer.toString(linkNum) + "\n");

        for (int link=0; link < linkNum; link++) {
            incompleteEdges.put(localLinks[link], ID);
        }

        // populate array list of pending Hellos
        for (int neighbor=0; neighbor < linkNum; neighbor++) {
            Integer neighborLink = localLinks[neighbor].link;
            pendingHellos.add(neighborLink);
        }
        
        // print database
        String [] printLinkData = new String[NBR_ROUTER];
        Arrays.fill(printLinkData, "");

        int [] printLinkNum = new int[NBR_ROUTER];       
        Arrays.fill(printLinkNum, 0);
        
        for ( linkData elem: incompleteEdges.keySet() ) { // only contains data for itself at the beginning
            int routerID = incompleteEdges.get(elem);
            int routerIndex = routerID-1;
            printLinkData[routerIndex] += "R" + stringID + " -> " + "R" + Integer.toString(routerID) + " link-" + Integer.toString(elem.link) + " cost " + Integer.toString(elem.cost) + "\n";
            printLinkNum[routerIndex]++;
        }

        bufferedWriter.write("\n# Topology database\n");
        for (int i=0; i<NBR_ROUTER && printLinkNum[i] !=0 ; i++) {
            bufferedWriter.write("R" + stringID + " -> " + "R" + Integer.toString(i+1) + " nbr_link " + Integer.toString(printLinkNum[i]) + "\n");
            bufferedWriter.write(printLinkData[i]);
        }
        
        bufferedWriter.write("\n# RIB\n");
        for (int i = 0; i<NBR_ROUTER; i++) {
            String routerDirection = "R" + stringID + " -> " + "R" + Integer.toString(i+1);
            String output = "Unknown, Unknown";
            // check if local
            if ((i+1) == ID) {
                bufferedWriter.write(routerDirection + " -> Local, 0");
            } 
            else {      
                bufferedWriter.write(routerDirection + " -> " + output);
            }

            bufferedWriter.write("\n");
        }
        bufferedWriter.write("\n");

        // Each router then sends a HELLO_PDU to tell its neighbour
        for (int neighbor=0; neighbor<linkNum; neighbor++) {
            bufferedWriter.write("R" + stringID + " sends a HELLO: ID " + stringID + ", linkID " + Integer.toString(localLinks[neighbor].link) + "\n");

            ByteBuffer neighborBuffer = ByteBuffer.allocate(8);
            neighborBuffer.order(ByteOrder.LITTLE_ENDIAN);
            // add data
            neighborBuffer.putInt(ID); neighborBuffer.putInt( localLinks[neighbor].link );

            DatagramPacket hello_packet = new DatagramPacket(neighborBuffer.array(), neighborBuffer.array().length, address, nsePort);
            UDP_Socket.send(hello_packet);
        }

        while (true) {
            try { // receive data
                dataArray=new byte[562];
                DatagramPacket newPacket= new DatagramPacket(dataArray, dataArray.length);
                // Loop exit condition is timeout
                UDP_Socket.setSoTimeout(1600);
                UDP_Socket.receive(newPacket);

                ByteBuffer dataBuffer = ByteBuffer.wrap(dataArray);
                dataBuffer.order(ByteOrder.LITTLE_ENDIAN);

                // tentatively assume it is a LSPDU, perform a check to confirm
                int sender=dataBuffer.getInt();
                int router_id=dataBuffer.getInt();
                int link=dataBuffer.getInt();
                int cost=dataBuffer.getInt();
                int via=dataBuffer.getInt();

                if (link != 0) { // if lspdu, because a hello will contain zeros for this out of bound field
                    // data for dijkstras
                    int [] linkCostArray = new int[NBR_ROUTER]; 
                    int [] linkNameArray = new int[NBR_ROUTER];

                    String message = "R" + stringID + " receives an ls_PDU: sender " + Integer.toString(sender);
                    message += ", ID " + Integer.toString(router_id) + ", linkID " + Integer.toString(link);
                    message += ", cost " + Integer.toString(cost) + ", via " + Integer.toString(via) + "\n";
                    bufferedWriter.write(message);


                    linkData pktLink = new linkData(link, cost);

                    // Inform each of the rest of neighbours by forwarding/rebroadcasting this LS_PDU to them.
                    for (int i = 0; i < linkNum; i++) {
                        LSPDU newLSPDU = new LSPDU(ID, router_id, link, cost, localLinks[i].link);
                        // if bad link (no hello received, don't resend to sender, or already sent before), do not send
                        if (pendingHellos.contains(localLinks[i].link) || localLinks[i].link == via) {
                            continue;
                        }

                        // check if already sent
                        boolean flag = false;
                        for (LSPDU p: sentLS_PDU) {
                            if (p.router_id == newLSPDU.router_id && p.link_id == newLSPDU.link_id &&
                                p.cost == newLSPDU.cost && p.via == newLSPDU.via) {
                                flag = true;
                            }
                        }
                        if (flag) continue; // if already sent, continue

                        // send new LS_PDU
                        sentLS_PDU.add(newLSPDU);
                        ByteBuffer lspduBuffer = ByteBuffer.allocate(20);
                        lspduBuffer.order(ByteOrder.LITTLE_ENDIAN);   
                        // stores 5 integers
                        lspduBuffer.putInt(newLSPDU.sender);
                        lspduBuffer.putInt(newLSPDU.router_id);    lspduBuffer.putInt(newLSPDU.link_id);
                        lspduBuffer.putInt(newLSPDU.cost);         lspduBuffer.putInt(newLSPDU.via);

                        DatagramPacket forward_packet = new DatagramPacket(lspduBuffer.array(), lspduBuffer.array().length, address, nsePort);
                        UDP_Socket.send(forward_packet);

                        // log
                        String lspduMessage = "R" + stringID + " sends an ls_PDU: sender " + stringID + ", ID " + Integer.toString(router_id) + ", linkID ";
                        lspduMessage += Integer.toString(link) + ", cost " + Integer.toString(cost) + ", via " + Integer.toString(localLinks[i].link) + "\n";
                        bufferedWriter.write(lspduMessage);
                    }

                    // update database
                    finishedEdge keyCheck = finishedEdges.get(pktLink); // returns null if key not in map, otherwise value
                    if (keyCheck == null) { // not a complete finishedEdge
                        boolean keycheck2 = false;
                        for (linkData elem: incompleteEdges.keySet()) {
                            if (elem.link == pktLink.link && elem.cost == pktLink.cost) {
                                keycheck2 = true;
                            }
                        }
                        // if not in incomplete edges list, add
                        if (!keycheck2) {
                            incompleteEdges.put(pktLink, router_id);
                        } // if new finishedEdge is complete, add finishedEdge to finished edges list
                        else if (getLinkID(pktLink, incompleteEdges) != router_id) {
                            finishedEdge newEdge = new finishedEdge(router_id, getLinkID(pktLink, incompleteEdges));
                            incompleteEdges.put(pktLink, router_id);
                            finishedEdges.put(pktLink, newEdge);

                            // Compute one hop for shortest path
                            // Costs start at infinity for Dijkstras
                            Arrays.fill(linkCostArray, Integer.MAX_VALUE);
                            Arrays.fill(linkNameArray, Integer.MAX_VALUE);
                            
                            // add root for Djikstras
                            ArrayList<Integer> spanningTree = new ArrayList<Integer>();
                            spanningTree.add(ID);
                            linkCostArray[ID - 1] = 0;
                            linkNameArray[ID - 1] = ID;

                            // add 4 more nodes to complete tree, iterate 4 times
                            for (int j=0; j<4; j++) {
                                int lowerBound = Integer.MAX_VALUE;

                                // update edges
      
                                int bestNew=0;
                                int bestOld=0;
                                int newRouter=0;
                                int oldRouter=0;
                                boolean newNode = false;
                                for (linkData elem: finishedEdges.keySet()) {
                                    if (spanningTree.contains(finishedEdges.get(elem).router1)) {
                                        if (spanningTree.contains(finishedEdges.get(elem).router2)) {
                                            continue;
                                        }
                                        newRouter = finishedEdges.get(elem).router2;
                                        oldRouter = finishedEdges.get(elem).router1;
                                    }
                                    else if (spanningTree.contains(finishedEdges.get(elem).router2)) {
                                        newRouter = finishedEdges.get(elem).router1;
                                        oldRouter = finishedEdges.get(elem).router2;
                                    }
                                    else { continue; }

                                    if (linkCostArray[oldRouter-1] + elem.cost <= lowerBound) {
                                        newNode = true;
                                        lowerBound = linkCostArray[oldRouter-1] + elem.cost;
                                        bestNew = newRouter;
                                        bestOld = oldRouter;
                                    }
                                }
                                if (newNode) {
                                    linkCostArray[bestNew - 1] = lowerBound;
                                    if (bestOld == ID) {
                                        linkNameArray[bestNew - 1] = bestNew;
                                    }
                                    else {
                                        linkNameArray[bestNew - 1] = linkNameArray[bestOld - 1];
                                    }
                                    spanningTree.add(bestNew);
                                }
                                newNode = false;
                            }
                        } 
                        else { // no new info
                            continue;
                        }

                        // The Link State Database and the Routing Information Base (RIB) are printed to a log file
                        printLinkData = new String[NBR_ROUTER];
                        printLinkNum = new int[NBR_ROUTER];
                        Arrays.fill(printLinkData, "");
                        Arrays.fill(printLinkNum, 0);

                        // prepare to print
                        for ( linkData elem: incompleteEdges.keySet() ) { 
                            int routerID = incompleteEdges.get(elem);
                            int routerIndex = routerID-1;
                            printLinkData[routerIndex] += "R" + stringID + " -> " + "R" + Integer.toString(routerID) + " link-" + Integer.toString(elem.link) + " cost " + Integer.toString(elem.cost) + "\n";
                            printLinkNum[routerIndex]++;
                        }

                        bufferedWriter.write("\n# Topology database\n");
                        for (int i = 0; i<NBR_ROUTER && printLinkNum[i] !=0 ; i++) {
                            bufferedWriter.write("R" + stringID + " -> " + "R" + Integer.toString(i+1) + " nbr_link " + Integer.toString(printLinkNum[i]) + "\n");
                            bufferedWriter.write(printLinkData[i]);
                        }

                        bufferedWriter.write("\n# RIB\n");
                        for (int i = 0; i<NBR_ROUTER; i++) {
                            int currentID = i + 1;
                            String routerDirection = "R" + stringID + " -> " + "R" + Integer.toString(currentID);
                            String output = "Unknown, Unknown";
                            if (linkNameArray != null && linkNameArray[i] != Integer.MAX_VALUE) {
                                output = "R" + Integer.toString(linkNameArray[i]) + ", " + Integer.toString(linkCostArray[i]);
                            }
                            if (currentID == ID) {
                                bufferedWriter.write(routerDirection + " -> Local, 0\n");
                            } 
                            else {
                                bufferedWriter.write(routerDirection + " -> " + output + "\n");
                            }
                        }
                        bufferedWriter.write("\n");
                    }

                } 
                else { // it is hello packet
                    ByteBuffer helloBuffer= ByteBuffer.wrap(dataArray);
                    helloBuffer.order(ByteOrder.LITTLE_ENDIAN);
                    int helloRouterID = helloBuffer.getInt();
                    int helloLinkID = helloBuffer.getInt();
    
                    String helloMessage = "R" + stringID + " receives a HELLO: ID " + Integer.toString(helloRouterID);
                    helloMessage += ", linkID " + Integer.toString(helloLinkID) + "\n";
                    bufferedWriter.write(helloMessage);

                    for (linkData elem: incompleteEdges.keySet()) { // send each finishedEdge one at a time from set of lspdus
                        int routerOfEdge = getLinkID(elem, incompleteEdges);

                        ByteBuffer lspduBuffer = ByteBuffer.allocate(20);
                        lspduBuffer.order(ByteOrder.LITTLE_ENDIAN);
                        lspduBuffer.putInt(ID);     lspduBuffer.putInt(routerOfEdge);
                        lspduBuffer.putInt(elem.link);    lspduBuffer.putInt(elem.cost);
                        lspduBuffer.putInt(helloLinkID);
                        DatagramPacket helloLSPDU = new DatagramPacket(lspduBuffer.array(), lspduBuffer.array().length, address, nsePort);
                        UDP_Socket.send(helloLSPDU);
                        
                        String helloMsg = "R" + stringID + " sends an ls_PDU: sender " + stringID +", ID " + Integer.toString(routerOfEdge) + ", linkID " + Integer.toString(elem.link);
                        helloMsg += ", cost " + Integer.toString(elem.cost) + ", via " + Integer.toString(helloLinkID) + "\n";
                        bufferedWriter.write(helloMsg);
                    }
                    // remove link from pending Hellos
                    pendingHellos.remove( Integer.valueOf(helloLinkID) );
                }
            } 
            catch (SocketTimeoutException e) {
                // program end
                bufferedWriter.close();
                break;
            }
        }
        // end message
        System.out.println ("R" + ID + " has finished");
    }
}


