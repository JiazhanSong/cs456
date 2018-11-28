import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class pkt_INIT {

    int router_id;

    public pkt_INIT(int router){
        router_id = router;
    }

    public int getRouter_id(){
        return router_id;
    }

    public byte[] getUDPdata() {
        ByteBuffer buffer = ByteBuffer.allocate(4);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(router_id);
        return buffer.array();
    }

    public static pkt_INIT init_parseUDPdata(byte[] UDPdata) throws Exception {
        ByteBuffer buffer = ByteBuffer.wrap(UDPdata);
        buffer.order(ByteOrder.LITTLE_ENDIAN); // default is big endian
        int router = buffer.getInt();
        return new pkt_INIT(router);
    }
}

