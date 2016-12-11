package t11wsn.agent;

import jade.core.AID;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import sajas.core.Agent;
import sajas.core.behaviours.CyclicBehaviour;
import sajas.domain.DFService;
import t11wsn.world.entity.Sink;

import java.util.HashMap;

public class SinkAgent extends Agent {

    private Sink entity;

    public SinkAgent(Sink entity) {
        this.entity = entity;
    }

    @Override
    protected void setup() {
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd =  new ServiceDescription();
        sd.setName(getLocalName()+"-sink");
        sd.setType("sink");
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        } catch (FIPAException e) {
            System.err.println(e.getMessage());
        }

        addBehaviour(new MessageProcessing());
    }

    @Override
    protected void takeDown() {
        try {
            DFService.deregister(this);
        } catch (FIPAException e) {
            e.printStackTrace();
        }
    }

    private class MessageProcessing extends CyclicBehaviour {
        MessageTemplate mt = MessageTemplate.or(MessageTemplate.MatchPerformative(MessageType.INFORM_MEASUREMENT),
                                                MessageTemplate.MatchPerformative(MessageType.INFORM_OFFLINE));
        @Override
        public void action() {
            ACLMessage m = receive();
            if (m != null) {
                switch (m.getPerformative()) {
                    case MessageType.INFORM_MEASUREMENT: {
                        String[] content = m.getContent().split(" ");
                        final double sample = Double.parseDouble(content[0]);

                        entity.sensorsReadings.put(m.getSender(), sample);

                        break;
                    }
                    case MessageType.INFORM_OFFLINE: {
                        entity.sensorsReadings.remove(m.getSender());

                        break;
                    }
                }
            } else {
                block();
            }
        }
    }
}
