
public class Main {
    public static void main(String[] args) {

        FsmFileSender sender = new FsmFileSender();
        sender.processMsg(FsmFileSender.Msg.SEND_PKT);

        System.out.println("ist hier noch alles da ");

    }
}
