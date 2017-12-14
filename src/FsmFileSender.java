import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

class FsmFileSender {

    // all states for this FSM
    enum State {
        WAIT_0, WAIT_FOR_ACK0, WAIT_1, WAIT_FOR_ACK1
    }

    // all messages/conditions which can occur
    enum Msg {
        SEND_PKT, WAIT_FOR_DATA
    }

    // current state of the FSM
    public State currentState;
    // 2D array defining all transitions that can occur
    private Transition[][] transition;

    String receiverName;
    String dataName;

    FsmFileSender(String dataName, String receiverName) throws SocketException {
        this.dataName = dataName;
        this.receiverName = receiverName;
        currentState = State.WAIT_0;
        // define all valid state transitions for our state machine
        // (undefined transitions will be ignored)
        transition = new Transition[State.values().length][Msg.values().length];
        transition[State.WAIT_0.ordinal()][Msg.SEND_PKT.ordinal()] = new SendPkt_0();
        transition[State.WAIT_0.ordinal()][Msg.WAIT_FOR_DATA.ordinal()] = new WaitForData_0();
        transition[State.WAIT_1.ordinal()][Msg.WAIT_FOR_DATA.ordinal()] = new WaitForData_1();

                System.out.println("INFO FSM constructed, current state: " + currentState);
    }

    /**
     * Process a message (a condition has occurred).
     *
     * @param input Message or condition that has occurred.
     */
    public void processMsg(Msg input, byte[] data, DatagramSocket socket) {
        System.out.println("INFO Received " + input + " in state " + currentState);
        Transition trans = transition[currentState.ordinal()][input.ordinal()];
        if (trans != null) {
            currentState = trans.execute(input, data, socket);
        }
        System.out.println("INFO State: " + currentState);
    }

    /**
     * Abstract base class for all transitions.
     * Derived classes need to override execute thereby defining the action
     * to be performed whenever this transition occurs.
     */
    abstract class Transition {
        abstract public State execute(Msg input, byte[] data, DatagramSocket socket);
    }

    class SendPkt_0 extends Transition {
        private final static int PORT = 10000;
        ByteArrayOutputStream bOut = new ByteArrayOutputStream();

        @Override
        public State execute(Msg input, byte[] data, DatagramSocket socket) {

            Checksum checksum = new CRC32();
            checksum.update(data, 0, data.length);
            int checksumValue = (int) checksum.getValue();
            System.out.println(checksumValue);
            try {

                bOut.write(0);
                bOut.write(checksumValue);
                bOut.write(data);

                byte packetData[] = bOut.toByteArray();
                DatagramPacket sendPacket = new DatagramPacket(packetData, packetData.length);

                //socket.send(sendPacket);
            } catch (SocketException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

            System.out.println("Packet is sended");
            return State.WAIT_FOR_ACK0;
        }
    }

    class SendPkt_1 extends Transition {
        private final static int PORT = 10000;
        ByteArrayOutputStream bOut = new ByteArrayOutputStream();

        @Override
        public State execute(Msg input, byte[] data, DatagramSocket socket) {
            Checksum checksum = new CRC32();
            checksum.update(data, 0, data.length);
            int checksumValue = (int) checksum.getValue();
            System.out.println(checksumValue);
            try {

                bOut.write(1);
                bOut.write(checksumValue);
                bOut.write(data);

                byte packetData[] = bOut.toByteArray();
                DatagramPacket sendPacket = new DatagramPacket(packetData, packetData.length);

                socket.send(sendPacket);
            } catch (SocketException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

            System.out.println("Packet is sended");
            return State.WAIT_FOR_ACK0;
        }


    }

    class WaitForData_0 extends Transition {

        @Override
        public State execute(Msg input, byte[] data, DatagramSocket socket) {
            System.out.println("There is no data to send");
            return State.WAIT_0;
        }
    }

    class WaitForData_1 extends Transition {

        @Override
        public State execute(Msg input, byte[] data, DatagramSocket socket) {
            System.out.println("There is no data to send");
            return State.WAIT_1;
        }
    }


}
