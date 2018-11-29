// data structure for lspdu
public class LSPDU {
    public int sender;
    public int router_id;
    public int link_id;
    public int cost;
    public int via;

    public LSPDU(int send, int router, int link, int input_cost, int input_via){
        sender = send;
        router_id = router;
        link_id = link;
        cost = input_cost;
        via = input_via;
    }
}

