import java.nio.*;

public class pkt_HELLO {

    int router_id;
    int link_id;

    public pkt_HELLO(int router, int link){
        router_id = router;
        link_id = link;
    }

    public int getRouter_id(){
        return router_id;
    }

    public int getLink_id(){
        return link_id;
    }

    public byte[] getUDPdata() {
        ByteBuffer buffer = ByteBuffer.allocate(8);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(router_id);
        buffer.putInt(link_id);
        return buffer.array();
    }

    public static pkt_HELLO hello_parseUDPdata(byte[] UDPdata) throws Exception {
        ByteBuffer buffer = ByteBuffer.wrap(UDPdata);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        int router = buffer.getInt();
        int link = buffer.getInt();
        return new pkt_HELLO(router, link);
    }
}

