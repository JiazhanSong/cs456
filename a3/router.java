import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.nio.file.Path;
import java.nio.file.Paths;

public class router {

    public static boolean floating_edges_contains(Map<link_cost, Integer> map, link_cost key){
        for (link_cost l:map.keySet()){
            if (l.getLink() == key.getLink() && l.getCost() == key.getCost()){
                return true;
            }
        }
        return false;
    }

    public static boolean complete_edges_contains(Map<link_cost, edge> map, link_cost key){
        for (link_cost l:map.keySet()){
            if (l.getLink() == key.getLink() && l.getCost() == key.getCost()){
                return true;
            }
        }
        return false;
    }

    public static int floating_edges_retreive(Map<link_cost, Integer> map, link_cost key){
        int return_val = 0;
        for (link_cost l:map.keySet()){
            if (l.getLink() == key.getLink() && l.getCost() == key.getCost()){
                return_val = map.get(l);
            }
        }
        return return_val;
    }

    public static edge complete_edges_retreive(Map<link_cost, edge> map, link_cost key){
        edge return_val = null;
        for (link_cost l:map.keySet()){
            if (l.getLink() == key.getLink() && l.getCost() == key.getCost()){
//                edge map_edge = map.get(l);
//                return_val = new edge(map_edge.getR1(), map_edge.getR2());
                return_val = map.get(l);
            }
        }
        return return_val;
    }

    public static boolean check_lspdu(ArrayList<pkt_LSPDU> pktlist, pkt_LSPDU pkt){
        for (pkt_LSPDU p:pktlist){
            if (p.getSender() == pkt.getSender() &&
                p.getRouter_id() == pkt.getRouter_id() &&
                p.getLink_id() == pkt.getLink_id() &&
                p.getCost() == pkt.getCost() &&
                p.getVia() == pkt.getVia()) return true;
        }
        return false;
    }

    public static void main(String[] args) throws Exception {
        // members
        link_cost [] local_linkcosts;
        int numlinks;

        // reading in command line arguements
        int router_id = Integer.valueOf(args[0]);
        String hostname = args[1];
        int nse_port = Integer.valueOf(args[2]);
        DatagramSocket receiveSocket = new DatagramSocket(Integer.valueOf(args[3]));
        InetAddress address = InetAddress.getByName(hostname); // Determines the IP address of a host, given the host's name.

        String filename = "router" + Integer.toString(router_id) + ".log";
        // clean up log file if exists already
        Path currentRelativePath = Paths.get(""); // from stack overflow to get current working directory
        String s = currentRelativePath.toAbsolutePath().toString();
        File log = new File(s + "/" + "router" + Integer.toString(router_id) + ".log");
        log.delete(); // delete if exists

        // general set up
        BufferedWriter log_writer = new BufferedWriter(new FileWriter(filename, true));
        Map<link_cost,Integer> floating_edges= new HashMap<link_cost,Integer>();
        Map<link_cost, edge> complete_edges = new HashMap<link_cost, edge>();

        int [] D_costs = null;
        int [] D_names = null;
        ArrayList<Integer> rec_hello_links = new ArrayList<Integer>();
        ArrayList<pkt_LSPDU> sent_lspdu = new ArrayList<pkt_LSPDU>();

        // send init packet to network state emulator containing router id
        log_writer.write("Router " + Integer.toString(router_id) + " sending INIT to network state emulator\n");
        pkt_INIT init_pkt = new pkt_INIT(router_id);
        DatagramPacket init_packet = new DatagramPacket(init_pkt.getUDPdata(), init_pkt.getUDPdata().length, address, nse_port);
        receiveSocket.send(init_packet);

        // waiting to receive circuit_db from nse
        byte[] receiveData = new byte[1024];
        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
        receiveSocket.receive(receivePacket);
        circuit_DB circuit = circuit_DB.circuit_parseUDPdata(receiveData);

        // adding circuit_db data to local router's database
        numlinks = circuit.getNum_links();
        local_linkcosts = circuit.getLinkcost();
        log_writer.write("R" + Integer.toString(router_id) + " receives a CIRCUIT_DB: nbr_link " + Integer.toString(numlinks));
        log_writer.newLine();
        for (int i = 0; i<numlinks; i++){
            floating_edges.put(local_linkcosts[i], router_id);
        }

        // populate array list of pending Hellos
        for (int neighbor = 0; neighbor<numlinks; neighbor++) {
            Integer linkNum = local_linkcosts[neighbor].getLink();
            rec_hello_links.add(linkNum);
        }
        
        // logging initial topology/rib
        String [] r_db = new String[5];
        Arrays.fill(r_db, "");

        int [] r_db_numlinks = new int[5];
        Arrays.fill(r_db_numlinks, 0);

        String starter = "R" + Integer.toString(router_id) + " -> ";
        
        for ( link_cost elem: floating_edges.keySet() ) { // only contains data for itself at the beginning
            int routerID = floating_edges.get(elem);
            int routerIndex = routerID-1;
            r_db_numlinks[routerIndex]++;
            r_db[routerIndex] += starter + "R" + Integer.toString(routerID) + " link-" + Integer.toString(elem.getLink()) + " cost-" + Integer.toString(elem.getCost()) + "\n";
        }

        log_writer.write("\n# Topology database\n");

        for (int i = 0; i<5 && r_db_numlinks[i] !=0 ; i++){
            log_writer.write(starter + "R" + Integer.toString(i+1) + " nbr link " + Integer.toString(r_db_numlinks[i]) + "\n");
            log_writer.write(r_db[i]);
        }
        
        log_writer.write("\n# RIB\n");

        for (int i = 0; i<5; i++){
            String routerDirection = "R" + Integer.toString(router_id) + " -> " + "R" + Integer.toString(i+1);
            String output = "Unknown, Unknown";
            if (router_id == (i+1)){
                log_writer.write(routerDirection + " -> Local, 0");
            } else {
                log_writer.write(routerDirection + " -> " + output);
            }
            log_writer.newLine();
        }
        log_writer.newLine();

        // sending hello packets to router's neighbours
        for (int neighbor = 0; neighbor<numlinks; neighbor++){
            log_writer.write("R" + Integer.toString(router_id) + " sends a HELLO: router_id " + Integer.toString(router_id) +
                              ", link_id " + Integer.toString(local_linkcosts[neighbor].getLink()));
            log_writer.newLine();
            pkt_HELLO hello_pkt = new pkt_HELLO(router_id, local_linkcosts[neighbor].getLink());
            DatagramPacket hello_packet = new DatagramPacket(hello_pkt.getUDPdata(), hello_pkt.getUDPdata().length, address, nse_port);
            receiveSocket.send(hello_packet);
        }

        // receiving LS_PDUs & HELLOs from neighbours
        while ( true ) {
            try {
                receiveData = new byte[1024];
                receivePacket = new DatagramPacket(receiveData, receiveData.length);
                receiveSocket.setSoTimeout(2000);
                receiveSocket.receive(receivePacket);
                ByteBuffer buffer = ByteBuffer.wrap(receiveData);
                buffer.order(ByteOrder.LITTLE_ENDIAN);
                int int1 = buffer.getInt();
                int int2 = buffer.getInt();
                int int3 = buffer.getInt();
                int int4 = buffer.getInt();
                int int5 = buffer.getInt();

                // checking if packet is hello packet or lspdu packet
                if (int3 != 0 || int4 != 0 || int5 != 0) { // if not hello packet
                    pkt_LSPDU rec_lspdu = pkt_LSPDU.lspdu_parseUDPdata(receiveData);
                    String message = "R" + Integer.toString(router_id) + " receives an LS PDU: sender " + Integer.toString(rec_lspdu.getSender());
                    message += ", router_id " + Integer.toString(rec_lspdu.getRouter_id()) + ", link_id " + Integer.toString(rec_lspdu.getLink_id());
                    message += ", cost " + Integer.toString(rec_lspdu.getCost()) + ", via " + Integer.toString(rec_lspdu.getVia()) + "\n";
                    log_writer.write(message);

                    int link = rec_lspdu.getLink_id();
                    int via = rec_lspdu.getVia();
                    int cost = rec_lspdu.getCost();
                    link_cost linkcost = new link_cost(link, cost);

                    // check if this link is already complete in router's database
                    if (!complete_edges_contains(complete_edges, linkcost)) { // not a complete edge
                        if (!floating_edges_contains(floating_edges, linkcost)) { // adding edge to floating edge list
                            floating_edges.put(linkcost, rec_lspdu.getRouter_id());
                        } else if (floating_edges_retreive(floating_edges, linkcost) != rec_lspdu.getRouter_id()) { // adding edge to complete edge list
                            edge routers = new edge(rec_lspdu.getRouter_id(), floating_edges_retreive(floating_edges, linkcost));
                            floating_edges.put(linkcost, rec_lspdu.getRouter_id());
                            complete_edges.put(linkcost, routers);

                            // Dijkstra's algorithm to compute shortest paths with addition of new edge
                            ArrayList<Integer> inTree = new ArrayList<Integer>();
                            D_costs = new int[5];
                            D_names = new int[5];
                            
                            Arrays.fill(D_costs, Integer.MAX_VALUE);
                            Arrays.fill(D_names, Integer.MAX_VALUE);

                            inTree.add(router_id);
                            for (link_cost l : complete_edges.keySet()) {
                                if (complete_edges.get(l).getR1() == router_id) {
                                    D_costs[complete_edges.get(l).getR2() - 1] = l.getCost();
                                    D_names[complete_edges.get(l).getR2() - 1] = complete_edges.get(l).getR2();
                                } else if (complete_edges.get(l).getR2() == router_id) {
                                    D_costs[complete_edges.get(l).getR1() - 1] = l.getCost();
                                    D_names[complete_edges.get(l).getR1() - 1] = complete_edges.get(l).getR1();
                                }
                            }

                            while (inTree.size() < 5) {
                                int min = Integer.MAX_VALUE;
                                int min_index = 0; // router index (0-4)
                                for (int i = 0; i<5; i++){
                                    if (inTree.contains(min_index + 1)){
                                        min_index++;
                                    }
                                }

                                for (int i = 0; i < 5; i++) {
                                    if (inTree.contains(i + 1)) continue;
                                    if (D_costs[i] < min) {
                                        min_index = i;
                                        min = D_costs[i];
                                    }
                                }
                                inTree.add(min_index + 1); // adding router number to tree (1-5)
                                for (link_cost l : complete_edges.keySet()) {
                                    if (complete_edges.get(l).getR1() == (min_index + 1)) {
                                        int router2 = complete_edges.get(l).getR2();
                                        if (inTree.contains(router2)) continue;
                                        if (D_costs[min_index] + l.getCost() < 0) continue;
                                        if (D_costs[router2 - 1] > D_costs[min_index] + l.getCost()) {
                                            D_costs[router2 - 1] = D_costs[min_index] + l.getCost();
                                            D_names[router2 - 1] = D_names[min_index];
                                        }
                                    } else if (complete_edges.get(l).getR2() == (min_index + 1)) {
                                        int router2 = complete_edges.get(l).getR1();
                                        if (inTree.contains(router2)) continue;
                                        if (D_costs[min_index] + l.getCost() < 0) continue;
                                        if (D_costs[router2 - 1] > D_costs[min_index] + l.getCost()) {
                                            D_costs[router2 - 1] = D_costs[min_index] + l.getCost();
                                            D_names[router2 - 1] = D_names[min_index];
                                        }
                                    }
                                }
                            }
                        } else{ // no changes to topology database
                            continue;
                        }
                        r_db = new String[5];
                        r_db_numlinks = new int[5];
                        Arrays.fill(r_db, "");
                        Arrays.fill(r_db_numlinks, 0);
                        for ( link_cost elem: floating_edges.keySet() ) { // only contains data for itself at the beginning
                            int routerID = floating_edges.get(elem);
                            int routerIndex = routerID-1;
                            r_db_numlinks[routerIndex]++;
                            r_db[routerIndex] += starter + "R" + Integer.toString(routerID) + " link-" + Integer.toString(elem.getLink()) + " cost-" + Integer.toString(elem.getCost()) + "\n";
                        }

                        // logging new topology database
                        log_writer.write("\n# Topology database\n");

                        for (int i = 0; i<5 && r_db_numlinks[i] !=0 ; i++){
                            log_writer.write(starter + "R" + Integer.toString(i+1) + " nbr link " + Integer.toString(r_db_numlinks[i]) + "\n");
                            log_writer.write(r_db[i]);
                        }

                        // logging new rib
                        log_writer.write("\n# RIB\n");

                        for (int i = 0; i<5; i++){
                            String routerDirection = "R" + Integer.toString(router_id) + " -> " + "R" + Integer.toString(i+1);
                            String output = "Unknown, Unknown";
                            if (D_names != null && D_names[i] != Integer.MAX_VALUE){
                                output = "R" + Integer.toString(D_names[i]) + ", " + Integer.toString(D_costs[i]);
                            }
                            if (router_id == (i+1)){
                                log_writer.write(routerDirection + " -> Local, 0");
                            } else {
                                log_writer.write(routerDirection + " -> " + output);
                            }
                            log_writer.newLine();
                            log_writer.newLine();
                        }
                        log_writer.newLine();
                    }

                    // forwarding ls_pdu to all neighbours
                    for (int i = 0; i < numlinks; i++) {
                        if (local_linkcosts[i].getLink() == via || rec_hello_links.contains(local_linkcosts[i].getLink()))
                            continue;
                        pkt_LSPDU forward_pkt = new pkt_LSPDU(router_id, rec_lspdu.getRouter_id(), link, cost, local_linkcosts[i].getLink());
                        if (check_lspdu(sent_lspdu, forward_pkt)) continue;
                        else sent_lspdu.add(forward_pkt);
                        byte[] forward_message = forward_pkt.getUDPdata();
                        DatagramPacket forward_packet = new DatagramPacket(forward_message, forward_message.length, address, nse_port);
                        receiveSocket.send(forward_packet);
                        log_writer.write("R" + Integer.toString(router_id) + " sends an LS PDU: sender " + Integer.toString(router_id) +
                                ", router_id " + Integer.toString(rec_lspdu.getRouter_id()) + ", link_id " + Integer.toString(link) +
                                ", cost " + Integer.toString(cost) + ", via " + Integer.toString(local_linkcosts[i].getLink()));
                        log_writer.newLine();
                    }
                } else { // if it is hello packet
                    pkt_HELLO rec_hello = pkt_HELLO.hello_parseUDPdata(receiveData);
                    log_writer.write("R" + Integer.toString(router_id) + " receives a HELLO: router_id " + Integer.toString(rec_hello.getRouter_id()) +
                                      ", link_id " + Integer.toString(rec_hello.getLink_id()));
                    log_writer.newLine();

                    for (link_cost elem : floating_edges.keySet()) { // send each edge one at a time
                        int router = floating_edges_retreive(floating_edges, elem);
                        pkt_LSPDU hello_response = new pkt_LSPDU(router_id, router, elem.getLink(), elem.getCost(), rec_hello.getLink_id());
                        byte[] hello_res = hello_response.getUDPdata();
                        DatagramPacket hello_response_pkt = new DatagramPacket(hello_res, hello_res.length, address, nse_port);
                        receiveSocket.send(hello_response_pkt);
                        log_writer.write("R" + Integer.toString(router_id) + " sends an LS PDU: sender " + Integer.toString(router_id) +
                                          ", router_id " + Integer.toString(router) + ", link_id " + Integer.toString(elem.getLink()) +
                                          ", cost " + Integer.toString(elem.getCost()) + ", via " + Integer.toString(rec_hello.getLink_id()));
                        log_writer.newLine();
                    }
                    // remove link from pending Hellos
                    rec_hello_links.remove( Integer.valueOf(rec_hello.getLink_id()) );
                }
            } catch (SocketTimeoutException e){
                break;
            }
        }
        log_writer.close();
    }
}


