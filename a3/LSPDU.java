// data structure for lspdu
public class LSPDU {    
    public int sender;      /* sender of the LS PDU */
    public int router_id;   /* router id */
    public int link_id;     /* link id */
    public int cost;        /* cost of the link */
    public int via;         /* id of the link through which the LS PDU is sent */

    public LSPDU(int s, int r, int l, int i, int v){
        sender = s;     router_id = r;
        link_id = l;    cost = i;
        via = v;
    }
}