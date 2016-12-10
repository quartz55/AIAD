package t11wsn.world.entity;

import t11wsn.util.Utils;
import t11wsn.world.World;
import t11wsn.world.util.Position;

import java.util.ArrayList;

public class Sensor extends Entity{

    public enum State { ON, OFF, SLEEP, DEEP_SLEEP, HIBERNATE }

    private World world;

    private ArrayList<Double> readings;
    private double energy;

    private State state;

    // CONSTANTS
    public static final double MAX_ENERGY = 100;
    public static final double SECURITY_ENERGY = 1.5;

    public Sensor(Position position, World world) {
        super(position);
        this.world = world;
        this.energy = MAX_ENERGY;
        this.state = State.ON;
        this.readings = new ArrayList<>();
    }

    public double readSample() {
        double sample = this.world.getWaterCellAt(this.getPosition()).getPollution();
        readings.add(sample);
        return sample;
    }

    public void update(double tick) {
        if (this.energy <= 0) {
            this.energy = 0;
            this.state = State.OFF;
            return;
        }

        switch (this.state) {
            case ON:
                this.energy -= 0.1;
                break;
            case HIBERNATE:
                this.energy -= 0.001;
                break;
            case OFF:
                break;
            default:
                this.energy -= 0.01;
                break;
        }
    }

    public void hibernate() {
        if (this.state != State.OFF)
            this.state = State.HIBERNATE;
        System.out.println("SLEEPING");
    }

    public void wakeUp() {
        if (this.state != State.OFF)
            this.state = State.ON;
        System.out.println("WAKING UP");
    }

    public ArrayList<Double> getReadings() { return readings; }
    public double getLastReading() { return readings.get(readings.size()-1); }
    public State getState() { return state; }
    public double getEnergy() { return energy; }
    public World getWorld() { return world; }
}
