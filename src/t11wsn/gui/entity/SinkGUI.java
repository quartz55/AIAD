package t11wsn.gui.entity;

import t11wsn.world.entity.Sink;
import uchicago.src.sim.gui.Drawable;
import uchicago.src.sim.gui.SimGraphics;

import java.awt.*;

public class SinkGUI implements Drawable {

    private Sink entity;

    public SinkGUI(Sink entity) {
        this.entity = entity;
    }

    @Override
    public void draw(SimGraphics simGraphics) {
        simGraphics.drawFastRect(Color.MAGENTA);
    }

    @Override
    public int getX() {
        return entity.getPosition().getX();
    }

    @Override
    public int getY() {
        return entity.getPosition().getY();
    }
}
