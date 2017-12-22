import java.net.SocketException;

public class Main {

    public static void main(String[] args) throws SocketException {
        new Thread(new FsmFileSender("sendTest","localhost")).start();
    }
}
