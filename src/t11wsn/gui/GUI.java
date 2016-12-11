package t11wsn.gui;

import t11wsn.Main;
import t11wsn.gui.entity.SensorGUI;
import t11wsn.gui.entity.SinkGUI;
import t11wsn.gui.entity.WaterGUI;
import t11wsn.util.Utils;
import t11wsn.world.World;
import t11wsn.world.entity.Sensor;
import uchicago.src.sim.analysis.OpenSequenceGraph;
import uchicago.src.sim.gui.DisplaySurface;
import uchicago.src.sim.gui.Object2DDisplay;
import uchicago.src.sim.space.Object2DGrid;
import java.util.ArrayList;
import java.util.stream.Collectors;

public class GUI {

    private DisplaySurface dsurf;
    private ArrayList<SensorGUI> sensors;
    private ArrayList<WaterGUI> water;

    private OpenSequenceGraph energyGraph;
    private OpenSequenceGraph qualityGraph;

    public GUI(Main model, World world) {
        String name = "River Top Down View";
        dsurf = new DisplaySurface(model, name);
        model.registerDisplaySurface(name, dsurf);

        Object2DGrid world2D = new Object2DGrid(world.getWidth(), world.getHeight());

        Object2DDisplay waterDisplay = new Object2DDisplay(world2D);
        water = world.getWaterCells().stream()
                .map(w -> {
                    WaterGUI g = new WaterGUI(w);
                    world2D.putObjectAt(g.getX(), g.getY(), g);
                    return g;
                })
                .collect(Collectors.toCollection(ArrayList::new));
        waterDisplay.setObjectList(water);
        dsurf.addDisplayableProbeable(waterDisplay, "Show water");

        Object2DDisplay sensorDisplay = new Object2DDisplay(world2D);
        sensors = world.getSensors().stream()
                .map(s -> {
                    SensorGUI g = new SensorGUI(s);
                    world2D.putObjectAt(g.getX(), g.getY(), g);
                    return g;
                })
                .collect(Collectors.toCollection(ArrayList::new));
        sensorDisplay.setObjectList(sensors);

        SinkGUI g = new SinkGUI(world.getSinkNode());
        world2D.putObjectAt(g.getX(), g.getY(), g);
        dsurf.addDisplayableProbeable(sensorDisplay, "Show sensors");

        model.addSimEventListener(dsurf);
        dsurf.display();

        // Graph
        energyGraph = new OpenSequenceGraph("Energy Information", model);
        energyGraph.setAxisTitles("Time", "Network remaining energy");

        energyGraph.addSequence("Median", () -> {
            return Utils.median(
                    world.getSensors().stream()
                    .mapToDouble(Sensor::getEnergy)
                    .sorted().toArray());

        });
        energyGraph.addSequence("Mean", () -> {
            return world.getSensors().stream().mapToDouble(Sensor::getEnergy).average().orElse(0);
        });

        qualityGraph = new OpenSequenceGraph("Error Information", model);
        qualityGraph.setAxisTitles("Time", "Network mean error");

        qualityGraph.addSequence("Network mean error", () -> {
            return model.agents.stream()
                    .filter(s -> s.getEntity().getEnergy() > 0)
                    .filter(s -> world.getSinkNode().sensorsReadings.containsKey(s.getAID()))
                    .mapToDouble(s -> {
                        double realValue = s.getEntity().realSample();
                        double sinkReading = world.getSinkNode().sensorsReadings.getOrDefault(s.getAID(), 0.0);
//                        System.out.println(realValue + " | " + sinkReading);
                        return Math.abs(sinkReading - realValue);
                    }).average().orElse(0);
        });

//        qualityGraph.addSequence("Entropy", () -> {
//
//        });

        energyGraph.display();
        qualityGraph.display();
    }

    public void cleanup() {
        this.dsurf.dispose();
        this.energyGraph.dispose();
        }

    public void render() {
        this.dsurf.updateDisplay();
    }

    public void renderGraphs() {
        this.energyGraph.step();
        this.qualityGraph.step();
    }
}