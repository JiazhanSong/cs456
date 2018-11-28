import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.nio.file.Path;
import java.nio.file.Paths;

public class router {
    // helper
    public static int getLinkID(link_cost link, Map<link_cost, Integer> hashmap) {
        for (link_cost elem: hashmap.keySet()) {
            if (elem.getLink() == link.getLink() && elem.getCost() == link.getCost()) {
                return hashmap.get(elem);
            }
        }
        return 0;
    }

    public static void main(String[] args) throws Exception{
        // members
        link_cost [] localLinks;
        int linkNum;

        // command line args
        int ID = Integer.valueOf(args[0]);
        String stringID = Integer.toString(ID);
        int nsePort = Integer.valueOf(args[2]);
        // socket
        DatagramSocket UDP_Socket = new DatagramSocket(Integer.valueOf(args[3]));
        InetAddress address = InetAddress.getByName(args[1]); // Determines the IP address of a host, given the host's name.

        // clean up log file if exists already
        Path currentRelativePath = Paths.get(""); // from stack overflow to get current working directory
        String s = currentRelativePath.toAbsolutePath().toString();
        File log = new File(s + "/" + "router" + stringID + ".log");
        log.delete(); // delete if exists

        // pending hellos
        ArrayList<Integer> rec_hello_links = new ArrayList<Integer>();
        // send lspdus
        ArrayList<pkt_LSPDU> sent_lspdu = new ArrayList<pkt_LSPDU>();

        // data for dijkstras
        int [] D_costs = null;
        int [] D_names = null;

        // database
        Map<link_cost,Integer> floating_edges= new HashMap<link_cost,Integer>();
        Map<link_cost, edge> complete_edges = new HashMap<link_cost, edge>();

        // send init packet to network state emulator containing router id
        BufferedWriter log_writer = new BufferedWriter(new FileWriter("router" + stringID + ".log", true));
        log_writer.write("Router " + stringID + " sending INIT to network state emulator\n");
        pkt_INIT init_pkt = new pkt_INIT(ID);
        DatagramPacket init_packet = new DatagramPacket(init_pkt.getUDPdata(), init_pkt.getUDPdata().length, address, nsePort);
        UDP_Socket.send(init_packet);

        // receive circuit data, containing all local edges
        byte[] receiveData = new byte[1024];
        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
        UDP_Socket.receive(receivePacket);

        circuit_DB circuit = circuit_DB.circuit_parseUDPdata(receiveData);
        linkNum = circuit.getNum_links();
        localLinks = circuit.getLinkcost();
        log_writer.write("R" + stringID + " receives a CIRCUIT_DB: nbr_link " + Integer.toString(linkNum) + "\n");

        for (int link=0; link < linkNum; link++) {
            floating_edges.put(localLinks[link], ID);
        }

        // populate array list of pending Hellos
        for (int neighbor = 0; neighbor<linkNum; neighbor++) {
            Integer neighborLink = localLinks[neighbor].getLink();
            rec_hello_links.add(neighborLink);
        }
        
        // print database
        String [] r_db = new String[5];
        Arrays.fill(r_db, "");

        int [] r_db_numlinks = new int[5];
        Arrays.fill(r_db_numlinks, 0);
        
        for ( link_cost elem: floating_edges.keySet() ) { // only contains data for itself at the beginning
            int routerID = floating_edges.get(elem);
            int routerIndex = routerID-1;
            r_db_numlinks[routerIndex]++;
            r_db[routerIndex] += "R" + stringID + " -> " + "R" + Integer.toString(routerID) + " link-" + Integer.toString(elem.getLink()) + " cost " + Integer.toString(elem.getCost()) + "\n";
        }

        log_writer.write("\n# Topology database\n");
        for (int i=0; i<5 && r_db_numlinks[i] !=0 ; i++) {
            log_writer.write("R" + stringID + " -> " + "R" + Integer.toString(i+1) + " nbr_link " + Integer.toString(r_db_numlinks[i]) + "\n");
            log_writer.write(r_db[i]);
        }
        
        log_writer.write("\n# RIB\n");
        for (int i = 0; i<5; i++) {
            String routerDirection = "R" + stringID + " -> " + "R" + Integer.toString(i+1);
            String output = "Unknown, Unknown";
            // check if local
            if ((i+1) == ID) {
                log_writer.write(routerDirection + " -> Local, 0");
            } 
            else {
                log_writer.write(routerDirection + " -> " + output);
            }

            log_writer.write("\n");
        }
        log_writer.write("\n");

        // Each router then sends a HELLO_PDU to tell its neighbour
        for (int neighbor=0; neighbor<linkNum; neighbor++) {
            log_writer.write("R" + stringID + " sends a HELLO: ID " + stringID + ", linkID " + Integer.toString(localLinks[neighbor].getLink()) + "\n");
            pkt_HELLO hello_pkt = new pkt_HELLO(ID, localLinks[neighbor].getLink());
            DatagramPacket hello_packet = new DatagramPacket(hello_pkt.getUDPdata(), hello_pkt.getUDPdata().length, address, nsePort);
            UDP_Socket.send(hello_packet);
        }

        while (true) {
            try {
                receiveData=new byte[1024];
                receivePacket= new DatagramPacket(receiveData, receiveData.length);
                UDP_Socket.setSoTimeout(1500);
                UDP_Socket.receive(receivePacket);

                ByteBuffer buffer = ByteBuffer.wrap(receiveData);
                buffer.order(ByteOrder.LITTLE_ENDIAN);

                // tentatively assume it is a pkt_LSPDU, perform a check to confirm
                int sender=buffer.getInt();
                int router_id=buffer.getInt();
                int link=buffer.getInt();
                int cost=buffer.getInt();
                int via=buffer.getInt();

                if (link != 0) { // if lspdu, because a pkt_hello will contain zeros for these fields
                    pkt_LSPDU rec_lspdu = pkt_LSPDU.lspdu_parseUDPdata(receiveData);
                    String message = "R" + stringID + " receives an ls_PDU: sender " + Integer.toString(rec_lspdu.getSender());
                    message += ", ID " + Integer.toString(rec_lspdu.getRouter_id()) + ", linkID " + Integer.toString(rec_lspdu.getLink_id());
                    message += ", cost " + Integer.toString(rec_lspdu.getCost()) + ", via " + Integer.toString(rec_lspdu.getVia()) + "\n";
                    log_writer.write(message);


                    link_cost linkcost = new link_cost(link, cost);

                    // Inform each of the rest of neighbours by forwarding/rebroadcasting this LS_PDU to them.
                    for (int i = 0; i < linkNum; i++) {
                        pkt_LSPDU forward_pkt = new pkt_LSPDU(ID, rec_lspdu.getRouter_id(), link, cost, localLinks[i].getLink());
                        // if bad link (no hello received, don't resend to sender, or already sent before), do not send
                        if (rec_hello_links.contains(localLinks[i].getLink()) || localLinks[i].getLink() == via) {
                            continue;
                        }

                        // check if already sent
                        boolean flag = false;
                        for (pkt_LSPDU p: sent_lspdu) {
                            if (p.getRouter_id() == forward_pkt.getRouter_id() && p.getLink_id() == forward_pkt.getLink_id() &&
                                p.getCost() == forward_pkt.getCost() && p.getVia() == forward_pkt.getVia()) {
                                flag = true;
                            }
                        }
                        if (flag) continue; // if already sent, continue

                        // send new LS_PDU
                        sent_lspdu.add(forward_pkt);
                        byte[] forward_message = forward_pkt.getUDPdata();
                        DatagramPacket forward_packet = new DatagramPacket(forward_message, forward_message.length, address, nsePort);
                        UDP_Socket.send(forward_packet);

                        String lspduMessage = "R" + stringID + " sends an ls_PDU: sender " + stringID + ", ID " + Integer.toString(rec_lspdu.getRouter_id()) + ", linkID ";
                        lspduMessage += Integer.toString(link) + ", cost " + Integer.toString(cost) + ", via " + Integer.toString(localLinks[i].getLink()) + "\n";
                        log_writer.write(lspduMessage);
                    }

                    edge keyCheck = complete_edges.get(linkcost); // returns null if key not in map, otherwise value
                    if (keyCheck == null) { // not a complete edge
                        boolean keycheck2 = false;
                        for (link_cost elem: floating_edges.keySet()) {
                            if (elem.getLink() == linkcost.getLink() && elem.getCost() == linkcost.getCost()) {
                                keycheck2 = true;
                            }
                        }
                        // if not in incomplete edges list, add
                        if (!keycheck2) {
                            floating_edges.put(linkcost, rec_lspdu.getRouter_id());
                        } // if new edge is complete, add edge to finished edges list
                        else if (getLinkID(linkcost, floating_edges) != rec_lspdu.getRouter_id()) {
                            edge routers = new edge(rec_lspdu.getRouter_id(), getLinkID(linkcost, floating_edges));
                            floating_edges.put(linkcost, rec_lspdu.getRouter_id());
                            complete_edges.put(linkcost, routers);

                            // Compute one hop for shortest path
                            D_costs = new int[5];
                            D_names = new int[5];
                            Arrays.fill(D_costs, Integer.MAX_VALUE);
                            Arrays.fill(D_names, Integer.MAX_VALUE);

                            ArrayList<Integer> inTree = new ArrayList<Integer>();
                            
                            // add root for Djikstras
                            inTree.add(ID);
                            // add boundary nodes
                            for (link_cost elem : complete_edges.keySet()) {
                                if (complete_edges.get(elem).getR1() == ID) {
                                    D_costs[complete_edges.get(elem).getR2() - 1] = elem.getCost();
                                    D_names[complete_edges.get(elem).getR2() - 1] = complete_edges.get(elem).getR2();
                                } 
                                else if (complete_edges.get(elem).getR2() == ID) {
                                    D_costs[complete_edges.get(elem).getR1() - 1] = elem.getCost();
                                    D_names[complete_edges.get(elem).getR1() - 1] = complete_edges.get(elem).getR1();
                                }
                            }
                            // add 4 more nodes to complete tree, iterate 4 times
                            for (int j=0; j<4; j++) {
                                int min = Integer.MAX_VALUE;
                                int min_index = 0;

                                // choose edge with lowest cost
                                for (int i = 0; i < 5; i++) {
                                    if (inTree.contains(i + 1)) continue;
                                    if (D_costs[i] <= min) {
                                        min_index = i;
                                        min = D_costs[i];
                                    }
                                }
                                inTree.add(min_index + 1);
                                // update edges
                                for (link_cost elem : complete_edges.keySet()) {
                                    int otherRouter;
                                    // find edge attached to newly added router
                                    if (complete_edges.get(elem).getR1() == (min_index + 1)) {
                                        otherRouter = complete_edges.get(elem).getR2();
                                    } 
                                    else if (complete_edges.get(elem).getR2() == (min_index + 1)) {
                                        otherRouter = complete_edges.get(elem).getR1();
                                    } 
                                    else { // if edge is not connected to newly added node, ignore
                                        continue;
                                    }
                                    // check if edge is new
                                    if (inTree.contains(otherRouter) || (D_costs[min_index] + elem.getCost()) < 0) continue;
                                    if (D_costs[otherRouter - 1] > D_costs[min_index] + elem.getCost()) {
                                        D_costs[otherRouter - 1] = D_costs[min_index] + elem.getCost();
                                        D_names[otherRouter - 1] = D_names[min_index];
                                    }
                                }
                            }
                        } 
                        else { // no new info
                            continue;
                        }

                        // The Link State Database and the Routing Information Base (RIB) are printed to a log file
                        r_db = new String[5];
                        r_db_numlinks = new int[5];
                        Arrays.fill(r_db, "");
                        Arrays.fill(r_db_numlinks, 0);

                        // prepare to print
                        for ( link_cost elem: floating_edges.keySet() ) { 
                            int routerID = floating_edges.get(elem);
                            int routerIndex = routerID-1;
                            r_db_numlinks[routerIndex]++;
                            r_db[routerIndex] += "R" + stringID + " -> " + "R" + Integer.toString(routerID) + " link-" + Integer.toString(elem.getLink()) + " cost " + Integer.toString(elem.getCost()) + "\n";
                        }

                        log_writer.write("\n# Topology database\n");
                        for (int i = 0; i<5 && r_db_numlinks[i] !=0 ; i++) {
                            log_writer.write("R" + stringID + " -> " + "R" + Integer.toString(i+1) + " nbr_link " + Integer.toString(r_db_numlinks[i]) + "\n");
                            log_writer.write(r_db[i]);
                        }

                        log_writer.write("\n# RIB\n");
                        for (int i = 0; i<5; i++) {
                            int currentID = i + 1;
                            String routerDirection = "R" + stringID + " -> " + "R" + Integer.toString(currentID);
                            String output = "Unknown, Unknown";
                            if (D_names != null && D_names[i] != Integer.MAX_VALUE) {
                                output = "R" + Integer.toString(D_names[i]) + ", " + Integer.toString(D_costs[i]);
                            }
                            if (currentID == ID) {
                                log_writer.write(routerDirection + " -> Local, 0\n");
                            } 
                            else {
                                log_writer.write(routerDirection + " -> " + output + "\n");
                            }
                        }
                        log_writer.write("\n");
                    }

                } 
                else { // it is hello packet
                    pkt_HELLO rec_hello = pkt_HELLO.hello_parseUDPdata(receiveData);
                    log_writer.write("R" + stringID + " receives a HELLO: ID " + Integer.toString(rec_hello.getRouter_id()) +
                                      ", linkID " + Integer.toString(rec_hello.getLink_id()));
                    log_writer.write("\n");

                    for (link_cost elem : floating_edges.keySet()) { // send each edge one at a time from set of lspdus
                        int routerOfEdge = getLinkID(elem, floating_edges);
                        pkt_LSPDU hello_response = new pkt_LSPDU(ID, routerOfEdge, elem.getLink(), elem.getCost(), rec_hello.getLink_id());
                        byte[] hello_res = hello_response.getUDPdata();
                        DatagramPacket hello_response_pkt = new DatagramPacket(hello_res, hello_res.length, address, nsePort);
                        UDP_Socket.send(hello_response_pkt);
                        
                        String helloMsg = "R" + stringID + " sends an ls_PDU: sender " + stringID +", ID " + Integer.toString(routerOfEdge) + ", linkID " + Integer.toString(elem.getLink());
                        helloMsg += ", cost " + Integer.toString(elem.getCost()) + ", via " + Integer.toString(rec_hello.getLink_id()) + "\n";
                        log_writer.write(helloMsg);
                    }
                    // remove link from pending Hellos
                    rec_hello_links.remove( Integer.valueOf(rec_hello.getLink_id()) );
                }
            } catch (SocketTimeoutException e) {
                // program end
                log_writer.close();
                break;
            }
        }
    }
}


