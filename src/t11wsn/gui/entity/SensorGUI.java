package t11wsn.gui.entity;

import t11wsn.util.Utils;
import t11wsn.world.entity.Sensor;
import uchicago.src.sim.gui.Drawable;
import uchicago.src.sim.gui.SimGraphics;

import java.awt.*;

public class SensorGUI implements Drawable{

    private Sensor entity;

    public SensorGUI(Sensor entity) {
        this.entity = entity;
    }

    @Override
    public void draw(SimGraphics simGraphics) {
        float r, g, b;
        double e = this.entity.getEnergy();
        if (this.entity.getState() == Sensor.State.DEEP_SLEEP) {
            r = g = b = 0.5f;
        }
        else if (e > Sensor.MAX_ENERGY / 2) {
            g = 1.0f;
            r = 1.0f - (float) Utils.i_lerp(Sensor.MAX_ENERGY / 2, Sensor.MAX_ENERGY, e);
            b = 0;
        } else if (e > 0){
            r = 1.0f;
            g = (float) Utils.i_lerp(0, Sensor.MAX_ENERGY / 2, e);
            b = 0;
        }
        else {
            r = g = b = 0.0f;
        }
        simGraphics.drawFastCircle(new Color(r, g, b));
    }

    @Override
    public int getX() {
        return this.entity.getPosition().getX();
    }

    @Override
    public int getY() {
        return this.entity.getPosition().getY();
    }
}
