import java.net.SocketException;

public class Main {

    public static void main(String[] args) throws SocketException {

        /*
         The following test files can be used:
                -   test_text.txt
                -   test_image.jpg
                -   test_music.MP3
         */


        new Thread(new FsmFileSender("test_image.jpg","mark")).start();
    }
}
