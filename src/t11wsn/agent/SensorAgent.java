package t11wsn.agent;

import jade.core.AID;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import sajas.core.Agent;
import sajas.core.behaviours.CyclicBehaviour;
import sajas.core.behaviours.TickerBehaviour;
import sajas.domain.DFService;
import t11wsn.util.Utils;
import t11wsn.world.entity.Sensor;
import t11wsn.world.util.Position;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SensorAgent extends Agent {

    class NeighbourInfo {
        AID id;
        double sd;
        private long sampleSize = 0;
        private double mean = 0;
        double adherence; // How adherent to me

        NeighbourInfo setId(AID id) { this.id = id; return this; }
        NeighbourInfo setSD(double sd) { this.sd = sd; return this; }
        NeighbourInfo setAdh(double adh) { this.adherence = adh; return this; }
        NeighbourInfo addSample(double sample) { mean += sample/++sampleSize; return this; }
        NeighbourInfo setMean(double mean, long size) { this.mean = mean; this.sampleSize = size; return this; }
        double mean() { return mean; }
    }

    private Sensor entity;
    private double stdDev;
    private ArrayList<SensorAgent> others;
    private HashSet<AID> neighbours = new HashSet<>();

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

        addBehaviour(new NodeBehaviour(this));
        addBehaviour(new MessageProcessing());
        addBehaviour(new FindNeighbours(this, 10));
//        addBehaviour(new CyclicBehaviour() {
//            MessageTemplate mt = MessageTemplate.MatchPerformative(MessageType.INFORM_POSITION);
//            @Override
//            public void action() {
//                ACLMessage m = receive(mt);
//                if (m != null) {
//                    try {
//                        Position p = (Position) m.getContentObject();
//                        if (isNeighbour(p) && !neighbours.containsKey(m.getSender())) {
//                            neighbours.put(m.getSender(), p);
//                            String nString = neighbours.keySet().parallelStream().map(AID::getLocalName).collect(Collectors.joining(" | "));
//                            System.out.println(myAgent.getLocalName() + " got a new neighbour " + m.getSender().getLocalName() + ": [" + nString + "]");
//                        }
//                    } catch (UnreadableException e) { e.printStackTrace(); }
//                } else block();
//            }
//            private boolean isNeighbour (Position p) {
//                return getPosition().distanceToSquared(p) <= Math.pow(NEIGHBOUR_MAX_DISTANCE, 2);
//            }
//        });

//        System.out.println(getLocalName()+" INITIALIZED");
    }

    @Override
    protected void takeDown() {
//        System.out.println(getLocalName()+" OUT OF ENERGY!");
        try {
            DFService.deregister(this);
        } catch (FIPAException e) {
            e.printStackTrace();
        }
    }


    // Custom behaviours
    class NodeBehaviour extends TickerBehaviour {
        DFAgentDescription t = new DFAgentDescription();
        ServiceDescription sd = new ServiceDescription();

        NodeBehaviour(Agent a) {
            super(a, Utils.minToTicks(10));
            sd.setType("sink");
            t.addServices(sd);
        }

        @Override
        protected void onTick() {
            switch (entity.getState()) {
                case ON: {
                    double reading = SensorAgent.this.entity.readSample();
                    ACLMessage msg = new ACLMessage(MessageType.INFORM_MEASUREMENT);
                    msg.setContent(String.valueOf(reading) + " " + String.valueOf(stdDev));
                    neighbours.forEach(msg::addReceiver);

                    // Also send to sink (if there is one)
                    try {
                        DFAgentDescription[] res = DFService.search(myAgent, t);
                        Arrays.stream(res).forEach(d -> msg.addReceiver(d.getName()));
                    } catch (FIPAException e) {
                        e.printStackTrace();
                    }

                    send(msg);

                    break;
                }
                case CRITICAL: {
                    // Inform sink sensor is going offline
                    ACLMessage msg = new ACLMessage(MessageType.INFORM_OFFLINE);
                    try {
                        DFAgentDescription[] res = DFService.search(myAgent, t);
                        Arrays.stream(res).forEach(d -> msg.addReceiver(d.getName()));
                    } catch (FIPAException e) {
                        e.printStackTrace();
                    }
                    send(msg);
                    entity.switchOff();

                    break;
                }
                case OFF:
                    myAgent.doDelete();
                    break;
            }
        }
    }

    class MessageProcessing extends CyclicBehaviour {
        private HashMap<AID, NeighbourInfo> neighInfo = new HashMap<>();
        private HashSet<AID> dependants = new HashSet<>();
        private AID leader = null;
        private boolean isLeader = false;

        private AID maxAdh = null;

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

                case MessageType.INFORM_MEASUREMENT: {
                    String[] content = msg.getContent().split(" ");
                    final double sample = Double.parseDouble(content[0]);
                    final double stdDev = Double.parseDouble(content[1]);

                    // Update neigh info
                    if (neighInfo.containsKey(sender))
                        neighInfo.compute(sender, (id, info) -> info.addSample(sample).setSD(stdDev));
                    else neighInfo.put(sender, new NeighbourInfo().setId(sender).addSample(sample).setSD(stdDev));

                    updateMaxAdh();

                    break;
                }

                case MessageType.INFORM_ADHERENCE: {
                    final double adherence = Double.parseDouble(msg.getContent());

//                    System.out.println(getLocalName() + " <==== " + sender.getLocalName() +" | ADHERENCE | " + adherence);

                    // Update neigh info
                    if (neighInfo.containsKey(sender))
                        neighInfo.compute(sender, (id, info) -> info.setAdh(adherence));
                    else neighInfo.put(sender, new NeighbourInfo().setId(sender).setAdh(adherence));

                    final double leadership = leadershipTo(sender);
                    ACLMessage reply = msg.createReply();
                    reply.setPerformative(MessageType.INFORM_LEADERSHIP);
                    reply.setContent(String.valueOf(leadership));
                    send(reply);

                    updateMaxAdh();

                    break;
                }

                case MessageType.INFORM_LEADERSHIP: {
                    final double leadership = Double.parseDouble(msg.getContent());
//                    System.out.println(sender.getLocalName() + " ====> " + getLocalName() +" | LEADERSHIP | " + leadership);

                    if (this.leader == null && leadershipTo(sender) < leadership) {
                        ACLMessage reply = msg.createReply();
                        reply.setPerformative(MessageType.FIRM_ADHERENCE);
                        send(reply);
                    }

                    break;
                }



                case MessageType.FIRM_ADHERENCE: {
//                    System.out.println(getLocalName() + " <==== " + sender.getLocalName() +" | FIRM | ");
                    if (this.leader == null) {
                        ACLMessage reply = msg.createReply();
                        reply.setPerformative(MessageType.ACK_ADHERENCE);
                        send(reply);

                        this.isLeader = true;
                        this.dependants.add(sender);
                    }

                    break;
                }

                case MessageType.ACK_ADHERENCE: {
//                    System.out.println(sender.getLocalName() + " ====> " + getLocalName() +" | ACK | ");
                    if (!this.isLeader && this.leader != sender) {
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
//                    System.out.println(getLocalName() + " GOING TO SLEEP");
                    entity.sleep();
                    addBehaviour(new TickerBehaviour(myAgent, Utils.minToTicks(60 * 24)) {
                        @Override
                        protected void onTick() {
//                            System.out.println(getLocalName() + " WAKING UP");
                            entity.wakeUp();
                            leader = null;
                            maxAdh = null;
                            isLeader = false;
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

        private boolean updateMaxAdh() {
            Map.Entry<AID, NeighbourInfo> maxAdhInfo = neighInfo.entrySet().stream().max(Map.Entry.comparingByValue(Comparator.comparingDouble(this::adherenceTo))).orElse(null);

            if ((this.maxAdh == null || this.maxAdh != maxAdhInfo.getKey()) && maxAdhInfo != null) {
                maxAdh = maxAdhInfo.getKey();

                ACLMessage reply = new ACLMessage(MessageType.INFORM_ADHERENCE);
                reply.addReceiver(maxAdh);
                reply.setContent(String.valueOf(adherenceTo(maxAdhInfo.getValue())));
                send(reply);
                return true;
            }
            return false;
        }

        private double adherenceTo(final NeighbourInfo neighbour) { return adherenceTo(neighbour.mean(), neighbour.sd); }
        private double adherenceTo(final double mean, final double sd) {
            final double eHj   = Math.exp(Utils.entropy(sd));
            final double eHmin = Math.exp(Utils.entropy(MIN_STD_DEV));
            final double eHmax = Math.exp(Utils.entropy(MAX_STD_DEV));

            final double similarity = Utils.pdf(entity.getLastReading(), mean, sd) / Utils.pdf(mean, mean, sd);
            final double certainty = 1 - ((eHj - eHmin) / (eHmax - eHmin));

            return similarity * certainty;
        }

        // TODO: Check correctness
        private double leadershipTo(AID neighbour) {
            if (!neighInfo.containsKey(neighbour)) return 0;

            final double selfSample = entity.getLastReading();
            final double selfAdh = adherenceTo(selfSample, stdDev);
            final NeighbourInfo selfInfo = new NeighbourInfo().setId(getAID()).setAdh(selfAdh).setMean(entity.getMean(), entity.getNumReadings()).setSD(stdDev);
            final NeighbourInfo negoInfo = neighInfo.get(neighbour);

            Supplier<Stream<NeighbourInfo>> group = () -> Stream.concat(dependants.stream().map(neighInfo::get),
                                                                        Stream.of(selfInfo, negoInfo));



            final double prestige = group.get().mapToDouble(i -> i.adherence).average().orElse(0);

            final double capacity = (entity.getEnergy() - Sensor.SECURITY_ENERGY) / Sensor.MAX_ENERGY;

            final double groupMean = group.get().mapToDouble(NeighbourInfo::mean).average().orElse(0);
            final double groupCV = pearsonCoefficient(group);
            final double representativeness = 1 / Math.exp(Math.abs(selfSample - groupMean) * groupCV);

            return prestige * capacity * representativeness;
        }

        private double pearsonCoefficient(Supplier<Stream<NeighbourInfo>> group) {
            final double sumMean = group.get().mapToDouble(NeighbourInfo::mean).sum();
            final double sumSD = group.get().mapToDouble(i -> i.sd).sum();
            if (sumMean == 0 || sumSD == 0) return 0;
            final double sumMeanSD = group.get().mapToDouble(i-> i.mean() * i.sd).sum();
            final double sumMeanSquared = group.get().mapToDouble(i-> Math.pow(i.mean(), 2)).sum();
            final double sumSDSquared = group.get().mapToDouble(i-> Math.pow(i.sd, 2)).sum();
            final long n = group.get().count();

            final double upper = (n * sumMeanSD) - (sumMean * sumSD);
            final double lower = Math.sqrt((n * sumMeanSquared - Math.pow(sumMean, 2)) * (n * sumSDSquared - Math.pow(sumSD, 2)));

            return upper / lower;
        }

    }

    class FindNeighbours extends TickerBehaviour {
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
                               .map(Agent::getAID)
                               .collect(Collectors.toCollection(HashSet::new));

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

    private Position getPosition() { return this.entity.getPosition(); }
    public Sensor getEntity() { return entity; }
}
