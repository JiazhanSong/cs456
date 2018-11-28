import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.nio.file.Path;
import java.nio.file.Paths;

public class router {
    // members

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
        int numLinks;
        link_cost [] routerLinks;

        int ID = Integer.valueOf(args[0]);
        String stringID = args[0];
        // set up nse host
        String nseHost = args[1];
        InetAddress address = InetAddress.getByName(nseHost); // Determines the IP address of a host, given the host's name.
        int nsePort = Integer.valueOf(args[2]);
        // set up router port
        DatagramSocket receiveSocket = new DatagramSocket( Integer.valueOf(args[3]) ); // socket bound to router port

        // clean up log file if exists already
        Path currentRelativePath = Paths.get(""); // from stack overflow to get current working directory
        String s = currentRelativePath.toAbsolutePath().toString();
        File log = new File(s + "/" + "router" + stringID + ".log");
        log.delete(); // delete if exists

        // general set up
        BufferedWriter log_writer = new BufferedWriter(new FileWriter("router" + stringID + ".log", true));
        Map<link_cost, Integer> newEdges= new HashMap<link_cost,Integer>();
        Map<link_cost, edge> finishedEdges = new HashMap<link_cost, edge>();
        ArrayList<Integer> rec_hello_links = new ArrayList<Integer>();
        ArrayList<pkt_LSPDU> sent_lspdu = new ArrayList<pkt_LSPDU>();
        ArrayList<Integer> inTree = null;
        ArrayList<Integer> D_costs = null;
        ArrayList<Integer> D_names = null;

        // send init packet to network state emulator containing router id
        log_writer.write("Router " + stringID + " sending INIT to network state emulator");
        log_writer.newLine();
        pkt_INIT init_pkt = new pkt_INIT(ID);
        DatagramPacket init_packet = new DatagramPacket(init_pkt.getUDPdata(), init_pkt.getUDPdata().length, address, nsePort);
        receiveSocket.send(init_packet);

        // receive circuit_DB from network state emulator, then store data in members for easy access
        byte[] circuitData = new byte[1024];
        DatagramPacket tempPacket = new DatagramPacket(circuitData,circuitData.length);
        receiveSocket.receive(tempPacket); // receive data into bye array
        circuit_DB circuit = circuit_DB.circuit_parseUDPdata(circuitData);

        numLinks = circuit.nbr_link;
        routerLinks = circuit.linkcost;
        log_writer.write("Router " + stringID + " received circuit_DB with numLinks: " + Integer.toString(numLinks));
        log_writer.newLine();
        for (int i = 0; i<numLinks; i++){
            newEdges.put(routerLinks[i], ID);
        }
        
        // logging initial topology/rib
        String [] r_db = new String[5];
        Arrays.fill(r_db, "");
        int [] r_db_numlinks = new int[5];
        Arrays.fill(r_db_numlinks, 0);
        String starter = "R" + stringID + " -> ";

        for (link_cost l: newEdges.keySet()){
            int db_router = newEdges.get(l);
            int db_link = l.getLink();
            int db_cost = l.getCost();
            r_db_numlinks[db_router-1]++;
            r_db[db_router-1] += starter;
            r_db[db_router-1] += ("R" + Integer.toString(db_router) + " link " + Integer.toString(db_link) + " cost " + Integer.toString(db_cost));
            r_db[db_router-1] += "\n";
        }

        log_writer.newLine();
        log_writer.write("# Topology database");
        log_writer.newLine();
        for (int i = 0; i<5; i++){
            if (r_db_numlinks[i] != 0){
                log_writer.write(starter + "R" + Integer.toString(i+1) + " nbr link " + Integer.toString(r_db_numlinks[i]));
                log_writer.newLine();
                log_writer.write(r_db[i]);
            }
        }
        log_writer.newLine();
        
        log_writer.write("# RIB");
        log_writer.newLine();
        for (int i = 0; i<5; i++){
            String router_from = "R" + stringID;
            String router_to = "R" + Integer.toString(i+1);
            String dname = "";
            String dcost = "";
            if (D_names == null || D_names.get(i) == Integer.MAX_VALUE){
                dname = "INF";
            } else {
                dname = "R" + Integer.toString(D_names.get(i));
            }
            if (D_costs == null || D_costs.get(i) == Integer.MAX_VALUE){
                dcost = "INF";
            } else {
                dcost = Integer.toString(D_costs.get(i));
            }
            if (ID == (i+1)){
                log_writer.write(router_from + " -> " + router_to + " -> Local, 0");
            } else {
                log_writer.write(router_from + " -> " + router_to + " -> " + dname + ", " + dcost);
            }
            log_writer.newLine();
        }
        log_writer.newLine();

        // sending hello packets to router's neighbours
        for (int i = 0; i<numLinks; i++){
            pkt_HELLO hello_pkt = new pkt_HELLO(ID, routerLinks[i].getLink());
            byte[] hello_message = hello_pkt.getUDPdata();
            DatagramPacket hello_packet = new DatagramPacket(hello_message, hello_message.length, address, nsePort);
            receiveSocket.send(hello_packet);
            log_writer.write("R" + stringID + " sends a HELLO: ID " + stringID +
                              ", link_id " + Integer.toString(routerLinks[i].getLink()));
            log_writer.newLine();
        }

        // receiving LS_PDUs & HELLOs from neighbours
        while(true) {
            try {
                circuitData = new byte[1024];
                DatagramPacket receivePacket = new DatagramPacket(circuitData, circuitData.length);
                receiveSocket.setSoTimeout(2000);
                receiveSocket.receive(receivePacket);
                ByteBuffer buffer = ByteBuffer.wrap(circuitData);
                buffer.order(ByteOrder.LITTLE_ENDIAN);
                int int1 = buffer.getInt();
                int int2 = buffer.getInt();
                int int3 = buffer.getInt();
                int int4 = buffer.getInt();
                int int5 = buffer.getInt();

                // checking if packet is hello packet or lspdu packet
                if (int3 == int4 && int4 == int5 && int5 == 0) { // hello packet: send lspdu packets back to sender of hello packet
                    pkt_HELLO rec_hello = pkt_HELLO.hello_parseUDPdata(circuitData);
                    log_writer.write("R" + stringID + " receives a HELLO: ID " + Integer.toString(rec_hello.getRouter_id()) +
                                      ", link_id " + Integer.toString(rec_hello.getLink_id()));
                    log_writer.newLine();
                    rec_hello_links.add(rec_hello.getLink_id());
                    for (link_cost l : newEdges.keySet()) {
                        int router = floating_edges_retreive(newEdges, l);
                        int link = l.getLink();
                        int cost = l.getCost();
                        pkt_LSPDU hello_response = new pkt_LSPDU(ID, router, link, cost, rec_hello.getLink_id());
                        byte[] hello_res = hello_response.getUDPdata();
                        DatagramPacket hello_response_pkt = new DatagramPacket(hello_res, hello_res.length, address, nsePort);
                        receiveSocket.send(hello_response_pkt);
                        log_writer.write("R" + stringID + " sends an LS PDU: sender " + stringID +
                                          ", ID " + Integer.toString(router) + ", link_id " + Integer.toString(link) +
                                          ", cost " + Integer.toString(cost) + ", via " + Integer.toString(rec_hello.getLink_id()));
                        log_writer.newLine();
                    }
                } else if (int3 != 0 || int4 != 0 || int5 != 0) { // lspdu packet: update database, forward to neighbours
                    pkt_LSPDU rec_lspdu = pkt_LSPDU.lspdu_parseUDPdata(circuitData);
                    log_writer.write("R" + stringID + " receives an LS PDU: sender " + Integer.toString(rec_lspdu.getSender()) +
                                      ", ID " + Integer.toString(rec_lspdu.getRouter_id()) + ", link_id " + Integer.toString(rec_lspdu.getLink_id()) +
                                      ", cost " + Integer.toString(rec_lspdu.getCost()) + ", via " + Integer.toString(rec_lspdu.getVia()));
                    log_writer.newLine();
                    int link = rec_lspdu.getLink_id();
                    int cost = rec_lspdu.getCost();
                    link_cost linkcost = new link_cost(link, cost);
                    int via = rec_lspdu.getVia();

                    // check if this link is already complete in router's database
                    if (!complete_edges_contains(finishedEdges, linkcost)) { // not a complete edge
                        if (!floating_edges_contains(newEdges, linkcost)) { // adding edge to floating edge list
                            newEdges.put(linkcost, rec_lspdu.getRouter_id());
                        } else if (floating_edges_contains(newEdges, linkcost) &&
                                (floating_edges_retreive(newEdges, linkcost) != rec_lspdu.getRouter_id())) { // adding edge to complete edge list
                            edge routers = new edge(rec_lspdu.getRouter_id(), floating_edges_retreive(newEdges, linkcost));
                            newEdges.put(linkcost, rec_lspdu.getRouter_id());
                            finishedEdges.put(linkcost, routers);

                            // Dijkstra's algorithm to compute shortest paths with addition of new edge
                            inTree = new ArrayList<Integer>();
                            D_costs = new ArrayList<Integer>(5);
                            D_names = new ArrayList<Integer>(5);

                            for (int i = 0; i < 5; i++) {
                                D_costs.add(Integer.MAX_VALUE);
                                D_names.add(Integer.MAX_VALUE);
                            }
                            inTree.add(ID);
                            for (link_cost l : finishedEdges.keySet()) {
                                if (finishedEdges.get(l).router1 == ID) {
                                    D_costs.set(finishedEdges.get(l).router2 - 1, l.getCost());
                                    D_names.set(finishedEdges.get(l).router2 - 1, finishedEdges.get(l).router2);
                                } else if (finishedEdges.get(l).router2 == ID) {
                                    D_costs.set(finishedEdges.get(l).router1 - 1, l.getCost());
                                    D_names.set(finishedEdges.get(l).router1 - 1, finishedEdges.get(l).router1);
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
                                    if (D_costs.get(i) < min) {
                                        min_index = i;
                                        min = D_costs.get(i);
                                    }
                                }
                                inTree.add(min_index + 1); // adding router number to tree (1-5)
                                for (link_cost l : finishedEdges.keySet()) {
                                    if (finishedEdges.get(l).router1 == (min_index + 1)) {
                                        int router2 = finishedEdges.get(l).router2;
                                        if (inTree.contains(router2)) continue;
                                        if (D_costs.get(min_index) + l.getCost() < 0) continue;
                                        if (D_costs.get(router2 - 1) > D_costs.get(min_index) + l.getCost()) {
                                            D_costs.set(router2 - 1, D_costs.get(min_index) + l.getCost());
                                            D_names.set(router2 - 1, D_names.get(min_index));
                                        }
                                    } else if (finishedEdges.get(l).router2 == (min_index + 1)) {
                                        int router2 = finishedEdges.get(l).router1;
                                        if (inTree.contains(router2)) continue;
                                        if (D_costs.get(min_index) + l.getCost() < 0) continue;
                                        if (D_costs.get(router2 - 1) > D_costs.get(min_index) + l.getCost()) {
                                            D_costs.set(router2 - 1, D_costs.get(min_index) + l.getCost());
                                            D_names.set(router2 - 1, D_names.get(min_index));
                                        }
                                    }
                                }
                            }
                        } else{ // no changes to topology database
                            continue;
                        }
                        r_db = new String[5];
                        r_db_numlinks = new int[5];
                        for (int i = 0; i<5; i++){
                            r_db[i] = "";
                            r_db_numlinks[i] = 0;
                        }
                        for (link_cost l: newEdges.keySet()){
                            int db_router = newEdges.get(l);
                            int db_link = l.getLink();
                            int db_cost = l.getCost();
                            r_db_numlinks[db_router-1]++;
                            r_db[db_router-1] += starter;
                            r_db[db_router-1] += ("R" + Integer.toString(db_router) + " link " + Integer.toString(db_link) + " cost " + Integer.toString(db_cost));
                            r_db[db_router-1] += "\n";
                        }

                        // logging new topology database
                        log_writer.newLine();
                        log_writer.write("# Topology database");
                        log_writer.newLine();
                        for (int i = 0; i<5; i++){
                            if (r_db_numlinks[i] != 0){
                                log_writer.write(starter + "R" + Integer.toString(i+1) + " nbr link " + Integer.toString(r_db_numlinks[i]));
                                log_writer.newLine();
                                log_writer.write(r_db[i]);
                            }
                        }
                        log_writer.newLine();

                        // logging new rib
                        log_writer.write("# RIB");
                        log_writer.newLine();
                        for (int i = 0; i<5; i++){
                            String router_from = "R" + stringID;
                            String router_to = "R" + Integer.toString(i+1);
                            String dname = "";
                            String dcost = "";
                            if (D_names == null || D_names.get(i) == Integer.MAX_VALUE){
                                dname = "INF";
                            } else {
                                dname = "R" + Integer.toString(D_names.get(i));
                            }
                            if (D_costs == null || D_costs.get(i) == Integer.MAX_VALUE){
                                dcost = "INF";
                            } else {
                                dcost = Integer.toString(D_costs.get(i));
                            }
                            if (ID == (i+1)){
                                log_writer.write(router_from + " -> " + router_to + " -> Local, 0");
                            } else {
                                log_writer.write(router_from + " -> " + router_to + " -> " + dname + ", " + dcost);
                            }
                            log_writer.newLine();
                        }
                        log_writer.newLine();
                    }

                    // forwarding ls_pdu to all neighbours
                    for (int i = 0; i < numLinks; i++) {
                        if (routerLinks[i].getLink() == via || !(rec_hello_links.contains(routerLinks[i].getLink())))
                            continue;
                        pkt_LSPDU forward_pkt = new pkt_LSPDU(ID, rec_lspdu.getRouter_id(), link, cost, routerLinks[i].getLink());
                        if (check_lspdu(sent_lspdu, forward_pkt)) continue;
                        else sent_lspdu.add(forward_pkt);
                        byte[] forward_message = forward_pkt.getUDPdata();
                        DatagramPacket forward_packet = new DatagramPacket(forward_message, forward_message.length, address, nsePort);
                        receiveSocket.send(forward_packet);
                        log_writer.write("R" + stringID + " sends an LS PDU: sender " + stringID +
                                ", ID " + Integer.toString(rec_lspdu.getRouter_id()) + ", link_id " + Integer.toString(link) +
                                ", cost " + Integer.toString(cost) + ", via " + Integer.toString(routerLinks[i].getLink()));
                        log_writer.newLine();
                    }
                }
            } catch (SocketTimeoutException e){
                break;
            }
        }
        log_writer.close();
    }
}


