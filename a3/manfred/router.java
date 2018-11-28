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
        // reading in command line arguements
        int router_id = Integer.parseInt(args[0]);
        String hostname = args[1];
        int nse_port = Integer.parseInt(args[2]);
        int router_port = Integer.parseInt(args[3]);

        // clearing old log files
        String filename = "router" + Integer.toString(router_id) + ".log";
        Path currentRelativePath = Paths.get("");
        String cur_dir = currentRelativePath.toAbsolutePath().toString();
        String log_path = cur_dir + "/" + filename;
        File log = new File(log_path);
        log.delete();

        // general set up
        BufferedWriter log_writer = new BufferedWriter(new FileWriter(filename, true));
        Map<link_cost,Integer> floating_edges= new HashMap<link_cost,Integer>();
        Map<link_cost, edge> complete_edges = new HashMap<link_cost, edge>();
        ArrayList<Integer> rec_hello_links = new ArrayList<Integer>();
        ArrayList<pkt_LSPDU> sent_lspdu = new ArrayList<pkt_LSPDU>();
        ArrayList<Integer> inTree = null;
        ArrayList<Integer> D_costs = null;
        ArrayList<Integer> D_names = null;

        // setting up address and receive socket
        InetAddress address = InetAddress.getByName(hostname);
        DatagramSocket receiveSocket = new DatagramSocket(router_port);

        // sending init packet to nse
        pkt_INIT init_pkt = new pkt_INIT(router_id);
        byte[] init_message = init_pkt.getUDPdata();
        DatagramPacket init_packet = new DatagramPacket(init_message, init_message.length, address, nse_port);
        receiveSocket.send(init_packet);
        log_writer.write("R" + Integer.toString(router_id) + " sends an INIT: router_id " + Integer.toString(router_id));
        log_writer.newLine();

        // waiting to receive circuit_db from nse
        byte[] receiveData = new byte[1024];
        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
        receiveSocket.receive(receivePacket);
        circuit_DB circuit = circuit_DB.circuit_parseUDPdata(receiveData);

        // adding circuit_db data to local router's database
        link_cost [] local_linkcosts = circuit.getLinkcost();
        int numlinks = circuit.getNum_links();
        log_writer.write("R" + Integer.toString(router_id) + " receives a CIRCUIT_DB: nbr_link " + Integer.toString(numlinks));
        log_writer.newLine();
        for (int i = 0; i<numlinks; i++){
            floating_edges.put(local_linkcosts[i], router_id);
        }
        
        // logging initial topology/rib
        String [] r_db = new String[5];
        int [] r_db_numlinks = new int[5];
        String starter = "R" + Integer.toString(router_id) + " -> ";
        for (int i = 0; i<5; i++){
            r_db[i] = "";
            r_db_numlinks[i] = 0;
        }
        for (link_cost l: floating_edges.keySet()){
            int db_router = floating_edges.get(l);
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
            String router_from = "R" + Integer.toString(router_id);
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
            if (router_id == (i+1)){
                log_writer.write(router_from + " -> " + router_to + " -> Local, 0");
            } else {
                log_writer.write(router_from + " -> " + router_to + " -> " + dname + ", " + dcost);
            }
            log_writer.newLine();
        }
        log_writer.newLine();

        // sending hello packets to router's neighbours
        for (int i = 0; i<numlinks; i++){
            pkt_HELLO hello_pkt = new pkt_HELLO(router_id, local_linkcosts[i].getLink());
            byte[] hello_message = hello_pkt.getUDPdata();
            DatagramPacket hello_packet = new DatagramPacket(hello_message, hello_message.length, address, nse_port);
            receiveSocket.send(hello_packet);
            log_writer.write("R" + Integer.toString(router_id) + " sends a HELLO: router_id " + Integer.toString(router_id) +
                              ", link_id " + Integer.toString(local_linkcosts[i].getLink()));
            log_writer.newLine();
        }

        // receiving LS_PDUs & HELLOs from neighbours
        while(true) {
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
                if (int3 == int4 && int4 == int5 && int5 == 0) { // hello packet: send lspdu packets back to sender of hello packet
                    pkt_HELLO rec_hello = pkt_HELLO.hello_parseUDPdata(receiveData);
                    log_writer.write("R" + Integer.toString(router_id) + " receives a HELLO: router_id " + Integer.toString(rec_hello.getRouter_id()) +
                                      ", link_id " + Integer.toString(rec_hello.getLink_id()));
                    log_writer.newLine();
                    rec_hello_links.add(rec_hello.getLink_id());
                    for (link_cost l : floating_edges.keySet()) {
                        int router = floating_edges_retreive(floating_edges, l);
                        int link = l.getLink();
                        int cost = l.getCost();
                        pkt_LSPDU hello_response = new pkt_LSPDU(router_id, router, link, cost, rec_hello.getLink_id());
                        byte[] hello_res = hello_response.getUDPdata();
                        DatagramPacket hello_response_pkt = new DatagramPacket(hello_res, hello_res.length, address, nse_port);
                        receiveSocket.send(hello_response_pkt);
                        log_writer.write("R" + Integer.toString(router_id) + " sends an LS PDU: sender " + Integer.toString(router_id) +
                                          ", router_id " + Integer.toString(router) + ", link_id " + Integer.toString(link) +
                                          ", cost " + Integer.toString(cost) + ", via " + Integer.toString(rec_hello.getLink_id()));
                        log_writer.newLine();
                    }
                } else if (int3 != 0 || int4 != 0 || int5 != 0) { // lspdu packet: update database, forward to neighbours
                    pkt_LSPDU rec_lspdu = pkt_LSPDU.lspdu_parseUDPdata(receiveData);
                    log_writer.write("R" + Integer.toString(router_id) + " receives an LS PDU: sender " + Integer.toString(rec_lspdu.getSender()) +
                                      ", router_id " + Integer.toString(rec_lspdu.getRouter_id()) + ", link_id " + Integer.toString(rec_lspdu.getLink_id()) +
                                      ", cost " + Integer.toString(rec_lspdu.getCost()) + ", via " + Integer.toString(rec_lspdu.getVia()));
                    log_writer.newLine();
                    int link = rec_lspdu.getLink_id();
                    int cost = rec_lspdu.getCost();
                    link_cost linkcost = new link_cost(link, cost);
                    int via = rec_lspdu.getVia();

                    // check if this link is already complete in router's database
                    if (!complete_edges_contains(complete_edges, linkcost)) { // not a complete edge
                        if (!floating_edges_contains(floating_edges, linkcost)) { // adding edge to floating edge list
                            floating_edges.put(linkcost, rec_lspdu.getRouter_id());
                        } else if (floating_edges_contains(floating_edges, linkcost) &&
                                (floating_edges_retreive(floating_edges, linkcost) != rec_lspdu.getRouter_id())) { // adding edge to complete edge list
                            edge routers = new edge(rec_lspdu.getRouter_id(), floating_edges_retreive(floating_edges, linkcost));
                            floating_edges.put(linkcost, rec_lspdu.getRouter_id());
                            complete_edges.put(linkcost, routers);

                            // Dijkstra's algorithm to compute shortest paths with addition of new edge
                            inTree = new ArrayList<Integer>();
                            D_costs = new ArrayList<Integer>(5);
                            D_names = new ArrayList<Integer>(5);

                            for (int i = 0; i < 5; i++) {
                                D_costs.add(Integer.MAX_VALUE);
                                D_names.add(Integer.MAX_VALUE);
                            }
                            inTree.add(router_id);
                            for (link_cost l : complete_edges.keySet()) {
                                if (complete_edges.get(l).getR1() == router_id) {
                                    D_costs.set(complete_edges.get(l).getR2() - 1, l.getCost());
                                    D_names.set(complete_edges.get(l).getR2() - 1, complete_edges.get(l).getR2());
                                } else if (complete_edges.get(l).getR2() == router_id) {
                                    D_costs.set(complete_edges.get(l).getR1() - 1, l.getCost());
                                    D_names.set(complete_edges.get(l).getR1() - 1, complete_edges.get(l).getR1());
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
                                for (link_cost l : complete_edges.keySet()) {
                                    if (complete_edges.get(l).getR1() == (min_index + 1)) {
                                        int router2 = complete_edges.get(l).getR2();
                                        if (inTree.contains(router2)) continue;
                                        if (D_costs.get(min_index) + l.getCost() < 0) continue;
                                        if (D_costs.get(router2 - 1) > D_costs.get(min_index) + l.getCost()) {
                                            D_costs.set(router2 - 1, D_costs.get(min_index) + l.getCost());
                                            D_names.set(router2 - 1, D_names.get(min_index));
                                        }
                                    } else if (complete_edges.get(l).getR2() == (min_index + 1)) {
                                        int router2 = complete_edges.get(l).getR1();
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
                        for (link_cost l: floating_edges.keySet()){
                            int db_router = floating_edges.get(l);
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
                            String router_from = "R" + Integer.toString(router_id);
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
                            if (router_id == (i+1)){
                                log_writer.write(router_from + " -> " + router_to + " -> Local, 0");
                            } else {
                                log_writer.write(router_from + " -> " + router_to + " -> " + dname + ", " + dcost);
                            }
                            log_writer.newLine();
                        }
                        log_writer.newLine();
                    }

                    // forwarding ls_pdu to all neighbours
                    for (int i = 0; i < numlinks; i++) {
                        if (local_linkcosts[i].getLink() == via || !(rec_hello_links.contains(local_linkcosts[i].getLink())))
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
                }
            } catch (SocketTimeoutException e){
                break;
            }
        }
        log_writer.close();
    }
}

