import java.nio.*;

// data structure for lspdu
public class pktLSPDU {
    public int sender;
    public int router_id;
    public int link_id;
    public int cost;
    public int via;

    public pktLSPDU(int send, int router, int link, int input_cost, int input_via){
        sender = send;
        router_id = router;
        link_id = link;
        cost = input_cost;
        via = input_via;
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

    public static pktLSPDU lspdu_parseUDPdata(byte[] UDPdata) throws Exception {
        ByteBuffer buffer = ByteBuffer.wrap(UDPdata);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        int sender = buffer.getInt();
        int router_id = buffer.getInt();
        int link_id = buffer.getInt();
        int cost = buffer.getInt();
        int via = buffer.getInt();
        return new pktLSPDU(sender, router_id, link_id, cost, via);
    }
}

