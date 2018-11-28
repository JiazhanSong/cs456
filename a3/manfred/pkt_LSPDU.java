import java.nio.ByteOrder;
import java.nio.ByteBuffer;

// lspdu data class
public class pkt_LSPDU {
    public int sender;
    public int router_id;
    public int link_id;
    public int cost;
    public int via;

    public pkt_LSPDU(int send, int router, int link, int input_cost, int input_via){
        sender = send;
        router_id = router;
        link_id = link;
        cost = input_cost;
        via = input_via;
    }

    public int getSender(){
        return sender;
    }

    public int getRouter_id(){
        return router_id;
    }

    public int getLink_id(){
        return link_id;
    }

    public int getCost(){
        return cost;
    }

    public int getVia(){
        return via;
    }

    public byte[] getUDPdata() {
        ByteBuffer buffer = ByteBuffer.allocate(20);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(sender);
        buffer.putInt(router_id);
        buffer.putInt(link_id);
        buffer.putInt(cost);
        buffer.putInt(via);
        return buffer.array();
    }

    public static pkt_LSPDU lspdu_parseUDPdata(byte[] UDPdata) throws Exception {
        ByteBuffer buffer = ByteBuffer.wrap(UDPdata);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        int sender = buffer.getInt();
        int router_id = buffer.getInt();
        int link_id = buffer.getInt();
        int cost = buffer.getInt();
        int via = buffer.getInt();
        return new pkt_LSPDU(sender, router_id, link_id, cost, via);
    }
}

