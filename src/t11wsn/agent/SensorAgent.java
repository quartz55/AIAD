package t11wsn.agent;

import jade.core.AID;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.lang.acl.UnreadableException;
import javafx.geometry.Pos;
import sajas.core.Agent;
import sajas.core.behaviours.CyclicBehaviour;
import sajas.core.behaviours.OneShotBehaviour;
import sajas.core.behaviours.TickerBehaviour;
import sajas.domain.DFService;
import sun.plugin2.message.Message;
import t11wsn.util.Utils;
import t11wsn.world.entity.Sensor;
import t11wsn.world.util.Position;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;

public class SensorAgent extends Agent {

    private Sensor entity;
    private double stdDev;
    private ArrayList<SensorAgent> others;
    private HashMap<AID, Position> neighbours = new HashMap<>();

    public static final double MIN_STD_DEV = 0.0005;
    public static final double MAX_STD_DEV = 6.0;
    public static final int NEIGHBOUR_MAX_DISTANCE = 10;

    public SensorAgent(Sensor entity) {
        this.entity = entity;
        this.stdDev = Utils.randDouble(MIN_STD_DEV, MAX_STD_DEV);
    }

    public SensorAgent(Sensor entity, ArrayList<SensorAgent> others) {
        this(entity);
        this.others = others;
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
        addBehaviour(new CyclicBehaviour() {
            MessageTemplate mt = MessageTemplate.MatchPerformative(MessageType.INFORM_POSITION);
            @Override
            public void action() {
                ACLMessage m = receive(mt);
                if (m != null) {
                    try {
                        Position p = (Position) m.getContentObject();
                        if (isNeighbour(p) && !neighbours.containsKey(m.getSender())) {
                            neighbours.put(m.getSender(), p);
                            String nString = neighbours.keySet().parallelStream().map(AID::getLocalName).collect(Collectors.joining(" | "));
                            System.out.println(myAgent.getLocalName() + " got a new neighbour " + m.getSender().getLocalName() + ": [" + nString + "]");
                        }
                    } catch (UnreadableException e) { e.printStackTrace(); }
                } else block();
            }
            private boolean isNeighbour (Position p) {
                return getPosition().distanceToSquared(p) <= Math.pow(NEIGHBOUR_MAX_DISTANCE, 2);
            }
        });

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
        neighbours.keySet().parallelStream().forEach(msg::addReceiver);
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

            if (entity.getState() == Sensor.State.ON && msg != null && msg.getSender() != myAgent.getAID()) {
                handleMessage(msg);
            } else {
                block();
            }
        }

        private void handleMessage(ACLMessage msg) {
            AID sender = msg.getSender();

            switch (msg.getPerformative()) {

                case MessageType.INFORM_SAMPLE: {
                    final double sample = Double.parseDouble(msg.getContent());
                    this.neighbourhoodSamples.put(sender, sample);
                    double adh = adherenceTo(sample);
                    updateMaxAdh(msg, adh);

                    break;
                }

                case MessageType.INFORM_ADH: {
                    final double adherence = Double.parseDouble(msg.getContent());
                    System.out.println(getLocalName() + " <==== " + sender.getLocalName() +" | ADHERENCE | " + adherence);


                    final double leadership = leadershipTo(adherence, neighbourhoodSamples.containsKey(sender) ? neighbourhoodSamples.get(sender) : 0);
                    System.out.println(getLocalName() + "leader("+adherence+") = " + leadership);
                    ACLMessage reply = msg.createReply();
                    reply.setPerformative(MessageType.INFORM_LEADER);
                    reply.setContent(String.valueOf(leadership));
                    send(reply);

                    if (this.neighbourhoodSamples.containsKey(sender)) {
                        updateMaxAdh(msg, adherenceTo(this.neighbourhoodSamples.get(sender)));
                    }

                    break;
                }

                case MessageType.INFORM_LEADER: {
                    final double leadership = Double.parseDouble(msg.getContent());
                    System.out.println(sender.getLocalName() + " ====> " + getLocalName() +" | LEADERSHIP | " + leadership);

                    if (this.leader == null && this.maxLeader < leadership) {
                        this.maxLeader = leadership;

                        ACLMessage reply = msg.createReply();
                        reply.setPerformative(MessageType.FIRM_ADH);
                        send(reply);
                    }

                    break;
                }



                case MessageType.FIRM_ADH: {
                    System.out.println(getLocalName() + " <==== " + sender.getLocalName() +" | FIRM | ");
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
                    System.out.println(sender.getLocalName() + " ====> " + getLocalName() +" | ACK | ");
                    if (!this.isLeader && this.leader != null && this.leader != sender) {
                        ACLMessage withdrawMsg = new ACLMessage(MessageType.WITHDRAW);
                        withdrawMsg.addReceiver(this.leader);
                        send(withdrawMsg);
                    }
                    if (this.isLeader && !this.dependants.isEmpty()) {
                        ACLMessage breakMsg = new ACLMessage(MessageType.BREAK);
                        dependants.forEach(breakMsg::addReceiver);
                        send(breakMsg);
                        dependants.clear();
                    }

                    this.isLeader = false;
                    this.leader = sender;
                    System.out.println(getLocalName() + " GOING TO SLEEP");
                    entity.hibernate();
                    addBehaviour(new TickerBehaviour(myAgent, 2000) {
                        @Override
                        protected void onTick() {
                            System.out.println(getLocalName() + " WAKING UP");
                            entity.wakeUp();
                            this.stop();
                        }
                    });

                    break;
                }

                case MessageType.BREAK: {
                    System.out.println(sender.getLocalName() + " ====> " + getLocalName() +" | BREAK | ");

                    this.leader = null;

                    break;
                }

                case MessageType.WITHDRAW: {
                    System.out.println(sender.getLocalName() + " ====> " + getLocalName() +" | WITHDRAW | ");

                    this.dependants.remove(sender);
                    if (this.dependants.isEmpty())
                        this.isLeader = false;

                    break;
                }
            }
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
            double mean = Utils.mean(entity.getReadings());

            double eHj   = Math.exp(Utils.entropy(stdDev));
            double eHmin = Math.exp(Utils.entropy(MIN_STD_DEV));
            double eHmax = Math.exp(Utils.entropy(MAX_STD_DEV));

            double similarity = Utils.pdf(sample, mean, stdDev) / Utils.pdf(mean, mean, stdDev);
            double certainty = 1 - ((eHj - eHmin) / (eHmax - eHmin));

//            System.out.println("adh("+sample+", "+getLocalName()+") = "+ similarity * certainty);

            return similarity * certainty;
        }

        // TODO: Check correctness
        private double leadershipTo(double otherAdh, double otherSample) {
            final double selfSample = entity.getLastReading();
            final double selfAdh = adherenceTo(selfSample);

            Supplier<DoubleStream> gAdh = () -> DoubleStream.concat(dependants.stream().map(neighbourhoodAdh::get).mapToDouble(Double::doubleValue), DoubleStream.of(selfAdh, otherAdh));
            Supplier<DoubleStream> gSamples = () -> DoubleStream.concat(dependants.stream().map(neighbourhoodSamples::get).mapToDouble(Double::doubleValue), DoubleStream.of(selfSample, otherSample));

            double prestige = gAdh.get().average().orElse(0);

            double capacity = (entity.getEnergy() - Sensor.SECURITY_ENERGY) / Sensor.MAX_ENERGY;

            double groupMean = gSamples.get().average().orElse(0);

            // CV
            double CV = 0;
            if (gAdh.get().count() > 0) {
                double g2Mean = gAdh.get().average().orElse(0);
                double g2Variance = gAdh.get().reduce(0.0, (acc, v) -> acc + Math.pow((g2Mean - v), 2)) / gAdh.get().count();
                CV = Math.sqrt(g2Variance) / g2Mean;
            }

            double representativeness = 1 / Math.exp(Math.abs(selfSample - groupMean) * CV);

            System.out.println(getLocalName()+" : " + prestige + " | " + capacity + " | " + representativeness);


            return prestige * capacity * representativeness;
        }

    }

    private class FindNeighbours extends TickerBehaviour {
        DFAgentDescription t = new DFAgentDescription();
        ServiceDescription sd = new ServiceDescription();

        FindNeighbours(Agent a, long period) {
            super(a, period);
            onTick();
            sd.setType("sensor");
            t.addServices(sd);
        }

        @Override
        protected void onTick() {
            neighbours = others.stream()
                                .filter(sa -> !sa.getAID().equals(getAID()))
                                .filter(sa -> getPosition().distanceToSquared(sa.getPosition()) <= Math.pow(NEIGHBOUR_MAX_DISTANCE, 2))
                                .collect(HashMap<AID, Position>::new, (m, sa) -> m.put(sa.getAID(), sa.getPosition()), (m, sa) -> {});

//            try {
//                ACLMessage m = new ACLMessage(MessageType.INFORM_POSITION);
//                m.setContentObject(getPosition());
//                DFAgentDescription[] result = DFService.search(myAgent, t);
//                Arrays.stream(result).parallel()
//                        .filter(a -> a.getName() != myAgent.getAID())
//                        .map(DFAgentDescription::getName)
//                        .sequential()
//                        .forEach(m::addReceiver);
//                send(m);
//                // System.out.println("Num sensors: " + result.length + " | Num neighbours: " + neighbours.size());
//            } catch (Exception e) {
//                e.printStackTrace();
//            }

        }
    }

    public Position getPosition() { return this.entity.getPosition(); }
}
