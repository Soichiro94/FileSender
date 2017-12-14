import java.net.DatagramSocket;
import java.net.SocketException;

public class Main {

    private final static int PORT = 10000;

    public static void main(String[] args) {
        byte[] data = new byte[0];

        try {
            FsmFileSender sender = new FsmFileSender("hi", "hello");
            DatagramSocket socket = new DatagramSocket(PORT);

            if (sender.currentState.equals(FsmFileSender.State.WAIT_0) || sender.currentState.equals(FsmFileSender.State.WAIT_1)) {
                if (data.length == 0) {
                    sender.processMsg(FsmFileSender.Msg.WAIT_FOR_DATA, data, socket);
                } else {
                    sender.processMsg(FsmFileSender.Msg.SEND_PKT, data, socket);
                }
            }


        } catch (SocketException e) {
            e.printStackTrace();
        }
    }
}
