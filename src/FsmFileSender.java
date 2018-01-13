import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.zip.CRC32;



class FsmFileSender implements Runnable {

    private final static int PORT = 10_000;
    private long sendTime = 0;
    private int rtt = 0;
    private byte[] data = new byte[1200];
    private byte[] nameByte = new byte[1200];
    private byte[] pkt = new byte[9];

    // all states for this FSM
    enum State {
        WAIT_0, WAIT_FOR_ACK0, WAIT_1, WAIT_FOR_ACK1
    }

    // all messages/conditions which can occur
    enum Msg {
        SEND_PKT, WAIT_FOR_DATA, CORRECT_PACKET, BROKEN_PACKET, TIMEOUT
    }

    // current state of the FSM
    private State currentState;
    // 2D array defining all transitions that can occur
    private Transition[][] transition;

    private String receiverName;
    private String dataName;

    FsmFileSender(String dataName, String receiverName)  {
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
        long startTime = System.currentTimeMillis();
        File file = new File(dataName);
        System.out.println(file.length());
        ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(byteOut);
        DatagramPacket sendPacket = null;
        boolean nameNotSend = true;

        try {
            InetAddress ia;
            if(receiverName.equals("localhost")){
                ia = InetAddress.getLocalHost();
            }
            else{
                ia = InetAddress.getByName("10.179.13.224");
            }

            DatagramSocket receiverSocket = new DatagramSocket(9000);
            FileInputStream fis = new FileInputStream(file);
            BufferedInputStream bis = new BufferedInputStream(fis);

            while (true) {
                DatagramSocket socket = new DatagramSocket();

                if (currentState.equals(State.WAIT_0) || currentState.equals(State.WAIT_1)) {

                    if (nameNotSend) {
                        CRC32 checksum = new CRC32();
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
                        int bytesAmount;
                        if ((bytesAmount = bis.read(data, 0, 1200)) > 0) {
                            byte packetData[] = buildPacket(currentState, data, bytesAmount);
                            sendPacket = new DatagramPacket(packetData, packetData.length, ia, PORT);

                        }else{

                            double endTime = System.currentTimeMillis();
                            double throughputTime = (endTime - startTime)/1000;
                            double sizeMBit = (file.length() * 8) / 1_000_000;

                            System.out.println("###########################################################");
                            System.out.println("####  We send "+ file.length() +" byte of data");
                            System.out.println("####  In "+ throughputTime +" seconds");
                            System.out.println("####  So the throughput is "+ sizeMBit/throughputTime+" MBit/s");
                            System.out.println("###########################################################");
                            System.out.println();
                            System.out.println("This is the end of the packet");
                            break;
                        }
                    }
                    processMsg(Msg.SEND_PKT, sendPacket, socket);
                    if(rtt == 0) {
                        socket.setSoTimeout(1_000);
                    }
                    else{
                        socket.setSoTimeout(rtt);
                    }

                } else if (currentState.equals(State.WAIT_FOR_ACK0) || currentState.equals(State.WAIT_FOR_ACK1)) {
                    try {

                        int number = 0;
                        boolean correct;

                        if(rtt == 0) {
                            receiverSocket.setSoTimeout(1_000);
                        }
                        else{
                            receiverSocket.setSoTimeout(rtt);
                        }


                        DatagramPacket ackpkt = new DatagramPacket(pkt, pkt.length);
                        System.out.println("Waiting for ACK...");

                        receiverSocket.receive(ackpkt);

                        long receiveTime = System.currentTimeMillis();
                        rtt = (int)(2 * (receiveTime - sendTime) + (rtt/2));

                        if (currentState.equals(State.WAIT_FOR_ACK0)) {
                            number = 0;
                        } else if (currentState.equals(State.WAIT_FOR_ACK1)) {
                            number = 1;
                        }
                        correct = extractAndCheck(pkt, ackpkt, number);
                        if (correct) {
                            System.out.println("The Packet is CORRECT");
                            processMsg(Msg.CORRECT_PACKET, ackpkt, receiverSocket);
                        } else {
                            System.out.println("The Packet is CORRUPT");
                            processMsg(Msg.BROKEN_PACKET, ackpkt, receiverSocket);
                        }
                    } catch (SocketTimeoutException ex) {
                        System.out.println("We caught a timeout");
                        processMsg(Msg.TIMEOUT, sendPacket, socket);
                    }
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }


    }


    /**
     * Process a message (a condition has occurred).
     *
     * @param input Message or condition that has occurred.
     */
    private void processMsg(Msg input, DatagramPacket packet, DatagramSocket socket) throws SocketException {
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
            try {
                socket.send(packet);
                sendTime = System.currentTimeMillis();

            } catch (IOException e) {
                e.printStackTrace();
            }
            if (currentState == State.WAIT_0) {
                System.out.println("Packet send from WAIT_0");
                return State.WAIT_FOR_ACK0;
            } else if (currentState == State.WAIT_FOR_ACK0) {
                System.out.println("Wait in ACK_0");
                return currentState;
            } else if (currentState == State.WAIT_1) {
                System.out.println("Packet send from WAIT_1");
                return State.WAIT_FOR_ACK1;
            } else {
                System.out.println("Wait in ACK_1");
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
                sendTime = System.currentTimeMillis();
                if(rtt == 0) {
                    socket.setSoTimeout(1_000);
                }
                else{
                    socket.setSoTimeout(rtt);
                }

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

    private byte[] buildPacket(State currentState, byte[] data, int bytesAmount) throws IOException {
        ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(byteOut);
        CRC32 checksum = new CRC32();
        checksum.reset();
        checksum.update(data, 0, bytesAmount);
        long checksumValue = checksum.getValue();

        if (currentState == State.WAIT_0) {
            out.write(0);
        } else if (currentState == State.WAIT_1) {
            out.write(1);
        }

        out.writeLong(checksumValue);
        out.writeInt(bytesAmount);
        out.write(data);


        return byteOut.toByteArray();
    }

    private boolean extractAndCheck(byte[] data, DatagramPacket packet, int number) throws IOException {
        byte[] numberByte = new byte[1];
        int ack;
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(packet.getData()));
        // Read ACK 0/1
        ack = in.read();

        if (ack != number) {
            return false;
        } else {

            // Combine checksum bytes
            byte[] check = new byte[8];
            for (int i = 0; i < check.length; i++) {
                check[i] = in.readByte();
            }
            ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
            buffer.put(check);
            buffer.flip();
            long checksum = buffer.getLong();

            numberByte[0] = (byte) number;
            CRC32 ackCheck = new CRC32();

            ackCheck.reset();
            ackCheck.update(numberByte, 0, 1);

            return checksum == ackCheck.getValue();
        }
    }
}


