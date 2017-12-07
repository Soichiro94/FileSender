
class FsmFileSender {
    // all states for this FSM
    enum State {
        WAIT_0, WAIT_FOR_ACK0, WAIT_1, WAIT_FOR_ACK1
    }
    // all messages/conditions which can occur
    enum Msg {
        SEND_PKT
    }
    // current state of the FSM
    private State currentState;
    // 2D array defining all transitions that can occur
    private Transition[][] transition;

    FsmFileSender(){
        currentState = State.WAIT_0;
        // define all valid state transitions for our state machine
        // (undefined transitions will be ignored)
        transition = new Transition[State.values().length] [Msg.values().length];
        transition[State.WAIT_0.ordinal()] [Msg.SEND_PKT.ordinal()] = new SendPkt();

        System.out.println("INFO FSM constructed, current state: "+currentState);
    }
    /**
     * Process a message (a condition has occurred).
     * @param input Message or condition that has occurred.
     */
    void processMsg(Msg input){
        System.out.println("INFO Received "+input+" in state "+currentState);
        Transition trans = transition[currentState.ordinal()][input.ordinal()];
        if(trans != null){
            currentState = trans.execute(input);
        }
        System.out.println("INFO State: "+currentState);
    }

    /**
     * Abstract base class for all transitions.
     * Derived classes need to override execute thereby defining the action
     * to be performed whenever this transition occurs.
     */
    abstract class Transition {
        abstract public State execute(Msg input);
    }

    class SendPkt extends Transition {
        @Override
        public State execute(Msg input) {
            // rdt_send(data)
            System.out.println("Packet is sended");
            return State.WAIT_FOR_ACK0;
        }
    }



}
