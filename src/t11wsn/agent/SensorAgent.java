package t11wsn.agent;

import jade.core.AID;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.UnreadableException;
import sajas.core.Agent;
import sajas.core.behaviours.CyclicBehaviour;
import sajas.core.behaviours.TickerBehaviour;
import sajas.domain.DFService;
import t11wsn.util.Utils;
import t11wsn.world.entity.Sensor;
import t11wsn.world.util.Position;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.DoubleStream;

public class SensorAgent extends Agent {

    private Sensor entity;
    private double stdDev;
    private HashMap<AID, Position> neighbours = new HashMap<>();

    public static final double MIN_STD_DEV = 0.0005;
    public static final double MAX_STD_DEV = 6.0;
    public static final int NEIGHBOUR_MAX_DISTANCE = 10;

    public SensorAgent(Sensor entity) {
        this.entity = entity;
        this.stdDev = Utils.randDouble(MIN_STD_DEV, MAX_STD_DEV);
    }

    @Override
    protected void setup() {
        // Register sensor to network
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd =  new ServiceDescription();
        sd.setName(getLocalName()+"-sensor");
        sd.setType("sensor");
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        } catch (FIPAException e) {
            System.err.println(e.getMessage());
        }

        addBehaviour(new NodeBehaviour());
        addBehaviour(new MessageProcessing());
        addBehaviour(new FindNeighbours(this, 200));

        System.out.println(getLocalName()+" INITIALIZED");
    }

    @Override
    protected void takeDown() {
        System.out.println(getLocalName()+" OUT OF ENERGY!");
        try {
            DFService.deregister(this);
        } catch (FIPAException e) {
            e.printStackTrace();
        }
    }

    private void sampleEnvironment() {
        double reading = this.entity.readSample();
        ACLMessage msg = new ACLMessage(MessageType.INFORM_SAMPLE);
        msg.setContent(String.valueOf(reading));
        neighbours.forEach((a, p) -> msg.addReceiver(a));
        send(msg);
    }

    // Custom behaviours
    private class NodeBehaviour extends CyclicBehaviour {
        @Override
        public void action() {
            switch (entity.getState()) {
                case ON:
                    sampleEnvironment();
                    break;
                case OFF:
                    myAgent.doDelete();
                    break;
            }
        }
    }

    private class MessageProcessing extends CyclicBehaviour {
        private HashMap<AID, Double> neighbourhoodSamples = new HashMap<>();
        private HashMap<AID, Double> neighbourhoodAdh = new HashMap<>();
        private HashSet<AID> dependants = new HashSet<>();
        private AID leader = null;
        private boolean isLeader = false;

        private double maxAdh = 0;
        private double maxLeader = 0;

        @Override
        public void action() {
            ACLMessage msg = myAgent.receive();

            if (entity.getState() == Sensor.State.ON && msg != null) {
                handleMessage(msg);
            } else {
                block();
            }
        }

        private void handleMessage(ACLMessage msg) {
            AID sender = msg.getSender();

            System.out.println(getLocalName() + " RECEIVED " + msg.getPerformative() + "with content: " + msg.getContent());

            switch (msg.getPerformative()) {

                case MessageType.INFORM_SAMPLE: {
                    final double sample = Double.parseDouble(msg.getContent());
                    System.out.println(sample);
                    this.neighbourhoodSamples.put(sender, sample);
                    double adh = adherenceTo(sample);
                    updateMaxAdh(msg, adh);

                    break;
                }

                case MessageType.INFORM_ADH: {
                    final double maxAdh = Double.parseDouble(msg.getContent());
                    ACLMessage reply = msg.createReply();
                    reply.setPerformative(MessageType.INFORM_LEADER);
                    reply.setContent(String.valueOf(leadershipTo(maxAdh)));
                    send(reply);

                    double adh = adherenceTo(this.neighbourhoodSamples.get(sender));
                    updateMaxAdh(msg, adh);

                    break;
                }

                case MessageType.INFORM_LEADER: {
                    final double leadership = Double.parseDouble(msg.getContent());

                    if (this.leader == null && this.maxLeader < leadership) {
                        this.maxLeader = leadership;

                        ACLMessage reply = msg.createReply();
                        reply.setPerformative(MessageType.FIRM_ADH);
                        send(reply);
                    }

                    break;
                }

                case MessageType.FIRM_ADH: {
                    if (this.leader == null) {
                        ACLMessage reply = msg.createReply();
                        reply.setPerformative(MessageType.ACK_ADH);
                        send(reply);

                        this.isLeader = true;
                        this.dependants.add(sender);
                    }

                    break;
                }

                case MessageType.ACK_ADH: {
                    if (!this.isLeader && this.leader != null && this.leader != sender) {
                        ACLMessage withdrawMsg = new ACLMessage(MessageType.WITHDRAW);
                        withdrawMsg.addReceiver(this.leader);
                        send(withdrawMsg);
                    }
                    else if (this.isLeader && !this.dependants.isEmpty()) {
                        ACLMessage breakMsg = new ACLMessage(MessageType.BREAK);
                        dependants.forEach(breakMsg::addReceiver);
                        send(breakMsg);
                        dependants.clear();
                    }

                    this.isLeader = false;
                    this.leader = sender;
                    entity.hibernate();
                    addBehaviour(new TickerBehaviour(myAgent, 500) {
                        @Override
                        protected void onTick() {
                            entity.wakeUp();
                            this.stop();
                        }
                    });

                    break;
                }

                case MessageType.BREAK: {
                    this.leader = null;

                    break;
                }

                case MessageType.WITHDRAW: {
                    this.dependants.remove(sender);
                    if (this.dependants.isEmpty())
                        this.isLeader = false;

                    break;
                }

                case MessageType.INFORM_POSITION: {
                    try {
                        Position p = (Position) msg.getContentObject();
                        if (isNeighbour(p)) {
                            neighbours.put(msg.getSender(), p);
                        }
                    } catch (UnreadableException e) { e.printStackTrace(); }

                    break;
                }
            }
        }

        private boolean isNeighbour (Position p) {
            return getPosition().distanceToSquared(p) <= Math.pow(NEIGHBOUR_MAX_DISTANCE, 2);
        }

        private boolean updateMaxAdh(ACLMessage msg, double adh) {
            if (adh > maxAdh) {
                maxAdh = adh;
                neighbourhoodAdh.put(msg.getSender(), adh);
                ACLMessage reply = msg.createReply();
                reply.setPerformative(MessageType.INFORM_ADH);
                reply.setContent(String.valueOf(adh));
                send(reply);
                return true;
            }
            return false;
        }

        private double adherenceTo(double sample) {
            double m = Utils.mean(entity.getReadings());

            double eHj   = Math.exp(Utils.entropy(stdDev));
            double eHmin = Math.exp(Utils.entropy(MIN_STD_DEV));
            double eHmax = Math.exp(Utils.entropy(MAX_STD_DEV));

            double similarity = Utils.pdf(sample, m, stdDev) / Utils.pdf(m, m, stdDev);
            double certainty = 1 - ((eHj - eHmin) / (eHmax - eHmin));

            return similarity * certainty;
        }

        // TODO: Check correctness
        private double leadershipTo(double maxAdh) {
            final double last = entity.getLastReading();

            double prestige = DoubleStream.concat(
                                dependants.stream().mapToDouble(neighbourhoodAdh::get),
                                DoubleStream.of(adherenceTo(last), maxAdh)
                              ).average().orElse(0);


            double capacity = (entity.getEnergy() - Sensor.SECURITY_ENERGY) / Sensor.MAX_ENERGY;


            DoubleStream g1 = DoubleStream.concat(
                                neighbourhoodSamples.values().stream().mapToDouble(Double::byteValue),
                                DoubleStream.of(last));
            double groupMean = g1.average().orElse(0);

            Supplier<DoubleStream> g2 = () -> neighbourhoodAdh.values().stream().mapToDouble(Double::byteValue);
            double g2Mean = g2.get().average().orElse(0);
            double g2Variance = g2.get().reduce(0.0, (acc, v) -> acc + Math.pow((g2Mean - v), 2)) / g2.get().count();
            double g2CV = Math.sqrt(g2Variance) / g2Mean;

            double representativeness = Math.exp(Math.abs(last - groupMean) * g2CV);

            return prestige * capacity * representativeness;
        }

    }

    private class FindNeighbours extends TickerBehaviour {
        FindNeighbours(Agent a, long period) { super(a, period); }

        @Override
        protected void onTick() {
            DFAgentDescription t = new DFAgentDescription();
            ServiceDescription sd = new ServiceDescription();
            sd.setType("sensor");
            t.addServices(sd);

            try {
                ACLMessage m = new ACLMessage(MessageType.INFORM_POSITION);
                m.setContentObject(getPosition());
                DFAgentDescription[] result = DFService.search(myAgent, t);
                Arrays.stream(result)
                        .map(DFAgentDescription::getName)
                        .forEach(m::addReceiver);
                send(m);
                // System.out.println("Num sensors: " + result.length + " | Num neighbours: " + neighbours.size());
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
    }

    public Position getPosition() { return this.entity.getPosition(); }
}
