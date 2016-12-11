package t11wsn.world.entity;

import t11wsn.util.Utils;
import t11wsn.world.World;
import t11wsn.world.util.Position;

import java.util.ArrayList;

public class Sensor extends Entity{

    public enum State { ON, OFF, DEEP_SLEEP, HIBERNATE }

    private World world;

    private long numReadings = 0;
    private double lastReading = 0;
    private double median = 0;

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
    }

    public double readSample() {
        double sample = this.world.getWaterCellAt(this.getPosition()).getPollution();
        lastReading = sample;
        median += sample / ++numReadings;
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
                this.energy -= 0.01;
                break;
            case DEEP_SLEEP:
                this.energy -= 0.00003;
                break;
            case HIBERNATE:
                this.energy -= 0.0000002;
            case OFF:
                break;
            default:
                this.energy -= 0.01;
                break;
        }
    }

    public void sleep() {
        if (this.state != State.OFF)
            this.state = State.DEEP_SLEEP;
    }

    public void hibernate() {
        if (this.state != State.OFF) {
            this.state = State.HIBERNATE;
        }
    }

    public void wakeUp() {
        if (this.state != State.OFF) {
            this.state = State.ON;
        }
    }

    public double getMedian() { return median; }
    public double getLastReading() { return lastReading; }
    public long getNumReadings() { return numReadings; }

    public State getState() { return state; }
    public double getEnergy() { return energy; }
    public World getWorld() { return world; }
}
