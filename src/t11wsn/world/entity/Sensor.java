package t11wsn.world.entity;

import t11wsn.util.Utils;
import t11wsn.world.World;
import t11wsn.world.util.Position;

import java.util.ArrayList;

public class Sensor extends Entity{



    public enum State { ON, OFF, SLEEP }

    private enum Action {
        READ(0.1);
        private double energyNeeded;
        Action(double v) { this.energyNeeded = v; }
    }

    private World world;

    private ArrayList<Double> readings;
    private double energy;

    private State state;

    // CONSTANTS
    public static final double MAX_ENERGY = 100;

    public Sensor(Position position, World world) {
        super(position);
        this.world = world;
        this.energy = MAX_ENERGY;
        this.state = State.ON;
        this.readings = new ArrayList<>();
    }

    public double readSample() {
        doAction(Action.READ);
        double sample = this.world.getWaterCellAt(this.getPosition()).getPollution();
        readings.add(sample);
        return sample;
    }

    private boolean doAction(Action a) {
        if (this.energy - a.energyNeeded < 0) {
            this.energy = 0;
            return false;
        } else {
            this.energy -= a.energyNeeded;
            return true;
        }
    }

    public ArrayList<Double> getReadings() { return readings; }
    public State getState() { return state; }
    public double getEnergy() { return energy; }
}
