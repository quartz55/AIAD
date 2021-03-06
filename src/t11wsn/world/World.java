package t11wsn.world;

import t11wsn.util.Utils;
import t11wsn.world.entity.Sensor;
import t11wsn.world.entity.Sink;
import t11wsn.world.entity.Water;
import t11wsn.world.util.Position;
import uchicago.src.sim.space.Object2DGrid;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.stream.IntStream;

public class World {

    public enum Scenario {
        EVENLY_SPACED(50), ALL_AT_END(30), RANDOM(50), TEST(3);
        private int numSensors;

        Scenario(int numSensors) {
            this.numSensors = numSensors;
        }
    }

    private int width;
    private int height;

    private Object2DGrid water;

    private ArrayList<Water> waterCells;
    private ArrayList<Sensor> sensorCells;

    private Sink sinkNode;

    public static final double SEDIMENTATION_FACTOR = 0.9999;

    public World() {
        this(250, 10);
    }
    public World(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public void update(double tick) {
        spewPollutant(tick);
        for (int y = 0; y < this.height; ++y) {
            for (int x = 0; x < this.width; ++x) {
                Water w = getWaterCellAt(x, y);
                w.setPollution(riverFlow(w.getPosition()));
            }
        }

        this.sensorCells.forEach(s -> s.update(tick));
    }

    public void createSimulation() {

        createSimulation(Scenario.EVENLY_SPACED); }
    public void createSimulation(Scenario s) {
        this.water = new Object2DGrid(this.width, this.height);
        this.waterCells = new ArrayList<>();
        this.sensorCells = new ArrayList<>();
        // River
        for (int y = 0; y < this.height; ++y) {
            for (int x = 0; x < this.width; ++x) {
                Water w = new Water(new Position(x, y));
                this.water.putObjectAt(x, y, w);
                this.waterCells.add(w);
            }
        }

        // Sensors
        final int spacing = (int) ((this.width) / s.numSensors);
        switch(s) {
            case EVENLY_SPACED:
                for (int i = 0; i < s.numSensors; ++i) {
                   int x = i * spacing;
                   int y = this.height / 2;
                   this.addSensor(x, y);
                }
                break;
            case ALL_AT_END:
                for (int i = 0; i < s.numSensors / 3; ++i) {
                    for (int j = 0; j < 3; j++) {
                        int x = i * this.height / 4 + this.height / 4;
                        int y = (j + 1) * this.height / 4;
                        this.addSensor(x, y);
                    }
                }
                break;
            case RANDOM:
                for (int i = 0; i < s.numSensors; ++i) {
                    int x = Utils.randInt(0, this.width - 1);
                    int y = Utils.randInt(0, this.height - 1);
                    this.addSensor(x, y);
                }
                break;
            case TEST:
                for (int i = 0; i < s.numSensors; ++i) {
                    int x = this.width/2 - (s.numSensors * 5) / 2 + i * 5;
                    int y = this.height/2;
                    this.addSensor(x, y);
                }
        }

        // Sink
        sinkNode = new Sink(new Position(this.width-1, this.height/2));
    }

    private void spewPollutant(double tick) {
        final int stainSize = (int) (1 * this.height);
        final int X = this.width - 1;
        final int Y = this.height/2 - stainSize/2;
        final double osc = Math.sin(tick / 10);
        final double pollution = Math.max(0, osc * Water.MAX_POLLUTION);
        final IntConsumer spew = y -> getWaterCellAt(X, y).setPollution(pollution);

        IntStream.range(Y, Y + stainSize).forEach(spew);
    }

    private double riverFlow(Position p) {
        double a = 0.15;
        double b = 0.7;
        double y = 0.15;

        return (1 - SEDIMENTATION_FACTOR) * getPollutionAt(p) +
                SEDIMENTATION_FACTOR * (
                        a * getPollutionAt(p.from(1, -1)) +
                        b * getPollutionAt(p.from(1, 0)) +
                        y * getPollutionAt(p.from(1, 1))
                );
    }

    public Object2DGrid getWorld() { return water; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }

    public World addSensor(int x, int y) { return addSensor(new Position(x, y)); }
    public World addSensor(Position p) { this.sensorCells.add(new Sensor(p, this)); return this; }

    public List<Sensor> getSensors() { return this.sensorCells; }
    public ArrayList<Water> getWaterCells() { return waterCells; }
    public Sink getSinkNode() { return sinkNode; }

    public double getPollutionAt(int x, int y) {
        if (x >= 0 && x < this.width && y >= 0 && y < this.height)
            return getWaterCellAt(x, y).getPollution();
        else return 0;
    }
    public double getPollutionAt(Position p) { return getPollutionAt(p.getX(), p.getY()); }

    public Water getWaterCellAt(int x, int y) { return (Water) this.water.getObjectAt(x, y); }
    public Water getWaterCellAt(Position p) {
        return getWaterCellAt(p.getX(), p.getY());
    }
}
