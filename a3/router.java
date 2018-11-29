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

    public static void main(String[] args) throws Exception{
        // members
        linkData [] localLinks = new linkData[5];
        int linkNum;

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

        // pending hellos
        ArrayList<Integer> pendingHellos = new ArrayList<Integer>();
        // send lspdus
        ArrayList<LSPDU > sentLS_PDU = new ArrayList<LSPDU>();

        // database
        Map<linkData, finishedEdge> finishedEdges = new HashMap<linkData, finishedEdge>();
        Map<linkData, Integer> incompleteEdges= new HashMap<linkData, Integer>();

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

        bufferedWriter.write("R" + stringID + " receives a CIRCUIT_DB: nbr_link " + Integer.toString(linkNum) + "\n");

        for (int link=0; link < linkNum; link++) {
            incompleteEdges.put(localLinks[link], ID);
        }

        // populate array list of pending Hellos
        for (int neighbor = 0; neighbor<linkNum; neighbor++) {
            Integer neighborLink = localLinks[neighbor].link;
            pendingHellos.add(neighborLink);
        }
        
        // print database
        String [] printLinkData = new String[5];
        Arrays.fill(printLinkData, "");

        int [] printLinkNum = new int[5];
        Arrays.fill(printLinkNum, 0);
        
        for ( linkData elem: incompleteEdges.keySet() ) { // only contains data for itself at the beginning
            int routerID = incompleteEdges.get(elem);
            int routerIndex = routerID-1;
            printLinkNum[routerIndex]++;
            printLinkData[routerIndex] += "R" + stringID + " -> " + "R" + Integer.toString(routerID) + " link-" + Integer.toString(elem.link) + " cost " + Integer.toString(elem.cost) + "\n";
        }

        bufferedWriter.write("\n# Topology database\n");
        for (int i=0; i<5 && printLinkNum[i] !=0 ; i++) {
            bufferedWriter.write("R" + stringID + " -> " + "R" + Integer.toString(i+1) + " nbr_link " + Integer.toString(printLinkNum[i]) + "\n");
            bufferedWriter.write(printLinkData[i]);
        }
        
        bufferedWriter.write("\n# RIB\n");
        for (int i = 0; i<5; i++) {
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

            ByteBuffer buffer = ByteBuffer.allocate(8);
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            // add data
            buffer.putInt(ID); buffer.putInt( localLinks[neighbor].link );

            DatagramPacket hello_packet = new DatagramPacket(buffer.array(), buffer.array().length, address, nsePort);
            UDP_Socket.send(hello_packet);
        }

        // data for dijkstras
        int [] linkCostArray = null;
        int [] linkNameArray = null;

        while (true) {
            try {
                dataArray=new byte[562];
                DatagramPacket newPacket= new DatagramPacket(dataArray, dataArray.length);
                // Loop exit condition is timeout
                UDP_Socket.setSoTimeout(1500);
                UDP_Socket.receive(newPacket);

                ByteBuffer buffer = ByteBuffer.wrap(dataArray);
                buffer.order(ByteOrder.LITTLE_ENDIAN);

                // tentatively assume it is a LSPDU, perform a check to confirm
                int sender=buffer.getInt();
                int router_id=buffer.getInt();
                int link=buffer.getInt();
                int cost=buffer.getInt();
                int via=buffer.getInt();

                if (link != 0) { // if lspdu, because a hello will contain zeros for these fields
                    LSPDU rec_lspdu = LSPDU.lspdu_parseUDPdata(dataArray);
                    String message = "R" + stringID + " receives an ls_PDU: sender " + Integer.toString(rec_lspdu.sender);
                    message += ", ID " + Integer.toString(rec_lspdu.router_id) + ", linkID " + Integer.toString(rec_lspdu.link_id);
                    message += ", cost " + Integer.toString(rec_lspdu.cost) + ", via " + Integer.toString(rec_lspdu.via) + "\n";
                    bufferedWriter.write(message);


                    linkData pktLink = new linkData(link, cost);

                    // Inform each of the rest of neighbours by forwarding/rebroadcasting this LS_PDU to them.
                    for (int i = 0; i < linkNum; i++) {
                        LSPDU forward_pkt = new LSPDU(ID, rec_lspdu.router_id, link, cost, localLinks[i].link);
                        // if bad link (no hello received, don't resend to sender, or already sent before), do not send
                        if (pendingHellos.contains(localLinks[i].link) || localLinks[i].link == via) {
                            continue;
                        }

                        // check if already sent
                        boolean flag = false;
                        for (LSPDU p: sentLS_PDU) {
                            if (p.router_id == forward_pkt.router_id && p.link_id == forward_pkt.link_id &&
                                p.cost == forward_pkt.cost && p.via == forward_pkt.via) {
                                flag = true;
                            }
                        }
                        if (flag) continue; // if already sent, continue

                        // send new LS_PDU
                        sentLS_PDU.add(forward_pkt);
                        byte[] forward_message = forward_pkt.getUDPdata();
                        DatagramPacket forward_packet = new DatagramPacket(forward_message, forward_message.length, address, nsePort);
                        UDP_Socket.send(forward_packet);

                        String lspduMessage = "R" + stringID + " sends an ls_PDU: sender " + stringID + ", ID " + Integer.toString(rec_lspdu.router_id) + ", linkID ";
                        lspduMessage += Integer.toString(link) + ", cost " + Integer.toString(cost) + ", via " + Integer.toString(localLinks[i].link) + "\n";
                        bufferedWriter.write(lspduMessage);
                    }

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
                            incompleteEdges.put(pktLink, rec_lspdu.router_id);
                        } // if new finishedEdge is complete, add finishedEdge to finished edges list
                        else if (getLinkID(pktLink, incompleteEdges) != rec_lspdu.router_id) {
                            finishedEdge routers = new finishedEdge(rec_lspdu.router_id, getLinkID(pktLink, incompleteEdges));
                            incompleteEdges.put(pktLink, rec_lspdu.router_id);
                            finishedEdges.put(pktLink, routers);

                            // Compute one hop for shortest path
                            linkCostArray = new int[5];
                            linkNameArray = new int[5];
                            Arrays.fill(linkCostArray, Integer.MAX_VALUE);
                            Arrays.fill(linkNameArray, Integer.MAX_VALUE);

                            ArrayList<Integer> spanningTree = new ArrayList<Integer>();
                            
                            // add root for Djikstras
                            spanningTree.add(ID);
                            // add boundary nodes
                            for (linkData elem : finishedEdges.keySet()) {
                                if (finishedEdges.get(elem).router1 == ID) {
                                    linkCostArray[finishedEdges.get(elem).router2 -1] = elem.cost;
                                    linkNameArray[finishedEdges.get(elem).router2 -1] = finishedEdges.get(elem).router2;
                                } 
                                else if (finishedEdges.get(elem).router2 == ID) {
                                    linkCostArray[finishedEdges.get(elem).router1 -1] = elem.cost;
                                    linkNameArray[finishedEdges.get(elem).router1 -1] = finishedEdges.get(elem).router1;
                                }
                            }
                            // add 4 more nodes to complete tree, iterate 4 times
                            for (int j=0; j<4; j++) {
                                int nodeIndex = 0;
                                int lowerBound = Integer.MAX_VALUE;

                                // choose finishedEdge with lowest cost
                                for (int i = 0; i < 5; i++) {
                                    if (spanningTree.contains(i+1)) {
                                        continue;
                                    }
                                    if (linkCostArray[i] <= lowerBound) {
                                        nodeIndex = i;
                                        lowerBound = linkCostArray[i];
                                    }
                                }
                                spanningTree.add(nodeIndex + 1);
                                // update edges
                                for (linkData elem : finishedEdges.keySet()) {
                                    int otherRouter;
                                    // find finishedEdge attached to newly added router
                                    if (finishedEdges.get(elem).router1 == (nodeIndex + 1)) {
                                        otherRouter = finishedEdges.get(elem).router2;
                                    } 
                                    else if (finishedEdges.get(elem).router2 == (nodeIndex + 1)) {
                                        otherRouter = finishedEdges.get(elem).router1;
                                    } 
                                    else { // if finishedEdge is not connected to newly added node, ignore
                                        continue;
                                    }
                                    // check if finishedEdge is new
                                    if (spanningTree.contains(otherRouter) || (linkCostArray[nodeIndex] + elem.cost) < 0) {
                                        continue;
                                    }
                                    if (linkCostArray[otherRouter-1] > linkCostArray[nodeIndex] + elem.cost) {
                                        linkCostArray[otherRouter-1] = linkCostArray[nodeIndex] + elem.cost;
                                        linkNameArray[otherRouter-1] = linkNameArray[nodeIndex];
                                    }
                                }
                            }
                        } 
                        else { // no new info
                            continue;
                        }

                        // The Link State Database and the Routing Information Base (RIB) are printed to a log file
                        printLinkData = new String[5];
                        printLinkNum = new int[5];
                        Arrays.fill(printLinkData, "");
                        Arrays.fill(printLinkNum, 0);

                        // prepare to print
                        for ( linkData elem: incompleteEdges.keySet() ) { 
                            int routerID = incompleteEdges.get(elem);
                            int routerIndex = routerID-1;
                            printLinkNum[routerIndex]++;
                            printLinkData[routerIndex] += "R" + stringID + " -> " + "R" + Integer.toString(routerID) + " link-" + Integer.toString(elem.link) + " cost " + Integer.toString(elem.cost) + "\n";
                        }

                        bufferedWriter.write("\n# Topology database\n");
                        for (int i = 0; i<5 && printLinkNum[i] !=0 ; i++) {
                            bufferedWriter.write("R" + stringID + " -> " + "R" + Integer.toString(i+1) + " nbr_link " + Integer.toString(printLinkNum[i]) + "\n");
                            bufferedWriter.write(printLinkData[i]);
                        }

                        bufferedWriter.write("\n# RIB\n");
                        for (int i = 0; i<5; i++) {
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

                    bufferedWriter.write("R" + stringID + " receives a HELLO: ID " + Integer.toString(helloRouterID) +
                                      ", linkID " + Integer.toString(helloLinkID));
                    bufferedWriter.write("\n");

                    for (linkData elem : incompleteEdges.keySet()) { // send each finishedEdge one at a time from set of lspdus
                        int routerOfEdge = getLinkID(elem, incompleteEdges);
                        LSPDU hello_response = new LSPDU(ID, routerOfEdge, elem.link, elem.cost, helloLinkID);
                        byte[] hello_res = hello_response.getUDPdata();
                        DatagramPacket hello_response_pkt = new DatagramPacket(hello_res, hello_res.length, address, nsePort);
                        UDP_Socket.send(hello_response_pkt);
                        
                        String helloMsg = "R" + stringID + " sends an ls_PDU: sender " + stringID +", ID " + Integer.toString(routerOfEdge) + ", linkID " + Integer.toString(elem.link);
                        helloMsg += ", cost " + Integer.toString(elem.cost) + ", via " + Integer.toString(helloLinkID) + "\n";
                        bufferedWriter.write(helloMsg);
                    }
                    // remove link from pending Hellos
                    pendingHellos.remove( Integer.valueOf(helloLinkID) );
                }
            } catch (SocketTimeoutException e) {
                // program end
                bufferedWriter.close();
                break;
            }
        }
        // end message
        System.out.println ("R" + ID + " has finished");
    }
}


