package t11wsn.agent;

import jade.core.AID;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import sajas.core.Agent;
import sajas.core.behaviours.CyclicBehaviour;
import sajas.domain.DFService;
import t11wsn.util.Utils;
import t11wsn.world.entity.Sensor;

import java.util.ArrayList;
import java.util.HashMap;

public class SensorAgent extends Agent {

    private Sensor entity;
    private double stdDev;

    public static final double MIN_STD_DEV = 0.0005;
    public static final double MAX_STD_DEV = 6.0;

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
//        addBehaviour(new MessageProcessing());

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
    }

    private double adherenceTo(double sample) {
        double m = Utils.mean(this.entity.getReadings());

        double eHj   = Math.exp(Utils.entropy(stdDev));
        double eHmin = Math.exp(Utils.entropy(MIN_STD_DEV));
        double eHmax = Math.exp(Utils.entropy(MAX_STD_DEV));

        double similarity = Utils.pdf(sample, m, stdDev) / Utils.pdf(m, m, stdDev);
        double certainty = 1 - ((eHj - eHmin) / (eHmax - eHmin));

        return similarity * certainty;
    }

    // Custom behaviours
    private class NodeBehaviour extends CyclicBehaviour {
        @Override
        public void action() {
            if (entity.getEnergy() > 0) {
                sampleEnvironment();
            } else {
                myAgent.doDelete();
            }
        }
    }

    private class MessageProcessing extends CyclicBehaviour {
        private HashMap<AID, Double> neighbourhoodSamples = new HashMap<>();
        private HashMap<AID, Double> neighbourhoodAdh = new HashMap<>();

        private double maxAdh = 0;

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

            switch (msg.getPerformative()) {

                case MessageType.INFORM_SAMPLE: {
                    double sample = Double.parseDouble(msg.getContent());
                    neighbourhoodSamples.put(sender, sample);
                    double adh = adherenceTo(sample);
                    updateMaxAdh(msg, adh);

                    break;
                }

                case MessageType.INFORM_ADH: {
                    ACLMessage reply = msg.createReply();
                    reply.setPerformative(MessageType.INFORM_LEADER);
                    reply.setContent(String.valueOf(2.0));
                    send(reply);

                    double adh = adherenceTo(neighbourhoodSamples.get(sender));
                    updateMaxAdh(msg, adh);

                    break;
                }

                case MessageType.INFORM_LEADER: {


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
    }
}
