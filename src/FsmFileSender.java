import java.io.*;
import java.net.*;
import java.util.zip.CRC32;
import java.util.zip.Checksum;


class FsmFileSender implements Runnable {

    private final static int PORT = 10_000;

    // all states for this FSM
    enum State {
        WAIT_0, WAIT_FOR_ACK0, WAIT_1, WAIT_FOR_ACK1
    }

    // all messages/conditions which can occur
    enum Msg {
        SEND_PKT, WAIT_FOR_DATA, CORRECT_PACKET, BROKEN_PACKET, TIMEOUT
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


        transition[State.WAIT_0.ordinal()][Msg.SEND_PKT.ordinal()] = new SendPkt();
        transition[State.WAIT_1.ordinal()][Msg.SEND_PKT.ordinal()] = new SendPkt();

        System.out.println("INFO FSM constructed, current state: " + currentState);
    }

    @Override
    public void run() {
        File file = new File("C:/Users/soich/IdeaProjects/UDP_Datatransfer/src/" + dataName + ".txt");
        ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(byteOut);
        byte[] data = new byte[1200];
        byte[] receiveData = new byte[15];
        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
        DatagramPacket sendPacket = null;
        boolean nameNotSend = true;

        try {
            DatagramSocket socket = new DatagramSocket();
            FileInputStream fis = new FileInputStream(file);
            BufferedInputStream bis = new BufferedInputStream(fis);
            InetAddress ia = InetAddress.getLocalHost();
            while (true) {
                if (currentState.equals(State.WAIT_0) || currentState.equals(State.WAIT_1)) {
                    if (nameNotSend) {
                        System.out.println("First send the Name");
                        Checksum checksum = new CRC32();
                        byte[] nameByte = new byte[1200];
                        int n = dataName.length();
                        int nameByteLength = 0;
                        for(int i = 0; i<n;i++){
                            char c = dataName.charAt(i);
                            nameByte[i] = (byte) c;
                            nameByteLength++;
                            System.out.println(nameByte[i]);
                        }




                        checksum.update(nameByte, 0, nameByteLength);
                        int checksumValue = (int) checksum.getValue();

                        System.out.println(checksumValue);

                        out.write(0);
                        out.writeLong(checksum.getValue());
                        out.writeInt(nameByteLength);
                        out.write(nameByte);

                        byte packetData[] = byteOut.toByteArray();
                        System.out.println(packetData.length);
                        sendPacket = new DatagramPacket(packetData, packetData.length, ia, PORT);
                        nameNotSend = false;
                    } else {
                        int bytesAmount = 0;
                        if ((bytesAmount = bis.read(data, 0, 1200)) > 0) {

                            byte packetData[] = buildPacket(currentState, data, bytesAmount);
                            sendPacket = new DatagramPacket(packetData, packetData.length);
                        }
                    }
                    processMsg(Msg.SEND_PKT, sendPacket, socket);
                    socket.setSoTimeout(10_000);
                } else if (currentState.equals(State.WAIT_FOR_ACK0) || currentState.equals(State.WAIT_FOR_ACK1)) {

                    byte[] pkt = new byte[9];
                    DatagramSocket receiverSocket = new DatagramSocket(9000);
                    receiverSocket.setSoTimeout(10_000);
                    DatagramPacket ackpkt = new DatagramPacket(pkt, pkt.length);
                    System.out.println("Waiting for ACK...");

                    receiverSocket.receive(ackpkt);
                    System.out.println("received ack");
                }
            }

        } catch (SocketException e) {
            e.printStackTrace();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }


    }


    /**
     * Process a message (a condition has occurred).
     *
     * @param input Message or condition that has occurred.
     */
    public void processMsg(Msg input, DatagramPacket packet, DatagramSocket socket) {
        System.out.println("INFO Received " + input + " in state " + currentState);
        Transition trans = transition[currentState.ordinal()][input.ordinal()];
        if (trans != null) {
            currentState = trans.execute(input, packet, socket);
        }
        System.out.println("INFO State: " + currentState);
    }

    /**
     * Abstract base class for all transitions.
     * Derived classes need to override execute thereby defining the action
     * to be performed whenever this transition occurs.
     */
    abstract class Transition {
        abstract public State execute(Msg input, DatagramPacket packet, DatagramSocket socket);
    }

    class SendPkt extends Transition {
        @Override
        public State execute(Msg input, DatagramPacket packet, DatagramSocket socket) {
            System.out.println("schicken wir was raus?");
            try {

                socket.send(packet);

            } catch (IOException e) {
                e.printStackTrace();
            }
            if (currentState == State.WAIT_0) {
                System.out.println("Packet send from wait 0");
                return State.WAIT_FOR_ACK0;
            } else if (currentState == State.WAIT_FOR_ACK0) {
                System.out.println("Wait in Ack 0");
                return currentState;
            } else if (currentState == State.WAIT_1) {
                System.out.println("Packet send from wait 1");
                return State.WAIT_FOR_ACK1;
            } else {
                System.out.println("Wait in Ack 1");
                return currentState;
            }


        }
    }

    public byte[] buildPacket(State currentState, byte[] data, int bytesAmount) throws IOException {
        ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(byteOut);
        Checksum checksum = new CRC32();
        checksum.update(data, 0, bytesAmount);
        int checksumValue = (int) checksum.getValue();

        if (currentState == State.WAIT_0) {
            out.write(0);
        } else if (currentState == State.WAIT_1) {
            out.write(1);
        }

        out.write(checksumValue);
        out.write(bytesAmount);
        out.write(data);

        byte packetData[] = byteOut.toByteArray();

        return packetData;
    }

    public boolean checkReceivePacket(DatagramPacket receivePacket){
        boolean isOkay = false;

        return isOkay;
    }
}


