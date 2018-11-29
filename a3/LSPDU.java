// data structure for lspdu
public class LSPDU {    
    public int sender;
    public int router_id;
    public int link_id;
    public int cost;
    public int via;

    public LSPDU(int s, int r, int l, int i, int v){
        sender = s;     router_id = r;
        link_id = l;    cost = i;
        via = v;
    }
}