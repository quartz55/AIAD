package t11wsn;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.wrapper.StaleProxyException;
import sajas.core.Runtime;
import sajas.sim.repast3.Repast3Launcher;
import sajas.wrapper.ContainerController;
import t11wsn.agent.SensorAgent;
import t11wsn.gui.GUI;
import t11wsn.world.World;
import t11wsn.world.entity.Sensor;
import uchicago.src.sim.engine.Schedule;
import uchicago.src.sim.engine.SimInit;

import java.util.ArrayList;
import java.util.List;

public class Main extends Repast3Launcher{

    private ContainerController mainContainer;
    private ArrayList<SensorAgent> agents = new ArrayList<>();
    private World world;
    private GUI gui;

    @Override
    protected void launchJADE() {
        Runtime rt = Runtime.instance();
        Profile p1 = new ProfileImpl();
        mainContainer = rt.createMainContainer(p1);

        initializeSimulation();
    }

    private void initializeSimulation() {
        world = new World();
        world.createSimulation();

        initializeAgents();
        initializeDisplay();
        initializeSchedule();
    }

    private void initializeAgents() {
        List<Sensor> sensorsList = this.world.getSensors();
        for (int i = 0; i < sensorsList.size(); ++i) {
            SensorAgent a = new SensorAgent(sensorsList.get(i), agents);
            agents.add(a);
            try {
                mainContainer.acceptNewAgent("["+i+"]", a).start();
            } catch (StaleProxyException e) { e.printStackTrace(); }
        }
    }

    private void initializeDisplay() {
        if(this.gui != null)
            this.gui.cleanup();

        this.gui = new GUI(this, this.world);
    }

    private void initializeSchedule() {
        getSchedule().scheduleActionAtInterval(1, this, "update");
        getSchedule().scheduleActionAtInterval(1, this, "render", Schedule.LAST);
        getSchedule().scheduleActionAtInterval(50, this, "renderGraph", Schedule.LAST);
    }

    public void update() {
        this.world.update(getTickCount());
    }

    public void render() {
        this.gui.render();
    }
    public void renderGraph() { this.gui.renderGraphs(); }

    @Override
    public String[] getInitParam() {
        return new String[] {};
    }
    @Override
    public String getName() {
        return "A Multi-agent Approach to Energy-Aware Wireless Sensor Networks Organization";
    }

    public static void main(String[] args) {
        SimInit init = new SimInit();
        init.setNumRuns(1);
        init.loadModel(new Main(), null, false);
    }
}
