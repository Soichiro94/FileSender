/**
 * Created by soich on 07.12.2017.
 */
public class Main {
    public static void main(String[] args) {

        FsmFileSender sender = new FsmFileSender();
        sender.processMsg(FsmFileSender.Msg.SEND_PKT);

    }
}
