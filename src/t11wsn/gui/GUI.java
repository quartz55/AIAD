package t11wsn.gui;

import sajas.sim.repast3.Repast3Launcher;
import t11wsn.gui.entity.SensorGUI;
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

    public GUI(Repast3Launcher model, World world) {
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
        dsurf.addDisplayableProbeable(sensorDisplay, "Show sensors");

        model.addSimEventListener(dsurf);
        dsurf.display();

        // Graph
        energyGraph = new OpenSequenceGraph("Energy Information", model);
        energyGraph.setAxisTitles("Time", "Energy");

        energyGraph.addSequence("Energy Median", () -> {
            return Utils.median(
                    world.getSensors().stream()
                    .mapToDouble(Sensor::getEnergy)
                    .sorted().toArray());

        });
//        energyGraph.addSequence("Energy Mean", () -> {
//            return world.getSensors().stream().mapToDouble(Sensor::getEnergy).average().orElse(0);
//        });

        energyGraph.display();
    }

    public void render() {
        this.dsurf.updateDisplay();
        this.energyGraph.step();
    }
}
