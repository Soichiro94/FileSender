import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeoutException;
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

        transition[State.WAIT_FOR_ACK0.ordinal()][Msg.CORRECT_PACKET.ordinal()] = new StopTimer();
        transition[State.WAIT_FOR_ACK1.ordinal()][Msg.CORRECT_PACKET.ordinal()] = new StopTimer();

        transition[State.WAIT_FOR_ACK0.ordinal()][Msg.BROKEN_PACKET.ordinal()] = new Wait();
        transition[State.WAIT_FOR_ACK1.ordinal()][Msg.BROKEN_PACKET.ordinal()] = new Wait();

        transition[State.WAIT_FOR_ACK0.ordinal()][Msg.TIMEOUT.ordinal()] = new ResendPacket();
        transition[State.WAIT_FOR_ACK1.ordinal()][Msg.TIMEOUT.ordinal()] = new ResendPacket();

        System.out.println("INFO FSM constructed, current state: " + currentState);
    }

    @Override
    public void run() {

        File file = new File("C:/Users/soich/IdeaProjects/FileSender/src/" + dataName + ".txt");
        ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(byteOut);
        byte[] data = new byte[1200];
        DatagramPacket sendPacket = null;
        boolean nameNotSend = true;

        try {

            DatagramSocket receiverSocket = new DatagramSocket(9000);
            FileInputStream fis = new FileInputStream(file);
            BufferedInputStream bis = new BufferedInputStream(fis);
            InetAddress ia = InetAddress.getLocalHost();
            while (true) {
                DatagramSocket socket = new DatagramSocket();
                if (currentState.equals(State.WAIT_0) || currentState.equals(State.WAIT_1)) {
                    if (nameNotSend) {
                        System.out.println("First send the Name");
                        CRC32 checksum = new CRC32();
                        byte[] nameByte = new byte[1200];
                        int n = dataName.length();
                        int nameByteLength = 0;
                        for (int i = 0; i < n; i++) {
                            char c = dataName.charAt(i);
                            nameByte[i] = (byte) c;
                            nameByteLength++;

                        }
                        checksum.reset();
                        checksum.update(nameByte, 0, nameByteLength);
                        System.out.println(checksum.getValue());

                        out.write(0);
                        out.writeLong(checksum.getValue());
                        out.writeInt(nameByteLength);
                        out.write(nameByte);

                        byte packetData[] = byteOut.toByteArray();
                        sendPacket = new DatagramPacket(packetData, packetData.length, ia, PORT);
                        nameNotSend = false;
                    } else {
                        System.out.println("send data, no name");
                        int bytesAmount = 0;
                        if ((bytesAmount = bis.read(data, 0, 1200)) > 0) {
                            System.out.println("bauen ein neues paket");
                            byte packetData[] = buildPacket(currentState, data, bytesAmount);
                            sendPacket = new DatagramPacket(packetData, packetData.length, ia, PORT);

                        }
                    }
                    processMsg(Msg.SEND_PKT, sendPacket, socket);
                    socket.setSoTimeout(10_000);

                } else if (currentState.equals(State.WAIT_FOR_ACK0) || currentState.equals(State.WAIT_FOR_ACK1)) {
                    try {
                        // toDo so bauen das es die richtigen states erkennt
                        int number = 0;
                        boolean correct = false;
                        byte[] pkt = new byte[9];
                        receiverSocket.setSoTimeout(10_000);
                        DatagramPacket ackpkt = new DatagramPacket(pkt, pkt.length);
                        System.out.println("Waiting for ACK...");

                        receiverSocket.receive(ackpkt);
                        if (currentState.equals(State.WAIT_FOR_ACK0)) {
                            number = 0;
                        } else if (currentState.equals(State.WAIT_FOR_ACK1)) {
                            number = 1;
                        }
                        correct = extractAndCheck(pkt, ackpkt, number);
                        if (correct) {
                            System.out.println("das paket passt");
                            processMsg(Msg.CORRECT_PACKET, ackpkt, receiverSocket);
                        } else {
                            System.out.println("das paket passt nicht");
                            processMsg(Msg.BROKEN_PACKET, ackpkt, receiverSocket);
                        }
                    } catch (SocketTimeoutException ex) {
                        System.out.println("we caught a timeout");
                        processMsg(Msg.TIMEOUT, sendPacket, socket);
                    }
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
    public void processMsg(Msg input, DatagramPacket packet, DatagramSocket socket) throws SocketException {
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
        abstract public State execute(Msg input, DatagramPacket packet, DatagramSocket socket) throws SocketException;
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

    class StopTimer extends Transition {
        @Override
        public State execute(Msg input, DatagramPacket packet, DatagramSocket socket) {
            try {
                socket.setSoTimeout(0);
            } catch (SocketException e) {
                e.printStackTrace();
            }
            System.out.println("Timer wurde gestoppt");
            if (currentState.equals(State.WAIT_FOR_ACK0)) {
                return State.WAIT_1;
            } else {
                return State.WAIT_0;
            }
        }
    }

    class ResendPacket extends Transition {
        @Override
        public State execute(Msg input, DatagramPacket packet, DatagramSocket socket) {
            try {
                socket.send(packet);
                socket.setSoTimeout(10000);

            } catch (IOException e) {
                e.printStackTrace();
            }
            return currentState;
        }
    }

    class Wait extends Transition {
        @Override
        public State execute(Msg input, DatagramPacket packet, DatagramSocket socket) {
            return currentState;
        }
    }

    public byte[] buildPacket(State currentState, byte[] data, int bytesAmount) throws IOException {
        ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(byteOut);
        CRC32 checksum = new CRC32();

        checksum.reset();
        checksum.update(data, 0, bytesAmount);
        long checksumValue = checksum.getValue();
        System.out.println("checksum of send data:" + checksum.getValue());
        if (currentState == State.WAIT_0) {
            out.write(0);
        } else if (currentState == State.WAIT_1) {
            out.write(1);
        }

        for (int i = 0; i < 10; i++) {
            System.out.println(data[i]);
        }
        out.writeLong(checksumValue);
        out.writeInt(bytesAmount);
        out.write(data);
        byte packetData[] = byteOut.toByteArray();


        return packetData;
    }

    private boolean extractAndCheck(byte[] data, DatagramPacket packet, int number) throws IOException {
        byte[] numberByte = new byte[1];
        System.out.println("Start");
        int ack;
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(packet.getData()));
        // Read ACK 0/1
        ack = in.read();
        System.out.println("ack " + ack);
        if (ack != number) {
            System.out.println("das ack ist leider falsch");
            return false;
        } else {

            // Combine checksum bytes
            byte[] check = new byte[8];
            for (int i = 0; i < check.length; i++) {
                check[i] = in.readByte();
                System.out.println("checksum (for loop) " + check[i]);

            }
            ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
            buffer.put(check);
            buffer.flip();
            long checksum = buffer.getLong();

            numberByte[0] = (byte) number;
            CRC32 ackCheck = new CRC32();

            ackCheck.reset();
            ackCheck.update(numberByte, 0, 1);

            System.out.println("checksum " + checksum);
            System.out.println("checker " + ackCheck.getValue());

            if (checksum != ackCheck.getValue()) {
                return false;
            }
            return true;
        }
    }
}


