import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class circuit_DB {

    public int nbr_link; /* number of links attached to a router */
    public link_cost linkcost[];

    public circuit_DB(int links, link_cost[] linkcosts){
        nbr_link = links;
        linkcost = new link_cost[5];
        for (int i = 0; i<links; i++) {
            int link = linkcosts[i].getLink();
            int cost = linkcosts[i].getCost();
            linkcost[i] = new link_cost(link, cost);
        }
    }

    public static circuit_DB circuit_parseUDPdata(byte[] UDPdata) throws Exception {
        ByteBuffer buffer = ByteBuffer.wrap(UDPdata);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        int links = buffer.getInt();
        link_cost[] linkcosts = new link_cost[5];
        int temp_link;
        int temp_cost;
        for (int i = 0; i<links; i++) {
            temp_link = buffer.getInt();
            temp_cost = buffer.getInt();
            linkcosts[i] = new link_cost(temp_link, temp_cost);
        }
        return new circuit_DB(links, linkcosts);
    }
}
